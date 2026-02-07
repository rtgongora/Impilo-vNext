package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for adding an artifact to a pack.
 *
 * @param artifactId the ID of the artifact to add
 */
public record AddPackArtifactRequest(@NotNull UUID artifactId) {}
