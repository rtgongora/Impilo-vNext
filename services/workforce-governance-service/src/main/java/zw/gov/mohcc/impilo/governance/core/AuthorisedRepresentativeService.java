package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.AuthorisedRepresentativeEntity;
import zw.gov.mohcc.impilo.governance.persistence.AuthorisedRepresentativeRepository;

import java.time.LocalDate;
import java.util.*;

@Service
public class AuthorisedRepresentativeService {

    private final AuthorisedRepresentativeRepository repository;
    private final ObjectMapper objectMapper;

    public AuthorisedRepresentativeService(AuthorisedRepresentativeRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public List<AuthorisedRepresentativeEntity> list(UUID tenantId, UUID organisationId) {
        return repository.findByTenantIdAndOrganisationIdOrderByUpdatedAtDesc(tenantId, organisationId);
    }

    @Transactional
    public AuthorisedRepresentativeEntity invite(UUID tenantId, UUID organisationId, Map<String, Object> request) {
        AuthorisedRepresentativeEntity entity = new AuthorisedRepresentativeEntity();
        entity.init(
                tenantId,
                organisationId,
                str(request.get("userId")),
                strOrDefault(request.get("representativeType"), "organisation_authorised_representative"),
                strOrDefault(request.get("roleTemplateId"), "organisation_authorised_representative"),
                strOrDefault(request.get("status"), "PENDING")
        );
        entity.setApprovedByUserId(str(request.get("approvedByUserId")));
        return repository.save(entity);
    }

    @Transactional
    public AuthorisedRepresentativeEntity update(UUID repId, Map<String, Object> request) {
        AuthorisedRepresentativeEntity entity = repository.findById(repId)
                .orElseThrow(() -> new IllegalArgumentException("Authorised representative not found"));
        if (request.containsKey("status")) entity.setStatus(str(request.get("status")));
        if (request.containsKey("roleTemplateId")) entity.setRoleTemplateId(str(request.get("roleTemplateId")));
        if (request.containsKey("dataResponsibilityAccepted")) {
            entity.acceptDataResponsibility();
        }
        return repository.save(entity);
    }

    private static String str(Object value) { return value == null ? "" : value.toString(); }
    private static String strOrDefault(Object value, String defaultValue) {
        String s = str(value);
        return s.isBlank() ? defaultValue : s;
    }
}
