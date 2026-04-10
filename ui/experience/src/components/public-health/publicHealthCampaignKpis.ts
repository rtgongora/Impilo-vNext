import type { PublicHealthCampaign } from "@/hooks/queries/usePublicHealth";
import { isPublicHealthCampaignActive } from "./publicHealthDashboardUtils";

export function countActivePublicHealthCampaigns(campaigns: PublicHealthCampaign[]): number {
  return campaigns.filter((c) => isPublicHealthCampaignActive(c.status)).length;
}

export function sumCampaignsReachedPopulation(campaigns: PublicHealthCampaign[]): number {
  return campaigns.reduce((acc, c) => acc + Math.max(0, c.reachedPopulation || 0), 0);
}

/** Weighted reached/target across campaigns with positive targetPopulation; null if no targets. */
export function weightedCampaignCoveragePercent(campaigns: PublicHealthCampaign[]): number | null {
  const totalTarget = campaigns.reduce((acc, c) => acc + Math.max(0, c.targetPopulation || 0), 0);
  if (totalTarget <= 0) return null;
  const reached = sumCampaignsReachedPopulation(campaigns);
  return Math.min(100, Math.round((reached / totalTarget) * 100));
}
