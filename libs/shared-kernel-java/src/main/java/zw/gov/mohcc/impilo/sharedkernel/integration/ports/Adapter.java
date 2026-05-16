package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

/**
 * Base marker for every adapter implementation. Adapters MUST provide a
 * descriptor so Integration Hub can audit and route requests through them.
 *
 * <p>Implementations should be registered as Spring beans and discovered via
 * the canonical port interface (which itself extends {@code Adapter}).
 */
public interface Adapter {
    AdapterDescriptor descriptor();
}
