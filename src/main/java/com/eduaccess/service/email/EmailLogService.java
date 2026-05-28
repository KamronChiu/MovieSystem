package com.eduaccess.service.email;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * In-memory store for every cancellation email / receipt produced by
 * {@link EmailReceiptService}.
 * <p>
 * The store is the data source behind the Email Management screen. Entries
 * are kept newest-first in a thread-safe deque so the dashboard can render
 * an immediately-sorted history without any database access.
 * <p>
 * <strong>Why in-memory?</strong> Email delivery is simulated for the
 * demo; persisting the audit trail to H2 would require another set of JPA
 * entities and migrations without any extra teaching value. The behaviour
 * matches a real outbox the moment we point the {@link #record} method at
 * a SMTP / queue client.
 */
@Service
public class EmailLogService {

    /** Common template identifiers used by the management UI. */
    public static final String TEMPLATE_SINGLE_STANDARD  = "single.standard.refund";
    public static final String TEMPLATE_SINGLE_VIP       = "single.vip.refund";
    public static final String TEMPLATE_SINGLE_EMERGENCY = "single.emergency.refund";
    public static final String TEMPLATE_BATCH_EMERGENCY  = "batch.emergency.refund";

    /** Newest entry at the head of the deque. */
    private final ConcurrentLinkedDeque<EmailLogEntry> entries = new ConcurrentLinkedDeque<>();

    /**
     * Records a freshly built email/receipt pair as a single history entry.
     * Returns the persisted {@link EmailLogEntry} so callers can keep a
     * handle for later status updates if needed.
     */
    public EmailLogEntry record(EmailLogEntry.Source source,
                                String batchOperationId,
                                CancellationEmail email,
                                CancellationReceipt receipt,
                                String templateKey,
                                EmailLogEntry.Status initialStatus) {
        EmailLogEntry entry = new EmailLogEntry(
                source, batchOperationId, email, receipt, templateKey, initialStatus);
        entries.addFirst(entry);
        return entry;
    }

    /** Convenience overload — defaults to SENT. */
    public EmailLogEntry record(EmailLogEntry.Source source,
                                String batchOperationId,
                                CancellationEmail email,
                                CancellationReceipt receipt,
                                String templateKey) {
        return record(source, batchOperationId, email, receipt, templateKey,
                EmailLogEntry.Status.SENT);
    }

    /** Returns a defensive copy of the log, newest first. */
    public List<EmailLogEntry> findAll() {
        return new ArrayList<>(entries);
    }

    public Optional<EmailLogEntry> findById(String id) {
        if (id == null) return Optional.empty();
        return entries.stream().filter(e -> id.equals(e.getId())).findFirst();
    }

    /**
     * Flips an entry's delivery status. The operator can use the management
     * dashboard to mark a message as resent, failed or pending.
     */
    public Optional<EmailLogEntry> markStatus(String id,
                                              EmailLogEntry.Status status,
                                              String note) {
        Optional<EmailLogEntry> match = findById(id);
        match.ifPresent(e -> e.updateStatus(status, note));
        return match;
    }

    public long count() {
        return entries.size();
    }

    public long countByStatus(EmailLogEntry.Status status) {
        if (status == null) return 0;
        return entries.stream().filter(e -> e.getStatus() == status).count();
    }

    public long countBySource(EmailLogEntry.Source source) {
        if (source == null) return 0;
        return entries.stream().filter(e -> e.getSource() == source).count();
    }

    /** Used by the management screen's filter dropdown. */
    public List<EmailLogEntry> filter(EmailLogEntry.Source source,
                                      EmailLogEntry.Status status,
                                      String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return entries.stream()
                .filter(e -> source == null || e.getSource() == source)
                .filter(e -> status == null || e.getStatus() == status)
                .filter(e -> kw.isEmpty()
                        || (e.getCustomerName()      != null && e.getCustomerName().toLowerCase().contains(kw))
                        || (e.getCustomerEmail()     != null && e.getCustomerEmail().toLowerCase().contains(kw))
                        || (e.getBookingReference()  != null && e.getBookingReference().toLowerCase().contains(kw))
                        || (e.getSubject()           != null && e.getSubject().toLowerCase().contains(kw)))
                .collect(Collectors.toList());
    }

    /** Used by tests / admin reset. */
    public void clear() {
        entries.clear();
    }
}
