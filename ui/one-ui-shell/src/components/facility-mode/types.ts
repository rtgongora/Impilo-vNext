/**
 * Facility Mode read-model types (C4 FacilityModeContext).
 *
 * Mirrors the frozen shared read-model produced by TUSO (single producer) and
 * composed by the experience-bff FacilityModeController. T4 owns this namespace.
 */

export interface FacilityModeDepartment {
  departmentId: number;
  name: string;
  serviceLine: string | null;
}

export interface FacilityModeServicePoint {
  servicePointId: string;
  departmentId: number | null;
  name: string;
  queueId: string | null;
}

export interface FacilityModeFacilityNode {
  facilityId: number;
  /** Canonical cross-service UUID (tuso facility_uuid) — the PCT/staff-binding key. */
  facilityUuid: string | null;
  tenantId: string | null;
  code: string | null;
  name: string;
  type: string | null;
  regulatoryStatus: string | null;
  departments: FacilityModeDepartment[];
  servicePoints: FacilityModeServicePoint[];
}

export interface FacilityModeSetupState {
  departmentsConfigured: boolean;
  servicePointsConfigured: boolean;
  queuesConfigured: boolean;
  workflowsConfigured: boolean;
  workforceLinked: boolean;
  orosRoutingConfigured: boolean;
  khulumaChannelsConfigured: boolean;
  fundoReady: boolean;
  goLive: boolean;
}

export interface FacilityModeOps {
  openEncounters: number;
  queueDepth: number;
  bedOccupancy: number | null;
}

export type FacilityModeVisibility = "ROW_DETAIL" | "AGGREGATE_ONLY";

export interface FacilityModeContext {
  facility: FacilityModeFacilityNode;
  setupState: FacilityModeSetupState;
  ops: FacilityModeOps;
  visibility: FacilityModeVisibility;
}

export interface FacilityModeContextEnvelope {
  contract: string;
  source: string;
  context: FacilityModeContext;
  liveOps?: { waiting?: number; inProgress?: number; total?: number } | null;
  liveOpsSource?: "PCT" | "UNAVAILABLE";
}

export interface FacilitySetupStepState {
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

export const SETUP_STEPS: Array<{ key: string; label: string; flag: keyof FacilitySetupStepState }> = [
  { key: "DEPARTMENTS", label: "Departments", flag: "departmentsConfigured" },
  { key: "SERVICE_POINTS", label: "Service points", flag: "servicePointsConfigured" },
  { key: "QUEUES", label: "Queues", flag: "queuesConfigured" },
  { key: "WORKFLOWS", label: "Workflows", flag: "workflowsConfigured" },
  { key: "WORKFORCE", label: "Workforce", flag: "workforceLinked" },
  { key: "OROS_ROUTING", label: "OROS routing", flag: "orosRoutingConfigured" },
  { key: "KHULUMA_CHANNELS", label: "Khuluma channels", flag: "khulumaChannelsConfigured" },
  { key: "FUNDO_READINESS", label: "Fundo readiness", flag: "fundoReady" },
  { key: "GO_LIVE", label: "Go live", flag: "goLive" },
];
