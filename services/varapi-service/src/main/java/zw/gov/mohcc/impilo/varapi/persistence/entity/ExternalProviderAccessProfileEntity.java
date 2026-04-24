package zw.gov.mohcc.impilo.varapi.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "external_provider_access_profile", schema = "varapi")
public class ExternalProviderAccessProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "temporary_provider_public_id", nullable = false, unique = true, length = 26)
    private String temporaryProviderPublicId;

    @Column(name = "vito_access_grant_ref", nullable = false, length = 64)
    private String vitoAccessGrantRef;

    @Column(name = "given_name")
    private String givenName;

    @Column(name = "family_name")
    private String familyName;

    @Column(name = "profession_or_cadre", length = 120)
    private String professionOrCadre;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 64)
    private String phone;

    @Column(name = "organisation_hint")
    private String organisationHint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "council_id")
    private CouncilEntity council;

    @Column(name = "council_registration_number", nullable = false, length = 120)
    private String councilRegistrationNumber;

    @Column(name = "self_assertion_state", nullable = false, length = 40)
    private String selfAssertionState = "CAPTURED";

    @Column(name = "verification_state", nullable = false, length = 40)
    private String verificationState = "PENDING";

    @Column(name = "trust_level", nullable = false, length = 64)
    private String trustLevel;

    @Column(name = "matched_provider_public_id", length = 26)
    private String matchedProviderPublicId;

    @Column(name = "linked_provider_public_id", length = 26)
    private String linkedProviderPublicId;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void pc() {
        Instant n = Instant.now();
        createdAt = n;
        updatedAt = n;
    }

    @PreUpdate
    void pu() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getTemporaryProviderPublicId() {
        return temporaryProviderPublicId;
    }

    public void setTemporaryProviderPublicId(String temporaryProviderPublicId) {
        this.temporaryProviderPublicId = temporaryProviderPublicId;
    }

    public String getVitoAccessGrantRef() {
        return vitoAccessGrantRef;
    }

    public void setVitoAccessGrantRef(String vitoAccessGrantRef) {
        this.vitoAccessGrantRef = vitoAccessGrantRef;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getProfessionOrCadre() {
        return professionOrCadre;
    }

    public void setProfessionOrCadre(String professionOrCadre) {
        this.professionOrCadre = professionOrCadre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOrganisationHint() {
        return organisationHint;
    }

    public void setOrganisationHint(String organisationHint) {
        this.organisationHint = organisationHint;
    }

    public CouncilEntity getCouncil() {
        return council;
    }

    public void setCouncil(CouncilEntity council) {
        this.council = council;
    }

    public String getCouncilRegistrationNumber() {
        return councilRegistrationNumber;
    }

    public void setCouncilRegistrationNumber(String councilRegistrationNumber) {
        this.councilRegistrationNumber = councilRegistrationNumber;
    }

    public String getSelfAssertionState() {
        return selfAssertionState;
    }

    public void setSelfAssertionState(String selfAssertionState) {
        this.selfAssertionState = selfAssertionState;
    }

    public String getVerificationState() {
        return verificationState;
    }

    public void setVerificationState(String verificationState) {
        this.verificationState = verificationState;
    }

    public String getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(String trustLevel) {
        this.trustLevel = trustLevel;
    }

    public String getMatchedProviderPublicId() {
        return matchedProviderPublicId;
    }

    public void setMatchedProviderPublicId(String matchedProviderPublicId) {
        this.matchedProviderPublicId = matchedProviderPublicId;
    }

    public String getLinkedProviderPublicId() {
        return linkedProviderPublicId;
    }

    public void setLinkedProviderPublicId(String linkedProviderPublicId) {
        this.linkedProviderPublicId = linkedProviderPublicId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
