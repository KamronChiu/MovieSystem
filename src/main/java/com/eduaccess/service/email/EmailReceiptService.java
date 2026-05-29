package com.eduaccess.service.email;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.Cinema;
import com.eduaccess.domain.Screening;
import com.eduaccess.service.batch.BatchOperationRecord;
import com.eduaccess.service.batch.BatchRefundResult;
import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.compensation.CompensationItemType;
import com.eduaccess.service.compensation.CompensationPackage;
import com.eduaccess.service.policy.PolicyRefundResult;
import com.eduaccess.service.policy.PolicyType;
import com.eduaccess.service.policy.RefundScope;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure transformation service that turns refund-flow data into the
 * VOs rendered by the Email Preview / Receipt Preview dialogs (TASK 12).
 * <p>
 * The service is intentionally stateless and side-effect free: it never
 * reads from or writes to the database — it only adapts the data the
 * caller already has into a format the dialog understands.
 * <p>
 * <b>Why a dedicated adapter?</b>
 * <ul>
 *   <li>Decouples the policy-driven refund pipeline from the UI markup.</li>
 *   <li>Lets the Single flow and the Batch flow share one renderer
 *       (both go through {@link CancellationEmail} / {@link CancellationReceipt}).</li>
 *   <li>Makes it trivial to swap the future SMTP / PDF generators in
 *       without touching the dialogs.</li>
 * </ul>
 */
@Service
public class EmailReceiptService {

    private final EmailLogService emailLogService;

    public EmailReceiptService(EmailLogService emailLogService) {
        this.emailLogService = emailLogService;
    }

    /**
     * Builds the email preview for a single-cancellation flow.
     * <p>
     * The copy is tailored to the {@link PolicyType} so the message reads
     * professionally for the channel that triggered it:
     * <ul>
     *   <li>STANDARD — neutral, business tone, full procedural details.</li>
     *   <li>VIP      — warm, loyalty-oriented, highlights member perks.</li>
     *   <li>EMERGENCY (single flow) — apologetic, mirrors batch copy.</li>
     * </ul>
     */
    public CancellationEmail buildSingleEmail(Booking booking,
                                              PolicyType policyType,
                                              PolicyRefundResult policyResult,
                                              CompensationPackage compensation,
                                              LocalDateTime cancellationTime) {
        if (booking == null) {
            return null;
        }
        BigDecimal refund = policyResult == null ? BigDecimal.ZERO : policyResult.getFinalRefund();
        BigDecimal compValue = compensation == null ? BigDecimal.ZERO : compensation.getTotalValue();
        List<CompensationItem> items = compensation == null ? List.of() : compensation.getItems();
        LocalDateTime when = cancellationTime == null ? LocalDateTime.now() : cancellationTime;
        String policyDisplay = policyType == null ? "Standard Refund Policy"
                : policyType.getDisplayName();
        String cinemaName = extractCinemaName(booking);
        String movieTitle = extractFilmTitle(booking);
        String screeningInfo = extractScreeningInfo(booking);

        List<String> body = buildSingleEmailBody(
                booking, policyType, policyDisplay, refund, items,
                when, cinemaName, movieTitle, screeningInfo);

        String subject = buildSingleEmailSubject(policyType, booking.getBookingReference());

        CancellationEmail email = new CancellationEmail.Builder()
                .kind(CancellationEmail.Kind.SINGLE)
                .to(safeEmail(booking.getCustomerEmail()))
                .customerName(safeName(booking.getCustomerName()))
                .subject(subject)
                .bookingReference(booking.getBookingReference())
                .policyType(policyType)
                .policyName(policyDisplay)
                .refundAmount(refund)
                .compensationValue(compValue)
                .compensationItems(items)
                .cancellationTime(when)
                .cinemaName(cinemaName)
                .bodyLines(body)
                .build();

        recordSingle(email, null, policyType);
        return email;
    }

    private String buildSingleEmailSubject(PolicyType policyType, String bookingRef) {
        if (policyType == null) {
            return "Cancellation Confirmation — Booking " + bookingRef;
        }
        switch (policyType) {
            case VIP:
                return "[HCBS Premium] Your VIP Cancellation Has Been Processed — Booking " + bookingRef;
            case EMERGENCY:
                return "[Important] Emergency Cancellation Confirmation — Booking " + bookingRef;
            case STANDARD:
            default:
                return "Cancellation Confirmation & Refund Receipt — Booking " + bookingRef;
        }
    }

    private List<String> buildSingleEmailBody(Booking booking,
                                              PolicyType policyType,
                                              String policyDisplay,
                                              BigDecimal refund,
                                              List<CompensationItem> items,
                                              LocalDateTime when,
                                              String cinemaName,
                                              String movieTitle,
                                              String screeningInfo) {
        List<String> body = new ArrayList<>();
        String greeting = (policyType == PolicyType.VIP)
                ? "Dear " + safeName(booking.getCustomerName()) + " (HCBS Premium Member),"
                : "Dear " + safeName(booking.getCustomerName()) + ",";
        body.add(greeting);
        body.add("");

        // Opening paragraph — tone varies by policy
        if (policyType == PolicyType.EMERGENCY) {
            body.add("We are writing to confirm that booking " + booking.getBookingReference()
                    + " has been cancelled under our Emergency Cancellation Policy due to an");
            body.add("unforeseen incident on our side. Please accept our sincere apologies for");
            body.add("the disruption to your plans.");
        } else if (policyType == PolicyType.VIP) {
            body.add("Thank you for being an HCBS Premium member. Your cancellation request for");
            body.add("booking " + booking.getBookingReference() + " has been processed under your");
            body.add("VIP Cancellation Policy, which entitles you to expedited handling and a");
            body.add("premium goodwill package.");
        } else {
            body.add("This message confirms that your cancellation request for booking "
                    + booking.getBookingReference() + " has been received");
            body.add("and processed in accordance with our Standard Cancellation Policy. The");
            body.add("refund details below have been recorded against your account and the");
            body.add("original payment method.");
        }
        body.add("");

        // Booking details block
        body.add("Booking details:");
        body.add("  • Reference        : " + booking.getBookingReference());
        if (movieTitle != null)   body.add("  • Film             : " + movieTitle);
        if (screeningInfo != null) body.add("  • Screening        : " + screeningInfo);
        if (cinemaName != null)   body.add("  • Cinema           : " + cinemaName);
        body.add("  • Original amount  : " + formatMoney(booking.getTotalCost()));
        body.add("");

        // Refund summary
        body.add("Refund summary:");
        body.add("  • Refund amount    : " + formatMoney(refund));
        body.add("  • Policy applied   : " + policyDisplay);
        body.add("  • Processed at     : " + when);
        body.add("  • Reference number : RFD-" + booking.getBookingReference());
        body.add("");

        if (!items.isEmpty()) {
            body.add(policyType == PolicyType.VIP
                    ? "Complimentary VIP rewards added to your account:"
                    : "Goodwill compensation issued to your account:");
            for (CompensationItem c : items) {
                body.add("  • " + c.getName() + " — " + formatMoney(c.getValue())
                        + " (valid until " + c.getExpiryDate() + ")");
            }
            body.add("");
        }

        // Closing — what to expect next
        body.add("What happens next:");
        body.add("  1. Refund will be returned to the original payment method within 5 business days.");
        body.add("  2. Any vouchers above are usable immediately at every HCBS cinema and on our app.");
        body.add("  3. A PDF receipt has been generated and is also available in your booking history.");
        body.add("");

        if (policyType == PolicyType.VIP) {
            body.add("As always, your Premium concierge team is one tap away inside the HCBS app");
            body.add("if anything is unclear or if you would like assistance rebooking.");
        } else if (policyType == PolicyType.EMERGENCY) {
            body.add("We would love the opportunity to welcome you back soon — please use the");
            body.add("vouchers above on your next visit, with our compliments.");
        } else {
            body.add("If anything in this confirmation looks incorrect, please reply to this");
            body.add("email within 14 days and our customer care team will be happy to help.");
        }
        body.add("");
        body.add("Thank you for choosing HCBS Cinemas.");
        body.add("");
        body.add("Kind regards,");
        body.add("HCBS Cinemas Customer Service");
        body.add("support@hcbs-cinema.example  ·  +44 (0)20 7946 0000");
        return body;
    }

    /**
     * Builds the receipt for a single-cancellation flow.
     */
    public CancellationReceipt buildSingleReceipt(Booking booking,
                                                  PolicyType policyType,
                                                  RefundScope scope,
                                                  PolicyRefundResult policyResult,
                                                  CompensationPackage compensation,
                                                  String operatorUsername,
                                                  LocalDateTime timestamp) {
        if (booking == null) {
            return null;
        }
        BigDecimal refund = policyResult == null ? BigDecimal.ZERO : policyResult.getFinalRefund();
        BigDecimal compValue = compensation == null ? BigDecimal.ZERO : compensation.getTotalValue();
        List<CompensationItem> items = compensation == null ? List.of() : compensation.getItems();
        List<String> breakdown = policyResult == null ? List.of() : policyResult.getBreakdownLines();

        CancellationReceipt receipt = new CancellationReceipt.Builder()
                .bookingReference(booking.getBookingReference())
                .customerName(safeName(booking.getCustomerName()))
                .refundAmount(refund)
                .policyType(policyType)
                .policyApplied(policyType == null ? "—" : policyType.getDisplayName())
                .refundScope(scope)
                .breakdownLines(breakdown)
                .compensationItems(items)
                .compensationValue(compValue)
                .operatorUsername(operatorUsername)
                .timestamp(timestamp == null ? LocalDateTime.now() : timestamp)
                .cinemaName(extractCinemaName(booking))
                .build();

        attachReceiptToLatestSingle(booking.getBookingReference(), receipt);
        return receipt;
    }

    // ── Batch helpers ────────────────────────────────────────────────────

    /**
     * Builds one email per successful batch row.
     * Failed rows are skipped (no-customer-impact emails are not generated).
     */
    public List<CancellationEmail> buildBatchEmails(BatchOperationRecord record) {
        List<CancellationEmail> out = new ArrayList<>();
        if (record == null) return out;
        String policyDisplay = record.getPolicyType() == null
                ? "—" : record.getPolicyType().getDisplayName();
        for (BatchRefundResult row : record.getEntries()) {
            if (!row.isSuccess()) continue;
            CancellationEmail email = buildBatchEmail(row, record.getPolicyType(), policyDisplay,
                    record.getScope(), record.getExecutedAt());
            out.add(email);
            // Record one history entry per email so the management screen
            // can show each customer’s message individually.
            emailLogService.record(
                    EmailLogEntry.Source.BATCH,
                    record.getOperationId(),
                    email,
                    null,
                    EmailLogService.TEMPLATE_BATCH_EMERGENCY,
                    EmailLogEntry.Status.SENT);
        }
        return out;
    }

    private CancellationEmail buildBatchEmail(BatchRefundResult row,
                                              PolicyType policyType,
                                              String policyDisplay,
                                              RefundScope scope,
                                              LocalDateTime stamp) {
        List<String> body = new ArrayList<>();
        body.add("Dear " + safeName(row.getCustomerName()) + ",");
        body.add("");
        body.add("Due to an emergency situation at our cinema, your booking "
                + row.getBookingReference() + " has been cancelled by our staff.");
        body.add("We sincerely apologise for the inconvenience this has caused.");
        body.add("");
        body.add("Refund summary:");
        body.add("  • Refund amount: " + formatMoney(row.getRefundAmount())
                + (scope == RefundScope.PARTIAL ? "  (partial refund — 50%)" : "  (full refund — 100%)"));
        body.add("  • Policy applied: " + policyDisplay);
        body.add("  • Cancellation time: " + stamp);
        if (!row.getCompensationItems().isEmpty()) {
            body.add("");
            body.add("As a goodwill gesture, the following compensation has been issued:");
            for (CompensationItem c : row.getCompensationItems()) {
                body.add("  • " + c.getName() + " — " + formatMoney(c.getValue())
                        + " (valid until " + c.getExpiryDate() + ")");
            }
        }
        body.add("");
        body.add("The refund has been processed and funds will be returned to your original "
                + "payment method within 5 business days. The compensation listed above has "
                + "been deposited into your HCBS account immediately.");
        body.add("");
        body.add("Thank you for your understanding.");
        body.add("HCBS Cinemas Customer Service");

        return new CancellationEmail.Builder()
                .kind(CancellationEmail.Kind.BATCH_EMERGENCY)
                .to(safeEmail(row.getCustomerEmail()))
                .customerName(safeName(row.getCustomerName()))
                .subject("[Emergency] Cancellation Confirmation — Booking " + row.getBookingReference())
                .bookingReference(row.getBookingReference())
                .policyType(policyType)
                .policyName(policyDisplay)
                .refundAmount(row.getRefundAmount())
                .compensationValue(row.getCompensationValue())
                .compensationItems(row.getCompensationItems())
                .cancellationTime(stamp == null ? LocalDateTime.now() : stamp)
                .bodyLines(body)
                .build();
    }

    /**
     * Builds one receipt per successful batch row, suitable for the
     * Receipt Preview dialog.
     */
    public List<CancellationReceipt> buildBatchReceipts(BatchOperationRecord record) {
        List<CancellationReceipt> out = new ArrayList<>();
        if (record == null) return out;
        String policyDisplay = record.getPolicyType() == null
                ? "—" : record.getPolicyType().getDisplayName();
        for (BatchRefundResult row : record.getEntries()) {
            if (!row.isSuccess()) continue;
            List<String> breakdown = row.getBreakdownLines();
            if (breakdown == null || breakdown.isEmpty()) {
                breakdown = synthesiseBreakdown(row, record.getScope());
            }
            CancellationReceipt receipt = new CancellationReceipt.Builder()
                    .bookingReference(row.getBookingReference())
                    .customerName(safeName(row.getCustomerName()))
                    .refundAmount(row.getRefundAmount())
                    .policyType(record.getPolicyType())
                    .policyApplied(policyDisplay)
                    .refundScope(record.getScope())
                    .breakdownLines(breakdown)
                    .compensationItems(row.getCompensationItems())
                    .compensationValue(row.getCompensationValue())
                    .operatorUsername(record.getOperatorUsername())
                    .timestamp(record.getExecutedAt())
                    .batchOperationId(record.getOperationId())
                    .build();
            out.add(receipt);
            // Pair the receipt with the previously-recorded batch email so
            // the management screen can preview both off one entry.
            attachReceiptToLatestBatch(record.getOperationId(),
                    row.getBookingReference(), receipt);
        }
        return out;
    }

    /**
     * Demo helper — builds a CompensationPackage applicable to <strong>every</strong>
     * customer (not just VIPs) used by the Batch Cancellation Dashboard's
     * emergency flow.
     *
     * @param movieAmount             ticket price for the booking (drives Half-price face value)
     * @param includeHalfPriceVoucher whether to add a half-price voucher line
     * @param includeFreeDrinkCoupon  whether to add a free-drink coupon line
     * @return compensation package; empty when both flags are {@code false}
     */
    public CompensationPackage buildBatchCompensation(BigDecimal movieAmount,
                                                      boolean includeHalfPriceVoucher,
                                                      boolean includeFreeDrinkCoupon) {
        CompensationPackage.Builder b = new CompensationPackage.Builder()
                .headline("EMERGENCY GOODWILL COMPENSATION");
        BigDecimal m = movieAmount == null ? BigDecimal.ZERO : movieAmount;
        if (includeHalfPriceVoucher && m.signum() > 0) {
            BigDecimal value = m.multiply(new BigDecimal("0.50"));
            b.add(new CompensationItem(CompensationItemType.HALF_PRICE_VOUCHER, value));
        }
        if (includeFreeDrinkCoupon) {
            b.add(new CompensationItem(CompensationItemType.FREE_DRINK_COUPON,
                    new BigDecimal("4.50")));
        }
        return b.build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<String> synthesiseBreakdown(BatchRefundResult row, RefundScope scope) {
        List<String> out = new ArrayList<>();
        String pct = scope == RefundScope.FULL ? "100%" : "50%";
        out.add("Emergency Policy: " + pct + " refund applied to selected line items.");
        out.add("• Total refund → " + formatMoney(row.getRefundAmount()));
        return out;
    }

    private String safeName(String name) {
        return (name == null || name.isBlank()) ? "Valued Customer" : name;
    }

    private String safeEmail(String email) {
        return (email == null || email.isBlank()) ? "customer@hcbs-cinema.example" : email;
    }

    private String extractCinemaName(Booking booking) {
        if (booking == null) return null;
        Screening s = booking.getScreening();
        if (s == null || s.getScreen() == null) return null;
        Cinema c = s.getScreen().getCinema();
        return c == null ? null : c.getName();
    }

    private String extractFilmTitle(Booking booking) {
        if (booking == null || booking.getScreening() == null
                || booking.getScreening().getFilm() == null) {
            return null;
        }
        return booking.getScreening().getFilm().getTitle();
    }

    private String extractScreeningInfo(Booking booking) {
        if (booking == null) return null;
        Screening s = booking.getScreening();
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        if (s.getScreeningDate() != null) sb.append(s.getScreeningDate());
        if (s.getStartTime() != null) sb.append(' ').append(s.getStartTime());
        if (s.getFormat() != null) sb.append("  ·  ").append(s.getFormat());
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * Records a single-flow email straight into the email log. Receipts
     * built for the same booking later are attached to this entry by
     * {@link #attachReceiptToLatestSingle(String, CancellationReceipt)}.
     */
    private void recordSingle(CancellationEmail email,
                              CancellationReceipt receipt,
                              PolicyType policyType) {
        if (email == null) return;
        emailLogService.record(
                EmailLogEntry.Source.SINGLE,
                null,
                email,
                receipt,
                templateKeyFor(policyType),
                EmailLogEntry.Status.SENT);
    }

    private String templateKeyFor(PolicyType policyType) {
        if (policyType == null) return EmailLogService.TEMPLATE_SINGLE_STANDARD;
        switch (policyType) {
            case VIP:       return EmailLogService.TEMPLATE_SINGLE_VIP;
            case EMERGENCY: return EmailLogService.TEMPLATE_SINGLE_EMERGENCY;
            case STANDARD:
            default:        return EmailLogService.TEMPLATE_SINGLE_STANDARD;
        }
    }

    /**
     * Finds the most recent single-flow log entry for the booking and
     * attaches the freshly built receipt to it. The log entry is replaced
     * (not mutated) because {@link EmailLogEntry} keeps its email/receipt
     * fields immutable on purpose.
     */
    private void attachReceiptToLatestSingle(String bookingRef,
                                             CancellationReceipt receipt) {
        if (bookingRef == null || receipt == null) return;
        emailLogService.findAll().stream()
                .filter(e -> e.getSource() == EmailLogEntry.Source.SINGLE)
                .filter(e -> bookingRef.equals(e.getBookingReference()))
                .filter(e -> e.getReceipt() == null)
                .findFirst()
                .ifPresent(existing -> {
                    // Remove the old entry (receipt-less) and re-record so
                    // only one entry per single-cancellation flow remains.
                    emailLogService.remove(existing);
                    emailLogService.record(
                            existing.getSource(),
                            existing.getBatchOperationId(),
                            existing.getEmail(),
                            receipt,
                            existing.getTemplateKey(),
                            existing.getStatus());
                });
    }

    private void attachReceiptToLatestBatch(String operationId,
                                            String bookingRef,
                                            CancellationReceipt receipt) {
        if (operationId == null || bookingRef == null || receipt == null) return;
        emailLogService.findAll().stream()
                .filter(e -> e.getSource() == EmailLogEntry.Source.BATCH)
                .filter(e -> operationId.equals(e.getBatchOperationId()))
                .filter(e -> bookingRef.equals(e.getBookingReference()))
                .filter(e -> e.getReceipt() == null)
                .findFirst()
                .ifPresent(existing -> {
                    emailLogService.remove(existing);
                    emailLogService.record(
                            existing.getSource(),
                            existing.getBatchOperationId(),
                            existing.getEmail(),
                            receipt,
                            existing.getTemplateKey(),
                            existing.getStatus());
                });
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return "£" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Helper exposed for callers that want a 1-year expiry date. */
    public static LocalDate oneYearExpiry() {
        return LocalDate.now().plusYears(1);
    }
}
