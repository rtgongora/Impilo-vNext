package zw.gov.mohcc.impilo.credential.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_log", schema = "credential_verification")
public class VerificationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "verification_token", nullable = false, length = 64)
    private String verificationToken;

    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "verifier_ip", length = 50)
    private String verifierIp;

    @Column(name = "verifier_agent", length = 500)
    private String verifierAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }

    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getVerifierIp() { return verifierIp; }
    public void setVerifierIp(String verifierIp) { this.verifierIp = verifierIp; }

    public String getVerifierAgent() { return verifierAgent; }
    public void setVerifierAgent(String verifierAgent) { this.verifierAgent = verifierAgent; }

    public Instant getCreatedAt() { return createdAt; }
}
