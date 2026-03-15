/**
 * Coverage Service — Citizen insurance/coverage visibility.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/coverage/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { CoverageInfo } from "../types";

const V1 = "/internal/v1/mobile/citizen/coverage";

export async function fetchCoverage(): Promise<CoverageInfo[]> {
  const response = await apiClient.get<{ data: CoverageInfo[] }>(V1);
  return response.data.data;
}

export async function fetchCoverageDetail(id: string): Promise<CoverageInfo> {
  const response = await apiClient.get<{ data: CoverageInfo }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}
