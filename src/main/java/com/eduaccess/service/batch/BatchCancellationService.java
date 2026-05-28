package com.eduaccess.service.batch;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.UserAccount;
import com.eduaccess.exception.CancellationNotAllowedException;
import com.eduaccess.service.CancellationService;
import com.eduaccess.service.LoginService;
import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.compensation.CompensationItemType;
import com.eduaccess.service.compensation.CompensationPackage;
import com.eduaccess.service.compensation.VIPBenefitService;
import com.eduaccess.service.policy.PolicyRefundResult;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundContext;
import com.eduaccess.service.policy.RefundScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// NOTE: Batch cancellation only ever runs the Emergency Policy (TASK 11 / 12).
// Compensation is therefore applied to <strong>every</strong> selected booking
// regardless of the customer's VIP flag — see {@link #buildBatchCompensation}.
// {@link VIPBenefitService} is still injected so the future-extension hooks
// can fall back to the per-customer (VIP-aware) matrix if required.

/**
 * Engine for the Batch Cancellation Dashboard (TASK 11).
 * <p>
 * Wraps the existing single-booking refund pipeline so the operator can
 * apply one chosen policy to many bookings in one click. Two operating
 * modes are exposed:
 * <ul>
 *   <li>{@link #previewBatch} — pure read, used to drive the live Summary card
 *       and the Email Preview dialog without mutating any state.</li>
 *   <li>{@link #executeBatch} — actually advances every selected booking
 *       through {@link CancellationService#submitPolicyRefund}, so the
 *       Refund History (CancellationRecord table) and Audit Log
 *       (AuditLog table) stay in lock-step with the per-booking flow.</li>
 * </ul>
 * <p>
 * Per-booking errors are captured into a {@link BatchRefundResult} of type
 * {@code failure(...)} so a single bad row never aborts the entire batch.
 * <p>
 * <b>Future-extension hooks</b> — placeholder methods at the bottom of
 * the class outline how Batch Approvals, Manager Override and Emergency
 * Cinema Shutdown will plug in without breaking existing call sites.
 */
@Service
public class BatchCancellationService {

    private final CancellationService cancellationService;
    private final VIPBenefitService vipBenefitService;
    private final LoginService loginService;

    public BatchCancellationService(CancellationService cancellationService,
                                    VIPBenefitService vipBenefitService,
                                    LoginService loginService) {
        this.cancellationService = cancellationService;
        this.vipBenefitService = vipBenefitService;
        this.loginService = loginService;
    }

    // ── Preview ──────────────────────────────────────────────────────────

    /**
     * Builds an in-memory {@link BatchOperationRecord} <strong>without</strong>
     * persisting anything — drives the Summary card and Email Preview.
     *
     * @param bookings                   bookings selected by the operator
     * @param policyType                 chosen refund policy (Standard / VIP / Emergency)
     * @param scope                      full vs partial refund
     * @param includeMovie               include the movie ticket line
     * @param includeFood                include attached food orders
     * @param includeVipPackage          include the VIP package add-on (VIP customers only)
     * @param includeHalfPriceVoucher    Emergency-only opt-in voucher
     * @param includeFreeDrinkCoupon     Emergency-only opt-in voucher
     * @return immutable preview record (never {@code null}; may carry an empty entry list)
     */
    @Transactional(readOnly = true)
    public BatchOperationRecord previewBatch(List<Booking> bookings,
                                             PolicyType policyType,
                                             RefundScope scope,
                                             boolean includeMovie,
                                             boolean includeFood,
                                             boolean includeVipPackage,
                                             boolean includeHalfPriceVoucher,
                                             boolean includeFreeDrinkCoupon) {
        List<BatchRefundResult> entries = new ArrayList<>();
        if (bookings != null) {
            for (Booking booking : bookings) {
                entries.add(previewSingle(booking, policyType, scope,
                        includeMovie, includeFood, includeVipPackage,
                        includeHalfPriceVoucher, includeFreeDrinkCoupon));
            }
        }
        return baseBuilder(policyType, scope, includeMovie, includeFood,
                includeVipPackage, includeHalfPriceVoucher, includeFreeDrinkCoupon)
                .previewOnly(true)
                .entries(entries)
                .build();
    }

    /**
     * Executes the batch: every cancellable booking is advanced to
     * {@code REFUND_PENDING} via the same single-booking pipeline, so the
     * existing CancellationRecord and AuditLog persistence happens
     * automatically — the Refund History and Audit Log views update
     * without any extra plumbing.
     */
    @Transactional
    public BatchOperationRecord executeBatch(List<Booking> bookings,
                                             PolicyType policyType,
                                             RefundScope scope,
                                             boolean includeMovie,
                                             boolean includeFood,
                                             boolean includeVipPackage,
                                             boolean includeHalfPriceVoucher,
                                             boolean includeFreeDrinkCoupon) {
        List<BatchRefundResult> entries = new ArrayList<>();
        if (bookings != null) {
            for (Booking booking : bookings) {
                entries.add(executeSingle(booking, policyType, scope,
                        includeMovie, includeFood, includeVipPackage,
                        includeHalfPriceVoucher, includeFreeDrinkCoupon));
            }
        }
        return baseBuilder(policyType, scope, includeMovie, includeFood,
                includeVipPackage, includeHalfPriceVoucher, includeFreeDrinkCoupon)
                .previewOnly(false)
                .entries(entries)
                .build();
    }

    // ── Single-booking helpers ───────────────────────────────────────────

    private BatchRefundResult previewSingle(Booking booking,
                                            PolicyType policyType,
                                            RefundScope scope,
                                            boolean includeMovie,
                                            boolean includeFood,
                                            boolean includeVipPackage,
                                            boolean includeHalfPriceVoucher,
                                            boolean includeFreeDrinkCoupon) {
        if (booking == null || booking.getStatus() == BookingStatus.REFUNDED) {
            return BatchRefundResult.failure(
                    booking == null ? "—" : booking.getBookingReference(),
                    booking == null ? "" : booking.getCustomerName(),
                    booking == null ? "" : booking.getCustomerEmail(),
                    "Booking is already fully refunded."
            );
        }
        try {
            RefundContext ctx = cancellationService.buildPolicyContext(
                    booking.getBookingReference(), policyType, scope,
                    includeMovie, includeFood, includeVipPackage);
            if (ctx == null) {
                return BatchRefundResult.failure(booking.getBookingReference(),
                        booking.getCustomerName(), booking.getCustomerEmail(),
                        "Refund context could not be built.");
            }
            PolicyRefundResult policy = cancellationService.quotePolicyRefund(policyType, ctx);
            CompensationPackage pkg = buildBatchCompensation(ctx,
                    includeHalfPriceVoucher, includeFreeDrinkCoupon);
            return BatchRefundResult.success(
                    booking.getBookingReference(),
                    booking.getCustomerName(),
                    booking.getCustomerEmail(),
                    booking.isVip(),
                    policy,
                    pkg);
        } catch (Exception ex) {
            return BatchRefundResult.failure(booking.getBookingReference(),
                    booking.getCustomerName(), booking.getCustomerEmail(),
                    safeMessage(ex));
        }
    }

    private BatchRefundResult executeSingle(Booking booking,
                                            PolicyType policyType,
                                            RefundScope scope,
                                            boolean includeMovie,
                                            boolean includeFood,
                                            boolean includeVipPackage,
                                            boolean includeHalfPriceVoucher,
                                            boolean includeFreeDrinkCoupon) {
        if (booking == null) {
            return BatchRefundResult.failure("—", "", "", "Booking missing.");
        }
        if (booking.getStatus() == BookingStatus.REFUNDED) {
            return BatchRefundResult.failure(booking.getBookingReference(),
                    booking.getCustomerName(), booking.getCustomerEmail(),
                    "Already refunded — skipped.");
        }
        try {
            // 1) build the same refund context the dashboard previewed.
            RefundContext ctx = cancellationService.buildPolicyContext(
                    booking.getBookingReference(), policyType, scope,
                    includeMovie, includeFood, includeVipPackage);
            if (ctx == null) {
                return BatchRefundResult.failure(booking.getBookingReference(),
                        booking.getCustomerName(), booking.getCustomerEmail(),
                        "Refund context could not be built.");
            }

            // 2) compute the policy + compensation again so the row carries
            //    every datum the email/summary need.
            PolicyRefundResult policy = cancellationService.quotePolicyRefund(policyType, ctx);
            CompensationPackage pkg = buildBatchCompensation(ctx,
                    includeHalfPriceVoucher, includeFreeDrinkCoupon);

            // 3) Drive the full state machine end-to-end so the booking lands
            //    in REFUNDED (a terminal status). The dashboard, Refund
            //    History view and any other read-only consumer therefore
            //    see the updated booking immediately after the batch returns.
            //
            //    The single-booking pipeline only knows how to do <i>one</i>
            //    transition per call — we replay each step here:
            //      CONFIRMED       → CANCELLED
            //      CANCELLED       → REFUND_PENDING  (via submitPolicyRefund)
            //      REFUND_PENDING  → REFUNDED
            //    submitPolicyRefund also writes/refreshes the CancellationRecord
            //    and the audit log, so Refund History stays in sync.
            if (booking.getStatus() == BookingStatus.CONFIRMED) {
                cancellationService.cancelBooking(booking.getBookingReference());
            }
            cancellationService.submitPolicyRefund(
                    booking.getBookingReference(), policyType, scope,
                    includeMovie, includeFood, includeVipPackage);
            cancellationService.advanceStatus(
                    booking.getBookingReference(), BookingStatus.REFUNDED);

            return BatchRefundResult.success(
                    booking.getBookingReference(),
                    booking.getCustomerName(),
                    booking.getCustomerEmail(),
                    booking.isVip(),
                    policy,
                    pkg);
        } catch (CancellationNotAllowedException ex) {
            return BatchRefundResult.failure(booking.getBookingReference(),
                    booking.getCustomerName(), booking.getCustomerEmail(),
                    safeMessage(ex));
        } catch (Exception ex) {
            return BatchRefundResult.failure(booking.getBookingReference(),
                    booking.getCustomerName(), booking.getCustomerEmail(),
                    safeMessage(ex));
        }
    }

    // ── Future-extension hooks (TASK 11 §8) ──────────────────────────────

    /**
     * Stamp a manager approval onto an existing record (Batch Approvals).
     * Currently a pass-through; production wiring will check role + write
     * a dedicated approval audit entry.
     */
    public BatchOperationRecord approveBatch(BatchOperationRecord original) {
        if (original == null) {
            return null;
        }
        return rebuildWithFlags(original, true, original.isEmergencyShutdown(),
                resolveOperatorUsername());
    }

    /** Manager override entry-point (Manager Override). */
    public BatchOperationRecord overrideAsManager(BatchOperationRecord original) {
        if (original == null) {
            return null;
        }
        return rebuildWithFlags(original, original.isManagerApproved(),
                original.isEmergencyShutdown(), resolveOperatorUsername());
    }

    /**
     * Convenience entry-point used by the Emergency Cinema Shutdown demo —
     * forces the Emergency policy on every booking belonging to a cinema.
     */
    public BatchOperationRecord emergencyCinemaShutdown(List<Booking> bookings) {
        BatchOperationRecord exec = executeBatch(bookings,
                PolicyType.EMERGENCY, RefundScope.FULL,
                /* movie */ true, /* food */ true, /* vip */ true,
                /* halfPriceVoucher */ true, /* freeDrink */ true);
        return rebuildWithFlags(exec, exec.isManagerApproved(),
                /* emergencyShutdown */ true, exec.getOperatorUsername());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private BatchOperationRecord.Builder baseBuilder(PolicyType policyType,
                                                     RefundScope scope,
                                                     boolean includeMovie,
                                                     boolean includeFood,
                                                     boolean includeVipPackage,
                                                     boolean includeHalfPriceVoucher,
                                                     boolean includeFreeDrinkCoupon) {
        return new BatchOperationRecord.Builder()
                .operationId(UUID.randomUUID().toString())
                .operatorUsername(resolveOperatorUsername())
                .policyType(policyType)
                .scope(scope)
                .includeMovie(includeMovie)
                .includeFood(includeFood)
                .includeVipPackage(includeVipPackage)
                .includeHalfPriceVoucher(includeHalfPriceVoucher)
                .includeFreeDrinkCoupon(includeFreeDrinkCoupon);
    }

    private BatchOperationRecord rebuildWithFlags(BatchOperationRecord src,
                                                  boolean approved,
                                                  boolean emergencyShutdown,
                                                  String overrideBy) {
        return new BatchOperationRecord.Builder()
                .operationId(src.getOperationId())
                .executedAt(src.getExecutedAt())
                .operatorUsername(src.getOperatorUsername())
                .policyType(src.getPolicyType())
                .scope(src.getScope())
                .previewOnly(src.isPreviewOnly())
                .includeMovie(src.isIncludeMovie())
                .includeFood(src.isIncludeFood())
                .includeVipPackage(src.isIncludeVipPackage())
                .includeHalfPriceVoucher(src.isIncludeHalfPriceVoucher())
                .includeFreeDrinkCoupon(src.isIncludeFreeDrinkCoupon())
                .entries(src.getEntries())
                .managerApproved(approved)
                .emergencyShutdown(emergencyShutdown)
                .managerOverrideBy(overrideBy)
                .build();
    }

    private String resolveOperatorUsername() {
        try {
            UserAccount user = loginService.getCurrentUser();
            return user == null ? "system" : user.getUsername();
        } catch (Exception ignored) {
            return "system";
        }
    }

    private String safeMessage(Exception ex) {
        if (ex == null) {
            return "Unknown error.";
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    /**
     * Build the compensation package handed out during a Batch (emergency)
     * cancellation. Unlike {@link VIPBenefitService#build}, this matrix is
     * <strong>not</strong> conditioned on the customer's VIP flag — every
     * impacted booking receives the same goodwill compensation when the
     * operator opts in to issue a voucher / coupon.
     *
     * @param ctx                       refund context (carries the movie amount)
     * @param includeHalfPriceVoucher   whether to issue a half-price voucher
     * @param includeFreeDrinkCoupon    whether to issue a free-drink coupon
     * @return compensation package, never {@code null}
     */
    private CompensationPackage buildBatchCompensation(RefundContext ctx,
                                                       boolean includeHalfPriceVoucher,
                                                       boolean includeFreeDrinkCoupon) {
        if (ctx == null) {
            return CompensationPackage.EMPTY;
        }
        CompensationPackage.Builder b = new CompensationPackage.Builder()
                .headline("EMERGENCY GOODWILL COMPENSATION");
        BigDecimal movie = ctx.movieAmount() == null ? BigDecimal.ZERO : ctx.movieAmount();
        if (includeHalfPriceVoucher && movie.signum() > 0) {
            BigDecimal value = movie.multiply(new BigDecimal("0.50"));
            b.add(new CompensationItem(CompensationItemType.HALF_PRICE_VOUCHER, value));
        }
        if (includeFreeDrinkCoupon) {
            b.add(new CompensationItem(CompensationItemType.FREE_DRINK_COUPON,
                    new BigDecimal("4.50")));
        }
        return b.build();
    }
}
