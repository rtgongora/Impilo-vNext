/**
 * Facility Setup Service — facility go-live configuration (departments, service points, readiness).
 *
 * Backend: experience-bff facility-mode setup routes (same live routes the web facility setup wizard
 * consumes via ui/one-ui-shell/src/components/facility-mode/useFacilityMode.ts):
 *   - GET  /internal/v1/facility-mode/{facilityId}/setup
 *   - GET  /internal/v1/facility-mode/{facilityId}/units      | POST (create department)
 *   - GET  /internal/v1/facility-mode/{facilityId}/service-points | POST (create service point)
 *   - POST /internal/v1/facility-mode/{facilityId}/setup/steps  (advance a wizard step)
 *
 * Closes part of GAP-19 (facility setup had no mobile surface). Real data only — no mock rows.
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/facility-mode";

export interface FacilitySetupState {
  facilityId: number;
  departmentsConfigured: boolean;
  servicePointsConfigured: boolean;
  queuesConfigured: boolean;
  workflowsConfigured: boolean;
  workforceLinked: boolean;
  orosRoutingConfigured: boolean;
  khulumaChannelsConfigured: boolean;
  fundoReady: boolean;
  goLive: boolean;
  nextStep: string | null;
  readyForGoLive: boolean;
}

export interface FacilityUnit {
  id: number;
  name: string;
  unitType: string;
  serviceLine: string | null;
  registrationRequired: boolean;
  regulatoryStatus: string | null;
  certificateStatus: string | null;
}

export interface ServicePoint {
  id: string;
  facilityId: number;
  facilityUnitId: number | null;
  name: string;
  code: string | null;
  servicePointType: string;
  queueId: string | null;
  workflowArchetype: string | null;
  status: string;
  active: boolean;
}

export async function fetchSetupState(facilityId: string | number): Promise<FacilitySetupState> {
  const response = await apiClient.get<{ data: FacilitySetupState }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/setup`,
  );
  return response.data.data;
}

export async function fetchFacilityUnits(facilityId: string | number): Promise<FacilityUnit[]> {
  const response = await apiClient.get<{ data: FacilityUnit[] }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/units`,
  );
  return response.data.data;
}

export async function createFacilityUnit(
  facilityId: string | number,
  input: { name: string; unitType?: string; serviceLine?: string; registrationRequired?: boolean },
): Promise<FacilityUnit> {
  const response = await apiClient.post<{ data: FacilityUnit }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/units`,
    input,
  );
  return response.data.data;
}

export async function fetchServicePoints(facilityId: string | number): Promise<ServicePoint[]> {
  const response = await apiClient.get<{ data: ServicePoint[] }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/service-points`,
  );
  return response.data.data;
}

export async function createServicePoint(
  facilityId: string | number,
  input: {
    name: string;
    code?: string;
    servicePointType?: string;
    facilityUnitId?: number;
    queueId?: string;
    workflowArchetype?: string;
  },
): Promise<ServicePoint> {
  const response = await apiClient.post<{ data: ServicePoint }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/service-points`,
    input,
  );
  return response.data.data;
}

export async function advanceSetupStep(
  facilityId: string | number,
  input: { step: string; complete: boolean },
): Promise<FacilitySetupState> {
  const response = await apiClient.post<{ data: FacilitySetupState }>(
    `${V1}/${encodeURIComponent(String(facilityId))}/setup/steps`,
    input,
  );
  return response.data.data;
}
