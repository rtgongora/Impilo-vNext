package zw.gov.mohcc.impilo.zibo.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for submitting an asynchronous validation job.
 *
 * @param payloadJson the JSON payload to validate asynchronously
 */
public record SubmitJobRequest(@NotNull Object payloadJson) {}
