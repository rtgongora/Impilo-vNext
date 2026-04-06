/**
 * Crowdfunding Service — Medical fundraising campaigns.
 */
import { apiClient } from "@impilo/mobile-api-client";
import type { CrowdfundingCampaign } from "../types";

const V1 = "/internal/v1/mobile/citizen/crowdfunding";

export async function fetchCampaigns(category?: string): Promise<CrowdfundingCampaign[]> {
  const params = category ? `?category=${category}` : "";
  const response = await apiClient.get<{ data: CrowdfundingCampaign[] }>(`${V1}${params}`);
  return response.data.data;
}

export async function donate(campaignId: string, body: {
  donorId: string; amount: number; message?: string; isAnonymous?: boolean;
}): Promise<{ donationId: string }> {
  const response = await apiClient.post<{ data: { donationId: string } }>(`${V1}/${campaignId}/donate`, body);
  return response.data.data;
}
