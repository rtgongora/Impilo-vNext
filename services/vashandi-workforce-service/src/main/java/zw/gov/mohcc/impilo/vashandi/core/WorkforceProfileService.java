package zw.gov.mohcc.impilo.vashandi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.api.VashandiDtos;
import zw.gov.mohcc.impilo.vashandi.integration.FundoIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.VarapiIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.integration.WorkforceGovernanceIntegrationClient;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;
import zw.gov.mohcc.impilo.vashandi.integration.IntegrationCheckResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkforceProfileService {

    private final WorkforceProfileRepository profileRepository;
    private final VarapiIntegrationClient varapiClient;
    private final WorkforceGovernanceIntegrationClient governanceClient;
    private final FundoIntegrationClient fundoClient;
    private final VashandiOutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public WorkforceProfileService(WorkforceProfileRepository profileRepository,
                                   VarapiIntegrationClient varapiClient,
                                   WorkforceGovernanceIntegrationClient governanceClient,
                                   FundoIntegrationClient fundoClient,
                                   VashandiOutboxWriter outboxWriter,
                                   ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.varapiClient = varapiClient;
        this.governanceClient = governanceClient;
        this.fundoClient = fundoClient;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public List<WorkforceProfileEntity> list(UUID tenantId) {
        return profileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public Optional<WorkforceProfileEntity> get(UUID tenantId, UUID id) {
        return profileRepository.findByTenantIdAndId(tenantId, id);
    }

    @Transactional
    public WorkforceProfileEntity create(UUID tenantId, VashandiDtos.CreateWorkforceProfileRequest request)
            throws Exception {
        WorkforceProfileEntity profile = new WorkforceProfileEntity();
        profile.setTenantId(tenantId);
        profile.setHealthId(request.healthId());
        profile.setProviderWorkerId(request.providerWorkerId());
        profile.setKeycloakUserId(request.keycloakUserId());
        profile.setPrimaryOrganisationId(request.primaryOrganisationId());
        profile.setPrimaryEmployerOrganisationId(request.primaryEmployerOrganisationId());
        profile.setWorkerType(request.workerType());
        profile.setProfession(request.profession());
        profile.setCadre(request.cadre());
        profile.setCurrentStatus(request.currentStatus() != null ? request.currentStatus() : "pending_appointment");
        WorkforceProfileEntity saved = profileRepository.save(profile);

        Map<String, Object> payload = new HashMap<>();
        payload.put("profileId", saved.getId().toString());
        payload.put("healthId", saved.getHealthId());
        outboxWriter.publish(tenantId, "WORKFORCE_PROFILE", saved.getId().toString(),
                "workforce_profile", "created",
                "vashandi:profile:created:" + saved.getId(), payload);
        return saved;
    }

    @Transactional
    public VashandiDtos.ReconcileProfileResponse reconcile(UUID tenantId, VashandiDtos.ReconcileProfileRequest request)
            throws Exception {
        WorkforceProfileEntity profile = profileRepository.findByTenantIdAndId(tenantId, request.profileId())
                .orElseThrow(() -> new IllegalArgumentException("profile not found"));

        IntegrationCheckResult varapi = varapiClient.fetchProfessionalStatus(profile.getProviderWorkerId());
        IntegrationCheckResult hsc = governanceClient.fetchHscEmploymentSummary(profile.getHealthId());
        IntegrationCheckResult fundo = fundoClient.fetchTrainingEvidence(profile.getProviderWorkerId());

        if (varapi.payload().isPresent()) {
            profile.setProfessionalStatusSummaryJson(objectMapper.writeValueAsString(varapi.payload().get()));
        }
        if (hsc.payload().isPresent()) {
            profile.setHscStatusSummaryJson(objectMapper.writeValueAsString(hsc.payload().get()));
        }
        if (fundo.payload().isPresent()) {
            profile.setTrainingStatusSummaryJson(objectMapper.writeValueAsString(fundo.payload().get()));
        }

        boolean allLive = varapi.isLive() && hsc.isLive() && fundo.isLive();
        String status = allLive ? "completed" : "pending_backend";
        profileRepository.save(profile);

        Map<String, Object> payload = new HashMap<>();
        payload.put("profileId", profile.getId().toString());
        payload.put("reconcileStatus", status);
        outboxWriter.publish(tenantId, "WORKFORCE_PROFILE", profile.getId().toString(),
                "workforce_profile", "reconciled",
                "vashandi:profile:reconciled:" + profile.getId() + ":" + System.currentTimeMillis(), payload);

        return new VashandiDtos.ReconcileProfileResponse(
                profile.getId(),
                status,
                List.of(varapi, hsc, fundo),
                profile
        );
    }
}
