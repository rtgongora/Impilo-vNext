package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventAttendanceEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventCertificateEntity;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.live.persistence.repository.LiveEventCertificateRepository;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CertificateService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final LiveEventService eventService;
    private final AttendanceService attendanceService;
    private final LiveEventCertificateRepository certificateRepository;
    private final LiveEventEmitter emitter;
    private final LiveIntegrationOrchestrator integrationOrchestrator;

    public CertificateService(LiveEventService eventService,
                              AttendanceService attendanceService,
                              LiveEventCertificateRepository certificateRepository,
                              LiveEventEmitter emitter,
                              LiveIntegrationOrchestrator integrationOrchestrator) {
        this.eventService = eventService;
        this.attendanceService = attendanceService;
        this.certificateRepository = certificateRepository;
        this.emitter = emitter;
        this.integrationOrchestrator = integrationOrchestrator;
    }

    @Transactional
    public LiveEventCertificateEntity issueAttendanceCertificate(UUID tenantId, UUID eventId,
                                                                  String participantId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        LiveEventAttendanceEntity attendance = attendanceService.get(tenantId, eventId, participantId);
        if (!attendance.isEligibleForCertificate()) {
            throw new IllegalStateException("Participant not eligible for attendance certificate");
        }
        return issue(tenantId, event, eventId, participantId, "ATTENDANCE",
                "CERT-ATT-" + eventId.toString().substring(0, 8).toUpperCase());
    }

    @Transactional
    public LiveEventCertificateEntity issueCpdCertificate(UUID tenantId, UUID eventId,
                                                           String participantId) {
        LiveEventEntity event = eventService.get(tenantId, eventId);
        if (!event.isCpdEnabled()) {
            throw new IllegalStateException("CPD is not enabled for this event");
        }
        LiveEventAttendanceEntity attendance = attendanceService.get(tenantId, eventId, participantId);
        if (!attendance.isEligibleForCpd()) {
            throw new IllegalStateException("Participant not eligible for CPD certificate");
        }
        LiveEventCertificateEntity cert = issue(tenantId, event, eventId, participantId, "CPD",
                "CERT-CPD-" + eventId.toString().substring(0, 8).toUpperCase());
        integrationOrchestrator.recordFundoCpdCompletion(event, participantId);
        return cert;
    }

    @Transactional(readOnly = true)
    public List<LiveEventCertificateEntity> listForEvent(UUID tenantId, UUID eventId) {
        eventService.get(tenantId, eventId);
        return certificateRepository.findByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public LiveEventCertificateEntity verify(String verificationCode) {
        return certificateRepository.findByVerificationCode(verificationCode)
                .orElseThrow(() -> new IllegalArgumentException("Certificate not found"));
    }

    private LiveEventCertificateEntity issue(UUID tenantId, LiveEventEntity event, UUID eventId,
                                              String participantId, String type, String certRef) {
        LiveEventCertificateEntity cert = certificateRepository
                .findByEventIdAndParticipantIdAndCertificateType(eventId, participantId, type)
                .orElseGet(LiveEventCertificateEntity::new);
        cert.setEventId(eventId);
        cert.setParticipantId(participantId);
        cert.setCertificateType(type);
        cert.setCertificateRef(certRef);
        if (cert.getVerificationCode() == null) {
            cert.setVerificationCode(generateVerificationCode());
        }
        LiveEventCertificateEntity saved = certificateRepository.save(cert);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certificateId", saved.getId().toString());
        payload.put("eventId", eventId.toString());
        payload.put("participantId", participantId);
        payload.put("certificateType", type);
        payload.put("verificationCode", saved.getVerificationCode());
        emitter.emit(tenantId, "LIVE_CERTIFICATE", saved.getId().toString(),
                "impilo.live.certificate.issued.v1", "LIVE_EVENT", eventId.toString(), payload);
        return saved;
    }

    private static String generateVerificationCode() {
        return "LV-" + String.format("%08X", RANDOM.nextInt());
    }
}
