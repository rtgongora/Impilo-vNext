package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import java.util.List;

/**
 * Metadata that every adapter MUST expose. Used by the Integration Hub to
 * surface adapter health, provenance, and routing decisions.
 *
 * @param portCode         e.g. "ImagingIntegrationPort"
 * @param adapterCode      e.g. "DicomWebPacsAdapter"
 * @param adapterVersion   semver string for the adapter implementation
 * @param vendor           human-readable vendor name (or "Native" for first-party)
 * @param marketplaceItemCode optional Msika Apps item code if registered through marketplace
 * @param requiredScopes   list of scopes the adapter requires from TSHEPO
 * @param supportsEnvironments list of environments the adapter is approved for
 */
public record AdapterDescriptor(
        String portCode,
        String adapterCode,
        String adapterVersion,
        String vendor,
        String marketplaceItemCode,
        List<String> requiredScopes,
        List<String> supportsEnvironments
) {}
