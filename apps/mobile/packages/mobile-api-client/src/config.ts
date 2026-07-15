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
  /**
   * Tenant identifier sent on ANONYMOUS public-lane requests (pre-authentication).
   * Matches the BFF's public default tenant so anonymous gateway traffic is
   * consistent server-side.
   */
  publicTenantId: string;
  /** Pod identifier sent on ANONYMOUS public-lane requests (pre-authentication). */
  publicPodId: string;
}

const DEFAULT_CONFIG: ApiClientConfig = {
  baseUrl: "http://localhost:8160",
  defaultTimeoutMs: 30_000,
  maxRetries: 3,
  retryBaseDelayMs: 1_000,
  // Mirrors PublicGatewayAnonymousDefaultsFilter.PUBLIC_DEFAULT_TENANT / PUBLIC_POD.
  publicTenantId: "00000000-0000-0000-0000-000000000001",
  publicPodId: "national-spine",
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
