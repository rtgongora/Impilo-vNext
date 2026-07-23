/**
 * Wave OF-C — telemonitoring view types, mirroring the frozen
 * telemonitoring-service API DTOs verbatim (the BFF forwards payloads
 * unmodified — MonitoringPlanDtos / DeviceAssignmentDtos / AlertEpisodeDtos /
 * ReadingController shapes).
 */

export type PlanStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "ACTIVE"
  | "SUSPENDED"
  | "COMPLETED"
  | "CANCELLED";

export type AlertStatus = "OPEN" | "ACKNOWLEDGED" | "IN_PROGRESS" | "ESCALATED" | "RESOLVED";

export type AlertSeverity = "AMBER" | "RED" | "EMERGENCY";

export type CalibrationPosture = "OK" | "DEGRADED" | "UNVERIFIED" | "UNTRACKED";

export type DeviceAssignmentStatus = "ASSIGNED" | "ACTIVE" | "SUSPENDED" | "RETURNED" | "LOST";

export interface MonitoringPlanView {
  id: string;
  tenantId: string;
  patientCpid: string;
  programmeCode: string;
  status: PlanStatus;
  orosOrderId: string | null;
  clinicalIndication: string | null;
  monitoringSetting: string | null;
  suggestedBy: string | null;
  suggestedSystemVersion: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  startAt: string | null;
  endAt: string | null;
  reviewCadence: string | null;
  reviewDueAt: string | null;
  careTeamJson: string | null;
  lifecycleReason: string | null;
  consentReference: string | null;
  consentStatus: string | null;
  consentUpdatedAt: string | null;
  lastReviewAt: string | null;
  reviewDueNotifiedAt: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface ThresholdProfileView {
  id: string;
  planId: string;
  version: number;
  supersedes: string | null;
  parametersJson: string;
  reason: string | null;
  createdBy: string | null;
  approvedBy: string | null;
  createdAt: string;
}

export interface MonitoringProgrammeView {
  id: string;
  code: string;
  name: string;
  description: string | null;
  conditionCode: string | null;
  defaultThresholdsJson: string | null;
  active: boolean;
  version: number;
}

export interface DeviceAssignmentView {
  assignmentId: string;
  tenantId: string;
  planId: string;
  patientCpid: string;
  deviceRef: string;
  assetRef: string | null;
  status: DeviceAssignmentStatus;
  assignmentType: string | null;
  assignedBy: string | null;
  assignedAt: string | null;
  activatedAt: string | null;
  activatedBy: string | null;
  trainingConfirmed: boolean;
  returnedAt: string | null;
  lifecycleReason: string | null;
  notes: string | null;
}

/** The assignment-gate answer — the only lane carrying calibration posture. */
export interface ResolvedAssignmentView {
  assignmentId: string;
  tenantId: string;
  planId: string;
  patientCpid: string;
  deviceRef: string;
  assetRef: string | null;
  status: DeviceAssignmentStatus;
  calibrationPosture: CalibrationPosture;
  calibrationPostureReason: string | null;
  activatedAt: string | null;
}

export interface ReadingView {
  id: string;
  deviceRef: string;
  planId: string | null;
  patientCpid: string | null;
  metricCode: string;
  metricValue: number;
  unit: string | null;
  measuredAt: string;
  receivedAt: string | null;
  status: "VALID" | "DEGRADED_VALID" | "QUARANTINED";
  quarantineReason: string | null;
  qualityStamp: string | null;
  shrWriteState: string;
  shrWrittenAt: string | null;
  shrRef: string | null;
}

export interface PagedReadingsView {
  readings: ReadingView[];
  page: number;
  totalElements: number;
  totalPages: number;
}

/** One §14.7 ladder-history node (JSON raw-value passthrough from the episode). */
export interface LadderHistoryNode {
  rung?: number;
  actor?: string;
  action?: string;
  note?: string | null;
  at?: string;
  [key: string]: unknown;
}

/** One contributing reading snapshot (JSON raw-value passthrough). */
export interface ContributingReading {
  metricCode?: string;
  value?: number;
  metricValue?: number;
  unit?: string;
  measuredAt?: string;
  qualityStamp?: unknown;
  [key: string]: unknown;
}

export interface AlertEpisodeView {
  episodeId: string;
  planId: string;
  patientCpid: string;
  deviceRef: string | null;
  triggerClass: "THRESHOLD_BREACH" | "REPEAT_ABNORMAL" | "DEVICE_OFFLINE" | "DEVICE_QUALITY";
  metricCode: string | null;
  initialSeverity: AlertSeverity;
  severity: AlertSeverity;
  status: AlertStatus;
  severityCeilingApplied: boolean;
  contributingReadings: ContributingReading[] | null;
  ladderHistory: LadderHistoryNode[] | null;
  currentRung: number | null;
  breachCount: number;
  responseDeadlineAt: string | null;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  autoEscalatedAt: string | null;
  chwTaskDispatched: boolean | null;
  reviewReferralRef: string | null;
  emsIncidentRef: string | null;
  emsDispatched: boolean | null;
  assumptionOfCareNote: string | null;
  assumptionOfCareBy: string | null;
  resolvedBy: string | null;
  assessment: string | null;
  actionTaken: string | null;
  outcome: string | null;
  resolvedAt: string | null;
  createdAt: string;
}

/** §14.6 accountable closure — all four fields are mandatory, enforced UI-side AND upstream. */
export interface AccountableClosure {
  resolvedBy: string;
  assessment: string;
  action: string;
  outcome: string;
}
