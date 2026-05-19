package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

/**
 * Canonical telemedicine port. Adapters: ExternalVideoProviderAdapter,
 * ExternalMessagingProviderAdapter, NativeTelemedicineAdapter.
 */
public interface TelemedicineIntegrationPort extends Adapter {

    OperationResult<TelemedicineSession>  scheduleSession(SessionRequest request, OperationContext ctx);
    OperationResult<JoinUrls>             getJoinUrls(String sessionId, ParticipantRole role, OperationContext ctx);
    OperationResult<Void>                 endSession(String sessionId, String reason, OperationContext ctx);
    OperationResult<TelemedicineSession>  getSession(String sessionId, OperationContext ctx);

    enum ParticipantRole { CITIZEN, PROVIDER, OBSERVER }

    record SessionRequest(PatientReference patient, ProviderReference provider, String scheduledStartIso, String clinicalIndication) {}
    record TelemedicineSession(String sessionId, String status, String scheduledStartIso, String startedAtIso, String endedAtIso) {}
    record JoinUrls(String webUrl, String mobileUrl, String fallbackPhoneNumber) {}
}
