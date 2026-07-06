/**
 * Channel B (public workforce) employment-match — EC-lane re-point (IATG WS-D).
 *
 * POST /api/v1/trust/employment-match asks workforce-governance (the HSC
 * employment system of record) to match an EC number for the signed-in person
 * and, when employment is confirmed and a provider registration exists, asserts
 * EMPLOYMENT_MATCHED trust on the VARAPI provider ledger.
 *
 * IMPORTANT: this endpoint returns a RAW map (NOT the `{ data, meta }`
 * envelope), so the response is consumed as-is — never `.data`-unwrapped. The
 * subject Health ID is server-authoritative (taken from the X-Actor-ID trust
 * header injected by api-client), never sent in the body. EC numbers are always
 * masked in the response.
 *
 * Contract source: TrustProfileBffController.java:55 +
 * EmploymentMatchBffService.java:58-113.
 */

import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface EmploymentMatchInput {
  ecNumber: string;
  /** Numeric id or providerPublicId; when present, a trust assertion is attempted. */
  providerId?: string;
}

export type EmploymentMatchStatus = "OK" | "INVALID_REQUEST" | "UNAVAILABLE";

export type TrustAssertionStatus = "ASSERTED" | "REJECTED" | "UNAVAILABLE";

export interface TrustAssertion {
  status: TrustAssertionStatus;
  providerPublicId?: string;
  trustLevel?: string;
  registryStatus?: string;
  /** Present on REJECTED (e.g. 409 downgrade guard). */
  statusCode?: number;
  sourceOfRecord: string;
}

/** Raw employment-match response — consumed as-is, never `.data`-unwrapped. */
export interface EmploymentMatchResult {
  /** First-2-chars + "***" — the raw EC number never leaves governance. */
  maskedEcNumber?: string;
  status: EmploymentMatchStatus;
  matchStatus?:
    | "MATCHED_BOTH"
    | "MATCHED_EMPLOYMENT_ONLY"
    | "NOT_FOUND"
    | "CONFLICT"
    | string;
  message?: string;
  sourceOfRecord?: string;
  employmentId?: string;
  linkedHealthId?: string;
  cadre?: string;
  grade?: string;
  employmentStatus?: string;
  disciplinaryEmploymentStatus?: string;
  verificationStatus?: string;
  conflictingRecordCount?: number;
  postings?: unknown[];
  /** Present only when a provider registration was resolved and asserted against. */
  trustAssertion?: TrustAssertion;
  /** Wave 1 never creates providers — a match without a provider surfaces guidance. */
  providerCreationRequired?: boolean;
}

/** Error shape thrown by apiClient: `{ status, error?: { code, message } }`. */
export interface TrustApiError {
  status?: number | string;
  error?: { code?: string; message?: string };
  message?: string;
}

/**
 * Match the signed-in person's EC number and (when a provider exists) assert
 * EMPLOYMENT_MATCHED trust. The subject Health ID travels only in the trust
 * headers — the body carries the EC number (and optional provider id) alone.
 */
export function useEmploymentMatch() {
  return useMutation<EmploymentMatchResult, TrustApiError, EmploymentMatchInput>({
    // Raw map — consumed as-is, NOT `.data`-unwrapped.
    mutationFn: async (input) =>
      apiClient.post<EmploymentMatchResult>("/api/v1/trust/employment-match", input),
  });
}

/** Human-readable message from a trust error (honest copy travels in the body). */
export function employmentMatchErrorMessage(err: unknown, fallback: string): string {
  const e = err as TrustApiError | undefined;
  return e?.error?.message || e?.message || fallback;
}
