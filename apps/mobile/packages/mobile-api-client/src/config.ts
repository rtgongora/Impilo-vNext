/**
 * API Client Configuration — Service discovery and base URL management.
 *
 * Supports per-environment configuration (dev/staging/prod).
 * All mobile traffic routes through the experience-bff gateway.
 */

export interface ApiClientConfig {
  /** Base URL of the experience-bff gateway. */
  baseUrl: string;
  /** Default timeout in milliseconds for requests. */
  defaultTimeoutMs: number;
  /** Maximum number of retry attempts for retryable errors. */
  maxRetries: number;
  /** Base delay in ms for exponential backoff on retries. */
  retryBaseDelayMs: number;
  /** TLS certificate pins (SHA-256 hashes) for certificate pinning. */
  certificatePins?: string[];
}

const DEFAULT_CONFIG: ApiClientConfig = {
  baseUrl: "http://localhost:8160",
  defaultTimeoutMs: 30_000,
  maxRetries: 3,
  retryBaseDelayMs: 1_000,
};

let currentConfig: ApiClientConfig = { ...DEFAULT_CONFIG };

/**
 * Configure the API client. Call once at app startup.
 */
export function configureApiClient(config: Partial<ApiClientConfig>): void {
  currentConfig = { ...DEFAULT_CONFIG, ...config };
}

export function getApiClientConfig(): ApiClientConfig {
  return currentConfig;
}
