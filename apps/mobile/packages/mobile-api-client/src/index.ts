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

// Mvumo (Ring-0) — communication preference bundles and evaluate/offline hooks
export {
  patientRefForMvumoPath,
  communicationPreferencesPath,
  getCommunicationPreferences,
  putCommunicationPreferences,
  postCommunicationPreferencesEvaluate,
  postCommunicationPreferencesOfflineSync,
} from "./mvumo";
export type { MvumoCommunicationPreferencesEnvelope } from "./mvumo";

// Pagination
export { fetchPage, fetchAllPages, buildPaginationQuery } from "./pagination";
export type { PaginationParams } from "./pagination";

// Safe fetch wrapper
export { safeApi } from "./safeApi";
export type { ApiResult } from "./safeApi";
