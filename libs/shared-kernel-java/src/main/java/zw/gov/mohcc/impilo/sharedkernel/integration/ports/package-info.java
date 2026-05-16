/**
 * Canonical integration ports for the Impilo vNext Health OS.
 *
 * <p>Each canonical port defines the abstract capability that the experience
 * layer calls. Vendor-specific or environment-specific adapters implement the
 * port. This is the architectural protection against vendor lock-in described
 * in {@code docs/architecture/CANONICAL_INTEGRATION_PORTS.md}.
 *
 * <p>Doctrine: The Impilo experience layer MUST call Impilo canonical services
 * (which in turn call the canonical port). Experience-layer code MUST NOT
 * depend on vendor-specific adapters directly.
 */
package zw.gov.mohcc.impilo.sharedkernel.integration.ports;
