package zw.gov.mohcc.impilo.zibo.api.dto;

/**
 * Request body for updating a draft artifact's mutable fields.
 * All fields are optional; only non-null fields are applied.
 *
 * @param name        updated computer-friendly name
 * @param title       updated human-readable title
 * @param description updated narrative description
 * @param contentJson updated FHIR resource JSON content
 */
public record UpdateArtifactRequest(
        String name,
        String title,
        String description,
        Object contentJson
) {}
