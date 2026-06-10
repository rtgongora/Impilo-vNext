import { getAdminGovernance } from "./client";
import type { LookupEnvelope } from "../types";

export async function searchHscEmployment(params: Record<string, string | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value) query.set(key, value);
  });
  return getAdminGovernance<LookupEnvelope<{ items: Record<string, unknown>[] }>>(
    `/hsc/employment-records/search?${query.toString()}`,
    "HSC employment lookup is not currently available.",
  );
}

export async function getHscEmploymentRecord(employmentRecordId: string) {
  return getAdminGovernance<LookupEnvelope>(`/hsc/employment-records/${encodeURIComponent(employmentRecordId)}`);
}
