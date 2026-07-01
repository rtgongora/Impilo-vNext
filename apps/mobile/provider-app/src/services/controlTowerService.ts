/**
 * Control Tower Service — facility operations command view for providers/supervisors.
 *
 * Backend: experience-bff facility-mode control tower (same live routes the web shell consumes
 * via ui/one-ui-shell/src/components/facility-mode/useControlTower.ts):
 *   - GET  /internal/v1/facility-mode/control-tower/aggregate
 *   - GET  /internal/v1/facility-mode/{facilityId}/control-tower/alerts?status=OPEN
 *   - POST /internal/v1/facility-mode/control-tower/alerts/{alertId}/acknowledge
 *
 * Closes GAP-19 (facility control tower had no mobile surface). Real data only — no mock rows.
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/facility-mode";

export interface ControlTowerFacility {
  facilityId: number;
  name: string;
  openAlerts: number;
  bedOccupancyPercent: number | null;
}

export interface ControlTowerAggregate {
  facilityCount: number;
  openAlertCount: number;
  visibility: "ROW_DETAIL" | "AGGREGATE_ONLY";
  facilities: ControlTowerFacility[];
}

export interface FacilityAlert {
  id: string;
  facilityId: number;
  facilityName: string;
  alertType: string;
  severity: string;
  title: string;
  description: string | null;
  status: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  createdAt: string;
}

/** Fleet-wide aggregate — facility count, open alerts, and per-facility rows (trust-scoped visibility). */
export async function fetchControlTowerAggregate(): Promise<ControlTowerAggregate> {
  const response = await apiClient.get<{ data: ControlTowerAggregate }>(
    `${V1}/control-tower/aggregate`,
  );
  return response.data.data;
}

/** Open (or other status) alerts for a single facility. */
export async function fetchFacilityAlerts(
  facilityId: string | number,
  status = "OPEN",
): Promise<FacilityAlert[]> {
  const response = await apiClient.get<{ data: FacilityAlert[] }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/control-tower/alerts?status=${encodeURIComponent(status)}`,
  );
  return response.data.data;
}

/** Acknowledge a control-tower alert; returns the updated alert. */
export async function acknowledgeAlert(alertId: string): Promise<FacilityAlert> {
  const response = await apiClient.post<{ data: FacilityAlert }>(
    `${V1}/control-tower/alerts/${encodeURIComponent(alertId)}/acknowledge`,
  );
  return response.data.data;
}
