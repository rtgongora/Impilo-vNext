import { getAdminGovernance } from "./client";
import type { LookupEnvelope, ProviderWorkerEligibility } from "../types";

export async function searchProviderWorkers(params: Record<string, string | undefined>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value) query.set(key, value);
  });
  return getAdminGovernance<LookupEnvelope<{ items: Record<string, unknown>[]; lookupAvailable?: boolean }>>(
    `/provider-workers/search?${query.toString()}`,
    "Provider/Worker lookup is not currently available; assignment cannot be completed until professional eligibility can be verified.",
  );
}

export async function getProviderWorkerEligibility(providerWorkerId: string) {
  return getAdminGovernance<LookupEnvelope<{ eligibility: ProviderWorkerEligibility }>>(
    `/provider-workers/${encodeURIComponent(providerWorkerId)}/eligibility`,
    "Provider/Worker lookup is not currently available; assignment cannot be completed until professional eligibility can be verified.",
  );
}

export function eligibilityBlocksAssignment(eligibility?: ProviderWorkerEligibility | null): boolean {
  if (!eligibility?.lookupAvailable) return true;
  return (eligibility.blockedReasons?.length ?? 0) > 0;
}

export function lookupUnavailableMessage(eligibility?: ProviderWorkerEligibility | null): string {
  return eligibility?.integrationStatus === "pending_backend"
    ? "Provider/Worker lookup is not currently available; assignment cannot be completed until professional eligibility can be verified."
    : "";
}
