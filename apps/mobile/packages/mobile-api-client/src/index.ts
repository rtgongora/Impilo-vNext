/**
 * @impilo/mobile-api-client — API Client SDK
 *
 * HTTP client with v1.1 header injection, ApiEnvelope parsing,
 * idempotency, retry, and error handling.
 * All 4 mobile apps MUST use this package for API communication.
 */

// Configuration
export { configureApiClient, getApiClientConfig } from "./config";
export type { ApiClientConfig } from "./config";

// Client
export { apiClient, onStepUpRequired } from "./client";
export type { RequestOptions, ApiResponse } from "./client";

// Errors
export { ApiError, NetworkError, TimeoutError, StepUpRequiredError, isRetryable } from "./errors";

// Pagination
export { fetchPage, fetchAllPages, buildPaginationQuery } from "./pagination";
export type { PaginationParams } from "./pagination";
