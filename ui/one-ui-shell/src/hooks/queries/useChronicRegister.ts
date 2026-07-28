"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * A chronic-disease register (brief.md §8) — who at this facility is on it, and who needs recalling.
 *
 * The first cohort read the shell has. Every other clinical query here is about one patient; this one
 * is about a population, and the answer is a worklist rather than a report.
 *
 * `control_status` is nullable on purpose and that null carries meaning: nobody has assessed control
 * for that person. It is not a claim that they are doing badly and not a claim that they are doing
 * well, and the server lists those entries first because they are who a recall list exists to find.
 */

export const CHRONIC_REGISTERS = [
  { programme: "HYPERTENSION", label: "Hypertension" },
  { programme: "DIABETES", label: "Diabetes" },
  { programme: "CHRONIC_KIDNEY_DISEASE", label: "Chronic kidney disease" },
  { programme: "CHRONIC_RESPIRATORY", label: "Asthma and COPD" },
] as const;

export type ChronicRegisterProgramme = (typeof CHRONIC_REGISTERS)[number]["programme"];

export type ControlStatus =
  | "CONTROLLED"
  | "PARTIALLY_CONTROLLED"
  | "NOT_CONTROLLED"
  | "TARGET_NOT_SET";

export interface RegisterEntry {
  enrolment_id: string;
  subject_cpid: string;
  programme_number: string | null;
  status: string;
  enrolled_on: string;
  managing_facility_id: string | null;
  /** null means never assessed — distinct from every assessed value. */
  control_status: ControlStatus | null;
  control_assessed_on: string | null;
  individualised_target: string | null;
}

export interface ChronicRegister {
  programme: string;
  facility_id: string | null;
  entries: RegisterEntry[];
  total: number;
  never_assessed: number;
  control_status_null_means_never_assessed: string;
}

export function useChronicRegister(programme: string, facilityId: string | null) {
  return useQuery<ApiResponse<ChronicRegister>>({
    queryKey: ["chronic-register", { programme, facilityId }],
    queryFn: () =>
      apiClient.get<ApiResponse<ChronicRegister>>(
        `/internal/v1/programmes/register?programme=${encodeURIComponent(programme)}` +
          (facilityId ? `&facility_id=${encodeURIComponent(facilityId)}` : ""),
      ),
    enabled: !!programme,
  });
}
