package zw.gov.mohcc.impilo.governance.mirror;

import java.util.UUID;

/**
 * In-process application event signalling that a {@code wgv_organisation} row
 * was written (create / status change / patch) and should be mirrored to
 * organization-registry.
 *
 * <p>Published inside the primary write transaction but consumed
 * {@code AFTER_COMMIT} by {@link OrgMirrorRelay}, so the mirror HTTP call is
 * never part of — and can never roll back — the primary wgv write. The tenant
 * and correlation ids are captured here (on the request thread) so the relay
 * can forward the trust headers the receiver requires.
 */
public record OrgMirrorEvent(
        OrgMirrorPayload payload,
        UUID tenantId,
        UUID correlationId,
        String changeType) {
}
