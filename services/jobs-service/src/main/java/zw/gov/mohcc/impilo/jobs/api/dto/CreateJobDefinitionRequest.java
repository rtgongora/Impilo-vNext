package zw.gov.mohcc.impilo.jobs.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for creating a new job definition.
 */
public class CreateJobDefinitionRequest {

    @NotNull
    private UUID tenantId;

    @NotBlank
    private String name;

    private String cronExpression;

    @NotBlank
    private String jobType;

    private String config;

    private Boolean enabled = true;

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
