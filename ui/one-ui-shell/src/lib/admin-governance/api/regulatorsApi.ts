import { getAdminGovernance } from "./client";
import type { LookupEnvelope } from "../types";

export async function getCouncilProfessionalStatus(params: Record<string, string | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value) query.set(key, value);
  });
  return getAdminGovernance<LookupEnvelope<{ professionalStatus: Record<string, unknown> }>>(
    `/regulators/professional-status?${query.toString()}`,
    "Council/HPA professional status lookup unavailable.",
  );
}
