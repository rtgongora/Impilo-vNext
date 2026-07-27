package zw.gov.mohcc.impilo.procedures.core;

import zw.gov.mohcc.impilo.procedures.api.dto.CompetenceDtos.PrivilegeRecord;

import java.util.List;

/**
 * The credentialing port. VARAPI is the system of record for registration, licence, scope and
 * privilege; this service reads and never writes.
 *
 * <p>An implementation MUST throw rather than return an empty list when it cannot reach VARAPI.
 * "No privileges found" and "could not ask" are different facts, and collapsing them turns an
 * outage into a confident denial — or, if the caller treats empty as permissive, into a
 * confident grant. The resolver depends on being able to tell them apart.</p>
 */
public interface PrivilegeSource {

    List<PrivilegeRecord> privilegesFor(String providerId);
}
