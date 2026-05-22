package com.eduaccess.ui;

import com.eduaccess.domain.Booking;
import com.eduaccess.domain.BookingSeat;
import com.eduaccess.domain.BookingStatus;
import com.eduaccess.service.CancellationService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Collectors;

@Route(value = "cancellation", layout = MainLayout.class)
@PageTitle("HCBS — Cancellation")
public class CancellationView extends VerticalLayout {

    private final CancellationService cancellationService;

    private final TextField bookingReferenceField = new TextField("Booking reference");
    private final TextArea bookingDetailsArea = new TextArea("Booking details");
    private final TextArea cancellationResultArea = new TextArea("Cancellation result");
    private final Button cancelButton = new Button("Cancel booking");

    private Booking currentBooking;

    public CancellationView(CancellationService cancellationService) {
        this.cancellationService = cancellationService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 title = new H2("Booking Cancellation");
        Paragraph description = new Paragraph(
                "Search for a booking reference and cancel the booking if it is still eligible for cancellation."
        );

        configureReferenceField();
        configureTextAreas();
        configureCancelButton();

        Button searchButton = new Button("Search booking", event -> searchBooking());

        HorizontalLayout searchRow = new HorizontalLayout(bookingReferenceField, searchButton, cancelButton);
        searchRow.setAlignItems(Alignment.END);
        searchRow.getStyle().set("flex-wrap", "wrap");

        add(
                title,
                description,
                searchRow,
                bookingDetailsArea,
                cancellationResultArea
        );
    }

    private void configureReferenceField() {
        bookingReferenceField.setWidth("420px");
        bookingReferenceField.setPlaceholder("e.g. HCBS-20260509-ABC123");
        bookingReferenceField.setClearButtonVisible(true);
    }

    private void configureTextAreas() {
        bookingDetailsArea.setWidthFull();
        bookingDetailsArea.setHeight("260px");
        bookingDetailsArea.setReadOnly(true);
        bookingDetailsArea.setPlaceholder("Booking information will appear here.");

        cancellationResultArea.setWidthFull();
        cancellationResultArea.setHeight("180px");
        cancellationResultArea.setReadOnly(true);
        cancellationResultArea.setPlaceholder("Cancellation result will appear here.");
    }

    private void configureCancelButton() {
        cancelButton.setEnabled(false);
        cancelButton.addClickListener(event -> cancelCurrentBooking());
    }

    private void searchBooking() {
        String reference = bookingReferenceField.getValue();

        currentBooking = cancellationService.findBookingByReference(reference)
                .orElse(null);

        cancellationResultArea.clear();

        if (currentBooking == null) {
            bookingDetailsArea.setValue("No booking was found for this reference.");
            cancelButton.setEnabled(false);
            Notification.show("Booking not found.");
            return;
        }

        bookingDetailsArea.setValue(buildBookingDetails(currentBooking));
        cancelButton.setEnabled(currentBooking.getStatus() == BookingStatus.CONFIRMED);
    }

    private void cancelCurrentBooking() {
        if (currentBooking == null) {
            Notification.show("Please search for a booking first.");
            return;
        }

        try {
            CancellationService.CancellationResult result =
                    cancellationService.cancelBooking(currentBooking.getBookingReference());

            currentBooking = result.booking();

            bookingDetailsArea.setValue(buildBookingDetails(currentBooking));
            cancellationResultArea.setValue(buildCancellationResult(result));
            cancelButton.setEnabled(false);

            Notification.show("Booking cancelled successfully.");

        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage());
        }
    }

    private String buildBookingDetails(Booking booking) {
        String seats = booking.getBookingSeats()
                .stream()
                .sorted(Comparator.comparing(bs -> bs.getSeat().getSeatNumber()))
                .map(BookingSeat::getSeat)
                .map(seat -> seat.getSeatNumber() + " (" + seat.getSeatType() + ")")
                .collect(Collectors.joining(", "));

        return """
                Booking Reference: %s
                Customer Name: %s
                Customer Email: %s
                
                Film: %s
                Cinema: %s
                City: %s
                Date: %s
                Showing Time: %s
                Screen: %s
                Seats: %s
                
                Total Booking Cost: %s
                Booking Date: %s
                Status: %s
                """.formatted(
                booking.getBookingReference(),
                booking.getCustomerName(),
                booking.getCustomerEmail(),
                booking.getScreening().getFilm().getTitle(),
                booking.getScreening().getScreen().getCinema().getName(),
                booking.getScreening().getScreen().getCinema().getCity(),
                booking.getScreening().getScreeningDate(),
                booking.getScreening().getStartTime(),
                booking.getScreening().getScreen().getScreenNumber(),
                seats,
                formatMoney(booking.getTotalCost()),
                booking.getBookingDate(),
                booking.getStatus()
        );
    }

    private String buildCancellationResult(CancellationService.CancellationResult result) {
        return """
                Cancellation Successful
                
                Booking Reference: %s
                Original Booking Cost: %s
                Cancellation Charge: %s
                Refund Amount: %s
                Updated Status: %s
                
                The selected seats have been released and can now be booked again.
                """.formatted(
                result.booking().getBookingReference(),
                formatMoney(result.booking().getTotalCost()),
                formatMoney(result.cancellationCharge()),
                formatMoney(result.refundAmount()),
                result.booking().getStatus()
        );
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.UK).format(amount);
    }
}