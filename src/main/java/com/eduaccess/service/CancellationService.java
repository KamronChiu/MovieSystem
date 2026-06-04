package com.eduaccess.service;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.domain.CancellationRecord;
import com.eduaccess.domain.FoodOrder;
import com.eduaccess.domain.FoodOrderStatus;
import com.eduaccess.domain.RefundSummary;
import com.eduaccess.exception.CancellationNotAllowedException;
import com.eduaccess.repository.BookingRepository;
import com.eduaccess.repository.CancellationRepository;
import com.eduaccess.service.policy.CancellationPolicy;
import com.eduaccess.service.policy.CancellationPolicyFactory;
import com.eduaccess.service.policy.PolicyRefundResult;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundContext;
import com.eduaccess.service.policy.RefundScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CancellationService {

    /**
     * Virtual VIP membership add-on price used when the administrator ticks
     * the &quot;VIP Package&quot; line item in the Refund Decision Panel.
     * <p>
     * The current data model does not persist a separate VIP package fee
     * on the booking, so the value is fixed at £10 — sufficient to demo
     * the refund-item toggle without altering the existing schema.
     */
    public static final BigDecimal VIP_PACKAGE_FEE = new BigDecimal("10.00");

    private final BookingRepository bookingRepository;
    private final RefundCalculator refundCalculator;
    private final CancellationRepository cancellationRepository;
    private final AuditService auditService;
    private final CancellationPolicyFactory policyFactory;
    private final FoodOrderService foodOrderService;

    public CancellationService(BookingRepository bookingRepository,
                               RefundCalculator refundCalculator,
                               CancellationRepository cancellationRepository,
                               AuditService auditService,
                               CancellationPolicyFactory policyFactory,
                               FoodOrderService foodOrderService) {
        this.bookingRepository = bookingRepository;
        this.refundCalculator = refundCalculator;
        this.cancellationRepository = cancellationRepository;
        this.auditService = auditService;
        this.policyFactory = policyFactory;
        this.foodOrderService = foodOrderService;
    }

    @Transactional(readOnly = true)
    public List<Booking> findAllBookings() {
        return bookingRepository.findAllByOrderByBookingDateDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Booking> findBookingByReference(String bookingReference) {
        if (bookingReference == null || bookingReference.isBlank()) {
            return Optional.empty();
        }

        return bookingRepository.findByBookingReference(normalizeReference(bookingReference));
    }

    /**
     * Updates the VIP flag for the given booking and persists it.
     *
     * @param bookingReference the booking reference
     * @param vip              whether the customer is VIP
     * @return the updated booking, or null if not found
     */
    @Transactional
    public Booking updateVipFlag(String bookingReference, boolean vip) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElse(null);
        if (booking == null) {
            return null;
        }
        boolean previous = booking.isVip();
        booking.setVip(vip);
        Booking saved = bookingRepository.save(booking);

        // TASK 7 — audit the VIP toggle (only when it actually changes).
        if (previous != vip) {
            auditService.record(
                    AuditService.ACTION_UPDATE_VIP,
                    saved.getBookingReference(),
                    saved.getStatus(),
                    saved.getStatus(),
                    "VIP flag set to " + vip
            );
        }
        return saved;
    }

    /**
     * Calculates the refund summary for a booking without changing its status.
     * <p>
     * This method is intended for preview purposes — the UI can call it to
     * display the refund breakdown before the user confirms the cancellation.
     *
     * @param bookingReference the booking reference
     * @return the refund summary, or null if the booking is not found
     */
    @Transactional(readOnly = true)
    public RefundSummary calculateRefund(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElse(null);
        if (booking == null) {
            return null;
        }
        return refundCalculator.calculate(booking);
    }

    /**
     * Cancels the booking identified by the given reference.
     * <p>
     * This is a convenience method that transitions CONFIRMED → CANCELLED
     * in a single call. For the full step-by-step refund flow use
     * {@link #advanceStatus(String, BookingStatus)}.
     *
     * @param bookingReference the booking reference to cancel
     * @return cancellation result with charge and refund details
     * @throws CancellationNotAllowedException if the booking status does not
     *         allow cancellation
     */
    @Transactional
    public CancellationResult cancelBooking(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElseThrow(() -> new CancellationNotAllowedException(
                        "Booking reference was not found."));

        if (!booking.getStatus().isCancellable()) {
            throw new CancellationNotAllowedException(
                    "Booking cannot be cancelled. Current status: "
                            + booking.getStatus().getDisplayName());
        }

        // Same-day cancellation is now allowed; refund will be £0
        // (RefundCalculator handles this)

        RefundSummary refundSummary = refundCalculator.calculate(booking);

        BookingStatus oldStatus = booking.getStatus();
        booking.transitionTo(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);

        // TASK 6 — persist a cancellation record for this booking.
        syncCancellationRecord(savedBooking, refundSummary, null);

        // TASK 7 — record the cancellation in the audit log.
        auditService.record(
                AuditService.ACTION_CANCEL_BOOKING,
                savedBooking.getBookingReference(),
                oldStatus,
                savedBooking.getStatus(),
                "Booking cancelled, refund " + refundSummary.getRefundAmount()
        );

        return new CancellationResult(savedBooking, refundSummary);
    }

    /**
     * Advances the booking to the next status in the refund flow.
     * <p>
     * The target status must be a valid transition from the booking's current
     * status as defined by {@link BookingStatus#canTransitionTo(BookingStatus)}.
     * <p>
     * Same-day cancellations are now permitted — the refund amount will be
     * £0 but the flow can still proceed through all steps to REFUNDED.
     *
     * @param bookingReference the booking reference
     * @param targetStatus     the target status to transition to
     * @return the updated booking
     * @throws CancellationNotAllowedException if the booking is not found or
     *         the transition is not allowed
     */
    @Transactional
    public Booking advanceStatus(String bookingReference, BookingStatus targetStatus) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElseThrow(() -> new CancellationNotAllowedException(
                        "Booking reference was not found."));

        if (!booking.getStatus().canTransitionTo(targetStatus)) {
            throw new CancellationNotAllowedException(
                    "Cannot transition from " + booking.getStatus().getDisplayName()
                            + " to " + targetStatus.getDisplayName());
        }

        // No date restriction — same-day cancellations are allowed
        // (refund will be £0 per RefundCalculator)

        BookingStatus oldStatus = booking.getStatus();
        booking.transitionTo(targetStatus);
        Booking saved = bookingRepository.save(booking);

        // TASK 6 — keep the cancellation record in sync with the new status.
        syncCancellationRecord(saved, refundCalculator.calculate(saved), null);

        // TASK 7 — record the transition in the audit log.
        auditService.record(
                AuditService.ACTION_ADVANCE_STATUS,
                saved.getBookingReference(),
                oldStatus,
                saved.getStatus(),
                "Status advanced from " + oldStatus.getDisplayName()
                        + " to " + saved.getStatus().getDisplayName()
        );

        return saved;
    }

    // ── TASK 9 — Refund Policy Decision (Strategy Pattern) ────────────────

    /**
     * Returns the sum of every refundable food-order total for the booking.
     * <p>
     * Only orders still in {@link FoodOrderStatus#PENDING} or
     * {@link FoodOrderStatus#PREPARING} count — {@code DELIVERED} food has
     * been consumed and {@code CANCELLED} food has already been refunded
     * out-of-band.
     *
     * @param bookingId the booking id
     * @return total food cost still eligible for refund (never null)
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateRefundableFoodAmount(Long bookingId) {
        if (bookingId == null) {
            return BigDecimal.ZERO;
        }
        List<FoodOrder> orders = foodOrderService.findOrdersForBooking(bookingId);
        BigDecimal total = BigDecimal.ZERO;
        for (FoodOrder order : orders) {
            if (order.getStatus() == FoodOrderStatus.PENDING
                    || order.getStatus() == FoodOrderStatus.PREPARING) {
                BigDecimal cost = order.getTotalCost();
                if (cost != null) {
                    total = total.add(cost);
                }
            }
        }
        return total;
    }

    /**
     * Builds the immutable {@link RefundContext} input that drives all
     * three concrete {@link CancellationPolicy} implementations.
     * <p>
     * 构建不可变的 {@link RefundContext} 输入，驱动所有三个具体的
     * {@link CancellationPolicy} 实现。
     *
     * @param bookingReference  booking identifier (预订标识符)
     * @param policyType        which policy the administrator picked (管理员选择的策略)
     * @param scope             full vs partial refund (全额或部分退款)
     * @param includeMovie      whether to refund the movie ticket (是否退电影票)
     * @param includeFood       whether to refund food orders (是否退食品订单)
     * @param includeVipPackage whether to refund the VIP package add-on (是否退VIP套餐)
     * @return refund context, or {@code null} if the booking is not found
     *         (退款上下文，如果预订未找到则返回 {@code null})
     */
    @Transactional(readOnly = true)
    public RefundContext buildPolicyContext(String bookingReference,
                                            PolicyType policyType,
                                            RefundScope scope,
                                            boolean includeMovie,
                                            boolean includeFood,
                                            boolean includeVipPackage) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElse(null);
        if (booking == null) {
            return null;
        }
        BigDecimal foodAmount = calculateRefundableFoodAmount(booking.getId());
        BigDecimal vipPackageAmount = booking.isVip() ? VIP_PACKAGE_FEE : BigDecimal.ZERO;
        LocalDate screeningDate = booking.getScreening().getScreeningDate();
        return new RefundContext(
                booking.getTotalCost(),
                foodAmount,
                vipPackageAmount,
                includeMovie,
                includeFood,
                includeVipPackage,
                scope,
                booking.isVip(),
                screeningDate
        );
    }

    /**
     * Runs the selected {@link CancellationPolicy} against the given context
     * and returns the refund breakdown shown live in the Refund Decision
     * Panel. Pure preview — no DB writes occur.
     *
     * @param policyType which policy to apply
     * @param context    refund context built via
     *                   {@link #buildPolicyContext(String, PolicyType, RefundScope, boolean, boolean, boolean)}
     * @return immutable result for UI rendering, or {@code null} if the
     *         context is null
     */
    public PolicyRefundResult quotePolicyRefund(PolicyType policyType, RefundContext context) {
        if (context == null || policyType == null) {
            return null;
        }
        CancellationPolicy policy = policyFactory.policyFor(policyType);
        return policy.calculate(context);
    }

    /**
     * Finalises the refund step: advances the booking into
     * {@link BookingStatus#REFUND_PENDING}, persists the policy-driven
     * refund amount on the cancellation record, optionally cancels
     * attached food orders, and emits an audit entry that captures the
     * full administrator decision.
     *
     * @param bookingReference  booking identifier
     * @param policyType        chosen policy
     * @param scope             chosen refund scope
     * @param includeMovie      whether the movie ticket is being refunded
     * @param includeFood       whether attached food orders are being refunded
     * @param includeVipPackage whether the VIP package add-on is being refunded
     * @return the updated booking (now {@code REFUND_PENDING})
     * @throws CancellationNotAllowedException if the booking cannot transition
     */
    @Transactional
    public Booking submitPolicyRefund(String bookingReference,
                                      PolicyType policyType,
                                      RefundScope scope,
                                      boolean includeMovie,
                                      boolean includeFood,
                                      boolean includeVipPackage) {
        Booking booking = bookingRepository.findByBookingReference(normalizeReference(bookingReference))
                .orElseThrow(() -> new CancellationNotAllowedException(
                        "Booking reference was not found."));

        // 1) Compute the policy result (we need it for the audit trail).
        // 1) 计算策略结果（我们需要它用于审计跟踪）。
        RefundContext context = new RefundContext(
                booking.getTotalCost(),
                calculateRefundableFoodAmount(booking.getId()),
                booking.isVip() ? VIP_PACKAGE_FEE : BigDecimal.ZERO,
                includeMovie,
                includeFood,
                includeVipPackage,
                scope,
                booking.isVip(),
                booking.getScreening().getScreeningDate()
        );
        PolicyRefundResult result = policyFactory.policyFor(policyType).calculate(context);

        // 2) Advance the booking to REFUND_PENDING (idempotent if already there).
        BookingStatus oldStatus = booking.getStatus();
        if (oldStatus.canTransitionTo(BookingStatus.REFUND_PENDING)) {
            booking.transitionTo(BookingStatus.REFUND_PENDING);
            booking = bookingRepository.save(booking);
        }

        // 3) Cancel attached food orders when the admin chose to refund food.
        if (includeFood) {
            foodOrderService.cancelPendingFoodOrdersForBooking(booking.getId());
        }

        // 4) Persist the policy-driven refund amount on the cancellation record.
        CancellationRecord record = cancellationRepository.findByBookingReference(
                booking.getBookingReference()).orElse(null);
        if (record == null) {
            record = new CancellationRecord(
                    booking.getBookingReference(),
                    result.getFinalRefund(),
                    "",
                    LocalDateTime.now(),
                    false
            );
        } else {
            record.setRefundAmount(result.getFinalRefund());
        }
        cancellationRepository.save(record);

        // 5) Audit the full administrator decision.
        auditService.record(
                AuditService.ACTION_ADVANCE_STATUS,
                booking.getBookingReference(),
                oldStatus,
                booking.getStatus(),
                String.format(
                        "Refund policy=%s, scope=%s, items=[movie=%s,food=%s,vip=%s], final=£%s, voucher=£%s",
                        policyType, scope,
                        includeMovie, includeFood, includeVipPackage,
                        result.getFinalRefund(), result.getVoucher()
                )
        );

        return booking;
    }

    // ── TASK 6 — Cancellation record persistence ──────────────────────────

    /**
     * Updates (or lazily creates) the cancellation record for the given
     * booking with a user-supplied reason.
     * <p>
     * If the booking has not yet transitioned out of {@code CONFIRMED} no
     * record exists yet — in that case the call is a no-op so the reason
     * can be re-submitted later when the cancellation is finalised.
     *
     * @param bookingReference the booking reference
     * @param reason           the user-supplied reason (may be blank)
     * @return the persisted record, or {@code null} if no record exists yet
     */
    @Transactional
    public CancellationRecord updateCancellationReason(String bookingReference, String reason) {
        if (bookingReference == null || bookingReference.isBlank()) {
            return null;
        }
        String ref = normalizeReference(bookingReference);
        CancellationRecord record = cancellationRepository.findByBookingReference(ref)
                .orElse(null);
        if (record == null) {
            return null;
        }
        String previous = record.getCancellationReason();
        String next = reason == null ? "" : reason.trim();
        record.setCancellationReason(next);
        CancellationRecord saved = cancellationRepository.save(record);

        // TASK 7 — audit reason updates only when the text actually changed.
        if (!java.util.Objects.equals(previous == null ? "" : previous, next)) {
            auditService.record(
                    AuditService.ACTION_UPDATE_REASON,
                    saved.getBookingReference(),
                    null,
                    null,
                    next.isBlank() ? "Reason cleared" : "Reason: " + next
            );
        }
        return saved;
    }

    /**
     * Returns the cancellation record for the given booking, if any.
     *
     * @param bookingReference the booking reference
     * @return the record, or empty if the booking has never been cancelled
     */
    @Transactional(readOnly = true)
    public Optional<CancellationRecord> findCancellationRecord(String bookingReference) {
        if (bookingReference == null || bookingReference.isBlank()) {
            return Optional.empty();
        }
        return cancellationRepository.findByBookingReference(normalizeReference(bookingReference));
    }

    /**
     * Returns every cancellation record ordered most recent first.
     *
     * @return all cancellation records, newest first
     */
    @Transactional(readOnly = true)
    public List<CancellationRecord> findAllCancellationRecords() {
        return cancellationRepository.findAllByOrderByCancelledAtDesc();
    }

    /**
     * Internal hook invoked after every successful status transition.
     * <p>
     * <ul>
     *   <li>Booking is still {@code CONFIRMED}: nothing to do.</li>
     *   <li>Record does not yet exist: insert one with the current refund
     *       snapshot and {@code refunded = (status == REFUNDED)}.</li>
     *   <li>Record exists: refresh the refund amount with the latest
     *       calculation (the refund amount may shift if VIP was toggled),
     *       and flip {@code refunded = true} once the booking reaches
     *       {@code REFUNDED}.</li>
     * </ul>
     *
     * @param booking       the booking after its status was updated
     * @param refundSummary the refund summary for the booking (may be null)
     * @param reason        an optional reason override (may be null)
     */
    private void syncCancellationRecord(Booking booking,
                                        RefundSummary refundSummary,
                                        String reason) {
        BookingStatus status = booking.getStatus();
        if (status == BookingStatus.CONFIRMED) {
            return;
        }

        String ref = booking.getBookingReference();
        BigDecimal refundAmount = refundSummary != null
                ? refundSummary.getRefundAmount()
                : BigDecimal.ZERO;

        CancellationRecord record = cancellationRepository.findByBookingReference(ref)
                .orElse(null);

        if (record == null) {
            record = new CancellationRecord(
                    ref,
                    refundAmount,
                    reason == null ? "" : reason.trim(),
                    LocalDateTime.now(),
                    status == BookingStatus.REFUNDED
            );
        } else {
            record.setRefundAmount(refundAmount);
            if (reason != null) {
                record.setCancellationReason(reason.trim());
            }
            if (status == BookingStatus.REFUNDED) {
                record.setRefunded(true);
            }
        }
        cancellationRepository.save(record);
    }

    private String normalizeReference(String bookingReference) {
        return bookingReference.trim().toUpperCase();
    }

    /**
     * Result of a cancellation operation, carrying the updated booking
     * and the full refund summary with adjustment breakdown.
     */
    public record CancellationResult(
            Booking booking,
            RefundSummary refundSummary
    ) {
    }
}
