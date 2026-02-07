package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;

/**
 * Request body for creating a new draft terminology artifact.
 *
 * @param fhirType     the FHIR resource type (CODE_SYSTEM, VALUE_SET, etc.)
 * @param canonicalUrl the canonical URL uniquely identifying this artifact
 * @param version      the semantic version string
 * @param name         the computer-friendly name
 * @param title        the human-readable title (optional)
 * @param description  a narrative description (optional)
 * @param contentJson  the full FHIR resource JSON content
 * @param publisher    the publishing organization or entity (optional)
 */
public record CreateArtifactRequest(
        @NotNull ArtifactType fhirType,
        @NotBlank String canonicalUrl,
        @NotBlank String version,
        @NotBlank String name,
        String title,
        String description,
        @NotNull Object contentJson,
        String publisher
) {}
