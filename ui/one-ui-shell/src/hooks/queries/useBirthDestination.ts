/**
 * Birth destination — web hook.
 *
 * Backend: experience-bff `GET /internal/v1/maternity/birth-destination?facilityId=&requiredLevel=`,
 * which composes tuso's facility status-summary and EmONC readiness into a single honest verdict on
 * whether a facility can be where a woman gives birth for the level of care she needs. See
 * `BirthDestinationService.java` for the three honesty rules this hook must never launder away:
 *
 *   1. `NOT_OPERATIONAL` covers both "assessed as not operational" and "unknown" — an unconfirmed
 *      operational status is never treated as a safe destination.
 *   2. `CAPABILITY_UNKNOWN` ("we don't know") must never be collapsed into "cannot" — nobody has
 *      assessed the facility's EmONC readiness, which is the common case across the estate, not the
 *      exception. `call_ahead` is the honest next step, not a refusal.
 *   3. `UNAVAILABLE` (the BFF's 502, same body shape as a 200) is a read failure — tuso could not be
 *      reached. It is neither a safe nor an unsafe destination, and must never be rendered as either.
 */
"use client";

import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type BirthDestinationRequiredLevel = "CEMONC" | "BEMONC" | "UNKNOWN";

export type BirthDestinationStatus =
  | "MEETS_REQUIRED_LEVEL"
  | "BELOW_REQUIRED_LEVEL"
  | "CAPABILITY_UNKNOWN"
  | "NOT_OPERATIONAL"
  | "REQUIRED_LEVEL_UNKNOWN"
  | "UNAVAILABLE";

/** Exactly `BirthDestinationController#toMap`'s field set. */
export interface BirthDestinationVerdict {
  facility_id: number;
  required_level: BirthDestinationRequiredLevel;
  status: BirthDestinationStatus;
  operational_gate_passed: boolean;
  emonc_verdict: string | null;
  call_ahead: boolean;
  message: string;
  guidance: string;
}

export interface BirthDestinationMeta {
  request_id?: string;
  correlation_id?: string;
}

export interface BirthDestinationResponse {
  data: BirthDestinationVerdict;
  meta: BirthDestinationMeta;
}

/**
 * The BFF's 502 arrives with the same `{ data, meta }` shape as a 200 — the verdict itself carries
 * `status: "UNAVAILABLE"` plus a message/guidance explaining that tuso could not be reached. The
 * thrown error from `apiClient` is `{ status, data, meta }`, so `err.data` is the same verdict shape
 * a caller would read on success.
 */
export interface BirthDestinationRefusal {
  status: number;
  data?: BirthDestinationVerdict;
  meta?: BirthDestinationMeta;
}

export function isBirthDestinationRefusal(err: unknown): err is BirthDestinationRefusal {
  return typeof err === "object" && err !== null && "status" in err;
}

/** True for the 502 — tuso could not be reached. Never a statement that the facility can or cannot. */
export function isBirthDestinationUnavailable(err: unknown): boolean {
  return isBirthDestinationRefusal(err) && err.status === 502;
}

export interface BirthDestinationParams {
  facilityId: number;
  requiredLevel: BirthDestinationRequiredLevel;
}

/**
 * Assesses one facility against one required level. A mutation rather than a query, the same way
 * `useClassifyMaternalNearMiss` is: the caller triggers it deliberately (an "Assess" action), and the
 * result is never re-fetched silently behind the clinician's back.
 */
export function useBirthDestination() {
  return useMutation({
    mutationFn: ({ facilityId, requiredLevel }: BirthDestinationParams) => {
      const qs = new URLSearchParams({
        facilityId: String(facilityId),
        requiredLevel,
      });
      return apiClient.get<BirthDestinationResponse>(
        `/internal/v1/maternity/birth-destination?${qs.toString()}`,
      );
    },
  });
}
