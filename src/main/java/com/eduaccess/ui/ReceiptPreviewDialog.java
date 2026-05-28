package com.eduaccess.ui;

import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.email.CancellationReceipt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reusable Vaadin Dialog that renders one {@link CancellationReceipt}
 * or a list of receipts (TASK 12 §3 / §4).
 * <p>
 * The receipt is rendered with a print-friendly monospace block so the
 * operator can quickly export it. Different policies produce visibly
 * different breakdown lines (TASK 12 §5 demonstration).
 */
public class ReceiptPreviewDialog extends Dialog {

    private static final String CARD_BG = "#f8fafc";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String BLUE = "#0072ce";
    private static final String CARD_BORDER = "rgba(15,23,42,0.08)";
    private static final String VIP_PURPLE = "#7c3aed";
    private static final String GREEN = "#16a34a";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Single-receipt constructor.
     */
    public ReceiptPreviewDialog(CancellationReceipt receipt) {
        configureBase("Receipt Preview");
        if (receipt != null) {
            add(buildReceiptCard(receipt));
        }
        Button close = new Button("Close", ev -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(close);
    }

    /**
     * Batch-receipt constructor.
     */
    public ReceiptPreviewDialog(List<CancellationReceipt> receipts, String headline) {
        configureBase(headline == null ? "Batch Receipt Preview" : headline);
        if (receipts == null || receipts.isEmpty()) {
            Span empty = new Span("No receipts to preview — the batch contains no successful rows.");
            empty.getStyle().set("color", LIGHT_MUTED).set("font-style", "italic");
            add(empty);
        } else {
            Span hint = new Span(receipts.size()
                    + " cancellation receipt(s) generated for this batch operation.");
            hint.getStyle()
                    .set("display", "block")
                    .set("color", LIGHT_MUTED)
                    .set("font-style", "italic")
                    .set("margin-bottom", "16px")
                    .set("font-size", "13px");
            add(hint);
            for (CancellationReceipt receipt : receipts) {
                add(buildReceiptCard(receipt));
            }
        }
        Button close = new Button("Close", ev -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(close);
    }

    private void configureBase(String title) {
        setHeaderTitle(title);
        setWidth("720px");
        setMaxHeight("88vh");
        setDraggable(true);
        setResizable(true);
        getElement().getStyle().set("color", LIGHT_TEXT);
    }

    private Div buildReceiptCard(CancellationReceipt r) {
        Div card = new Div();
        card.getStyle()
                .set("background", CARD_BG)
                .set("border", "1px solid " + CARD_BORDER)
                .set("border-radius", "10px")
                .set("padding", "22px 24px")
                .set("margin-bottom", "16px");

        // Title
        H3 title = new H3("CANCELLATION RECEIPT");
        title.getStyle()
                .set("margin", "0")
                .set("color", BLUE)
                .set("font-weight", "950")
                .set("letter-spacing", "0.1em");
        Span subtitle = new Span(r.getBatchOperationId() == null
                ? "Single-booking refund record"
                : "Part of batch operation OP-" + r.getBatchOperationId().substring(0, 8).toUpperCase());
        subtitle.getStyle().set("display", "block").set("color", LIGHT_MUTED)
                .set("font-size", "12px").set("font-style", "italic")
                .set("margin-top", "4px");
        card.add(title, subtitle);

        // Tracking strip
        Div strip = new Div();
        strip.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(3, minmax(0,1fr))")
                .set("gap", "10px")
                .set("margin", "14px 0");
        strip.add(metaCell("Booking", safe(r.getBookingReference()), BLUE));
        strip.add(metaCell("Operator", safe(r.getOperatorUsername()), VIP_PURPLE));
        strip.add(metaCell("Timestamp",
                r.getTimestamp() == null ? "—" : r.getTimestamp().format(STAMP), GREEN));
        card.add(strip);

        // Print-style receipt body
        Div body = new Div();
        body.getStyle()
                .set("background", "white")
                .set("border", "1px solid " + CARD_BORDER)
                .set("border-radius", "8px")
                .set("padding", "16px 20px")
                .set("font-family", "monospace")
                .set("font-size", "12.5px")
                .set("line-height", "1.7")
                .set("white-space", "pre-wrap")
                .set("color", LIGHT_TEXT);
        StringBuilder sb = new StringBuilder();
        sb.append("Cancellation Receipt\n");
        sb.append("---------------------------------------------\n");
        sb.append("Booking Reference : ").append(safe(r.getBookingReference())).append('\n');
        sb.append("Customer          : ").append(safe(r.getCustomerName())).append('\n');
        if (r.getCinemaName() != null) {
            sb.append("Cinema            : ").append(r.getCinemaName()).append('\n');
        }
        sb.append("Policy Applied    : ").append(safe(r.getPolicyApplied()));
        if (r.getRefundScope() != null) {
            sb.append(" (").append(r.getRefundScope().getDisplayName()).append(')');
        }
        sb.append('\n');
        sb.append("Refund Amount     : ").append(formatMoney(r.getRefundAmount())).append('\n');
        sb.append("Compensation      : ").append(formatMoney(r.getCompensationValue())).append('\n');
        sb.append("Operator          : ").append(safe(r.getOperatorUsername())).append('\n');
        sb.append("Timestamp         : ")
                .append(r.getTimestamp() == null ? "—" : r.getTimestamp().format(STAMP)).append('\n');
        sb.append("---------------------------------------------\n");
        sb.append("Refund Breakdown:\n");
        if (r.getBreakdownLines().isEmpty()) {
            sb.append("  (no breakdown lines provided)\n");
        } else {
            for (String line : r.getBreakdownLines()) {
                sb.append("  ").append(line).append('\n');
            }
        }
        if (!r.getCompensationItems().isEmpty()) {
            sb.append("---------------------------------------------\n");
            sb.append("Compensation Items:\n");
            for (CompensationItem c : r.getCompensationItems()) {
                sb.append("  - ").append(c.getName())
                        .append("  ").append(formatMoney(c.getValue()))
                        .append("  (valid until ").append(c.getExpiryDate()).append(")\n");
            }
        }
        sb.append("=============================================\n");
        sb.append("Thank you for choosing HCBS Cinemas.\n");
        body.setText(sb.toString());
        card.add(body);

        return card;
    }

    private Div metaCell(String label, String value, String accent) {
        Div cell = new Div();
        cell.getStyle()
                .set("background", "white")
                .set("border-left", "4px solid " + accent)
                .set("border-radius", "8px")
                .set("padding", "10px 12px");
        Span lab = new Span(label);
        lab.getStyle()
                .set("display", "block")
                .set("font-size", "10.5px")
                .set("font-weight", "800")
                .set("letter-spacing", "0.08em")
                .set("color", LIGHT_MUTED)
                .set("text-transform", "uppercase")
                .set("margin-bottom", "4px");
        Span val = new Span(value == null ? "—" : value);
        val.getStyle()
                .set("font-size", "13.5px")
                .set("font-weight", "800")
                .set("color", LIGHT_TEXT);
        cell.add(lab, val);
        return cell;
    }

    private static String formatMoney(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return "£" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String safe(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }
}
