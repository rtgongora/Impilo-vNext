"use client";

import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { ProgrammeGuidance } from "./usePrograms";

/**
 * Governed adult-medicine decision support (W4–W6 content packs).
 *
 * Evaluates a topic's versioned DAK content against the facts supplied. The result reuses
 * {@link ProgrammeGuidance} because the CKP evaluator returns one shape for every pack — a second
 * near-identical type would drift from it.
 *
 * This is a mutation rather than a query on purpose: an evaluation is an act with an audit trail on
 * the server, not a cacheable read, and firing it on render would assess patients nobody asked
 * about.
 *
 * The failure contract matters more than usual here. `apiClient` throws on a non-2xx, so `isError`
 * is set and the caller must render it: an evaluation that did not run has no alerts, and "no
 * alerts" from a failed call is indistinguishable on screen from a clean assessment unless the
 * screen says which. The BFF returns 502 precisely so this cannot be mistaken for an all-clear.
 */

/** The eight topics the CKP evaluator serves. Kebab-case is the wire form the BFF path expects. */
export const MEDICINE_CDS_TOPICS = [
  "cvd-risk",
  "deprescribing",
  "procedure-indication",
  "icope",
  "mhgap",
  "antimicrobial-stewardship",
  "palliative",
  "oncology",
] as const;

export type MedicineCdsTopic = (typeof MEDICINE_CDS_TOPICS)[number];

export interface MedicineCdsRequest {
  topic: MedicineCdsTopic | string;
  facts: Record<string, unknown>;
}

export function useMedicineCds() {
  return useMutation<ApiResponse<ProgrammeGuidance>, unknown, MedicineCdsRequest>({
    mutationFn: ({ topic, facts }) =>
      apiClient.post<ApiResponse<ProgrammeGuidance>>(
        `/internal/v1/medicine/cds/${topic}/evaluate`,
        facts,
      ),
  });
}
