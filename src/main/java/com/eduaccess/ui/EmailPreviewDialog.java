package com.eduaccess.ui;

import com.eduaccess.service.compensation.CompensationItem;
import com.eduaccess.service.email.CancellationEmail;
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
 * Reusable Vaadin Dialog that renders a {@link CancellationEmail} preview
 * (TASK 12 §1 / §2 / §4). One instance can show either a single-flow email
 * or a list of batch emails.
 * <p>
 * The dialog is intentionally generic — it never reads from a service,
 * just renders the immutable VO it receives.
 */
public class EmailPreviewDialog extends Dialog {

    private static final String CARD_BG = "#f8fafc";
    private static final String LIGHT_TEXT = "#142033";
    private static final String LIGHT_MUTED = "#64748b";
    private static final String BLUE = "#0072ce";
    private static final String CARD_BORDER = "rgba(15,23,42,0.08)";
    private static final String VIP_PURPLE = "#7c3aed";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Single-email constructor.
     */
    public EmailPreviewDialog(CancellationEmail email) {
        configureBase("Email Preview");
        if (email != null) {
            add(buildEmailCard(email));
        }
        Button close = new Button("Close", ev -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(close);
    }

    /**
     * Batch-email constructor — renders one card per email.
     */
    public EmailPreviewDialog(List<CancellationEmail> emails, String headline) {
        configureBase(headline == null ? "Batch Email Preview" : headline);
        if (emails == null || emails.isEmpty()) {
            Span empty = new Span("No emails to preview — the batch contains no successful rows.");
            empty.getStyle().set("color", LIGHT_MUTED).set("font-style", "italic");
            add(empty);
        } else {
            Span hint = new Span("One email is generated per cancelled booking. "
                    + "Customers will receive the message below.");
            hint.getStyle()
                    .set("display", "block")
                    .set("color", LIGHT_MUTED)
                    .set("font-style", "italic")
                    .set("margin-bottom", "16px")
                    .set("font-size", "13px");
            add(hint);
            for (CancellationEmail email : emails) {
                add(buildEmailCard(email));
            }
        }
        Button close = new Button("Close", ev -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(close);
    }

    private void configureBase(String title) {
        setHeaderTitle(title);
        setWidth("760px");
        setMaxHeight("88vh");
        setDraggable(true);
        setResizable(true);
        getElement().getStyle().set("color", LIGHT_TEXT);
    }

    private Div buildEmailCard(CancellationEmail email) {
        Div card = new Div();
        card.getStyle()
                .set("background", CARD_BG)
                .set("border", "1px solid " + CARD_BORDER)
                .set("border-radius", "10px")
                .set("padding", "20px 22px")
                .set("margin-bottom", "16px");

        // Header — To / Subject
        H3 subject = new H3(email.getSubject() == null
                ? "Cancellation Confirmation" : email.getSubject());
        subject.getStyle().set("margin", "0 0 4px 0").set("color", BLUE)
                .set("font-weight", "900");
        Span to = new Span("To: " + email.getTo());
        to.getStyle().set("display", "block").set("font-size", "12px")
                .set("color", LIGHT_MUTED).set("margin-bottom", "2px");
        Span when = new Span("Sent: "
                + (email.getCancellationTime() == null ? "—" : email.getCancellationTime().format(STAMP)));
        when.getStyle().set("display", "block").set("font-size", "12px")
                .set("color", LIGHT_MUTED);
        card.add(subject, to, when);

        // Quick-look meta strip
        Div strip = new Div();
        strip.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(3, minmax(0,1fr))")
                .set("gap", "10px")
                .set("margin", "14px 0");
        strip.add(metaCell("Booking", safe(email.getBookingReference()), BLUE));
        strip.add(metaCell("Refund", formatMoney(email.getRefundAmount()), "#16a34a"));
        strip.add(metaCell("Compensation",
                email.getCompensationItems().isEmpty()
                        ? "—" : describeCompensation(email.getCompensationItems()),
                VIP_PURPLE));
        card.add(strip);

        // Body lines
        Div body = new Div();
        body.getStyle()
                .set("background", "white")
                .set("border", "1px solid " + CARD_BORDER)
                .set("border-radius", "8px")
                .set("padding", "14px 18px")
                .set("font-family", "monospace")
                .set("font-size", "12.5px")
                .set("line-height", "1.7")
                .set("white-space", "pre-wrap")
                .set("color", LIGHT_TEXT);
        StringBuilder sb = new StringBuilder();
        for (String line : email.getBodyLines()) {
            sb.append(line).append('\n');
        }
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
                .set("font-size", "14px")
                .set("font-weight", "800")
                .set("color", LIGHT_TEXT);
        cell.add(lab, val);
        return cell;
    }

    private static String describeCompensation(List<CompensationItem> items) {
        if (items.isEmpty()) return "—";
        if (items.size() == 1) return items.get(0).getName();
        return items.get(0).getName() + " +" + (items.size() - 1) + " more";
    }

    private static String formatMoney(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return "£" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String safe(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }
}
