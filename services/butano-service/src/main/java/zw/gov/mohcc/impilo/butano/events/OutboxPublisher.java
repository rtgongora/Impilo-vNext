package zw.gov.mohcc.impilo.butano.events;

/**
 * @deprecated Replaced by {@link ButanoOutboxPublisher} which extends the shared
 * {@code CompanionOutboxPublisher} base class with dual-emit (legacy + v1.1) support.
 * This class is retained only for reference and will be removed in a future release.
 */
@Deprecated(forRemoval = true)
final class OutboxPublisher {
    private OutboxPublisher() {}
}
