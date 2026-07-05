/**
 * PCT API service layer.
 *
 * All calls route through apiClient which injects trust headers
 * (X-Tenant-Id, X-Actor-Id, X-Correlation-Id, etc.) before they
 * reach Envoy ext_authz -> TSHEPO -> PCT.
 */

import { apiClient } from "./apiClient";

// ---------------------------------------------------------------------------
// Types — Work Session
// ---------------------------------------------------------------------------

export interface WorkStartRequest {
  facilityId: string;
  workspaceId: string;
  dutyMode: "CLINICAL" | "ADMIN" | "VIRTUAL";
}

export interface WorkSession {
  shiftId: string;
  actorId: string;
  actorDisplayName: string;
  facilityId: string;
  facilityName: string;
  workspaceId: string;
  workspaceName: string;
  dutyMode: "CLINICAL" | "ADMIN" | "VIRTUAL";
  startedAt: string;
  endedAt: string | null;
  status: "ACTIVE" | "ENDED";
}

export interface WorkContext {
  activeShift: WorkSession | null;
  facilityId: string;
  facilityName: string;
  workspaceId: string;
  workspaceName: string;
}

// ---------------------------------------------------------------------------
// Types — Journey
// ---------------------------------------------------------------------------

export interface JourneyStartRequest {
  patientCpid: string;
  facilityId: string;
}

export interface TriageRequest {
  acuity: 1 | 2 | 3 | 4 | 5;
  vitals?: {
    systolicBp?: number;
    diastolicBp?: number;
    heartRate?: number;
    temperature?: number;
    respiratoryRate?: number;
    oxygenSaturation?: number;
  };
  notes?: string;
}

export interface RouteRequest {
  targetQueueId: string;
  priority?: "NORMAL" | "HIGH" | "URGENT";
}

export type JourneyState =
  | "ARRIVED"
  | "TRIAGED"
  | "QUEUED"
  | "IN_SERVICE"
  | "ENCOUNTER_STARTED"
  | "ADMITTED"
  | "DISCHARGED"
  | "DEATH_RECORDED"
  | "COMPLETED"
  | "CANCELLED";

export interface Journey {
  id: string;
  patientCpid: string;
  facilityId: string;
  state: JourneyState;
  acuity: number | null;
  tokenNumber: string | null;
  currentQueueId: string | null;
  currentQueueName: string | null;
  encounterId: string | null;
  admissionId: string | null;
  startedAt: string;
  completedAt: string | null;
  triageNotes: string | null;
}

// ---------------------------------------------------------------------------
// Types — Queue
// ---------------------------------------------------------------------------

export type QueueItemStatus =
  | "WAITING"
  | "CALLED"
  | "IN_SERVICE"
  | "COMPLETED"
  | "NO_SHOW"
  | "TRANSFERRED"
  | "CANCELLED";

export interface Queue {
  id: string;
  name: string;
  facilityId: string;
  workspaceId: string;
  waitingCount: number;
  inServiceCount: number;
  averageWaitMinutes: number;
  active: boolean;
}

export interface QueueItem {
  id: string;
  queueId: string;
  journeyId: string;
  patientCpid: string;
  tokenNumber: string;
  priority: "NORMAL" | "HIGH" | "URGENT";
  status: QueueItemStatus;
  acuity: number | null;
  enqueuedAt: string;
  calledAt: string | null;
  serviceStartedAt: string | null;
  completedAt: string | null;
  /** Escalation facts — null unless the item has been escalated. */
  escalatedAt?: string | null;
  escalatedBy?: string | null;
  escalationReason?: string | null;
}

export interface QueueStatusUpdateRequest {
  status: QueueItemStatus;
}

export interface QueueTransferRequest {
  targetQueueId: string;
  reason: string;
}

export interface QueueEscalateRequest {
  /** Mandatory operational reason — PCT rejects blank reasons. */
  reason: string;
  /** Optional destination queue (e.g. emergency workspace). */
  targetQueueId?: string;
  /** Optional explicit priority override on the 1–5 scale. */
  priority?: number;
}

// ---------------------------------------------------------------------------
// Types — Encounter
// ---------------------------------------------------------------------------

export interface Encounter {
  id: string;
  journeyId: string;
  patientCpid: string;
  facilityId: string;
  practitionerId: string;
  type: string;
  status: "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  startedAt: string;
  completedAt: string | null;
  notes: string | null;
}

// ---------------------------------------------------------------------------
// Types — Admission
// ---------------------------------------------------------------------------

export interface AdmitRequest {
  wardId: string;
  reason: string;
  expectedLos?: number;
}

export interface BedAssignRequest {
  bedId: string;
}

export interface AdmissionTransferRequest {
  targetWardId: string;
  targetBedId?: string;
  reason: string;
}

export interface Admission {
  id: string;
  journeyId: string;
  patientCpid: string;
  facilityId: string;
  wardId: string;
  wardName: string;
  bedId: string | null;
  bedLabel: string | null;
  status: "PENDING_APPROVAL" | "APPROVED" | "ADMITTED" | "TRANSFERRED" | "DISCHARGED";
  reason: string;
  admittedAt: string | null;
  dischargedAt: string | null;
}

// ---------------------------------------------------------------------------
// Types — Discharge
// ---------------------------------------------------------------------------

export interface DischargeCase {
  id: string;
  journeyId: string;
  admissionId: string;
  patientCpid: string;
  status: "IN_PROGRESS" | "BLOCKED" | "READY" | "COMPLETED";
  blockers: DischargeBlocker[];
  startedAt: string;
  completedAt: string | null;
}

export interface DischargeBlocker {
  id: string;
  type: string;
  description: string;
  resolved: boolean;
  resolvedAt: string | null;
}

// ---------------------------------------------------------------------------
// Types — Death
// ---------------------------------------------------------------------------

export interface DeathRecordRequest {
  dateOfDeath: string;
  causeOfDeath: string;
  placeOfDeath: string;
  certifiedById: string;
}

export interface DeathCase {
  id: string;
  journeyId: string;
  patientCpid: string;
  dateOfDeath: string;
  causeOfDeath: string;
  placeOfDeath: string;
  certifiedById: string;
  ubomiStatus: "PENDING" | "SUBMITTED" | "ACCEPTED" | "REJECTED";
  status: "RECORDED" | "UBOMI_PENDING" | "COMPLETED";
  recordedAt: string;
  completedAt: string | null;
}

// ---------------------------------------------------------------------------
// Types — Control Tower
// ---------------------------------------------------------------------------

export interface ControlTowerData {
  facilityId: string;
  facilityName: string;
  timestamp: string;
  queueStats: QueueStat[];
  admissionSummary: AdmissionSummary;
  journeysByState: Record<string, number>;
  activeJourneyCount: number;
  averageTotalWaitMinutes: number;
}

export interface QueueStat {
  queueId: string;
  queueName: string;
  workspaceId: string;
  workspaceName: string;
  waitingCount: number;
  inServiceCount: number;
  completedToday: number;
  averageWaitMinutes: number;
  longestWaitMinutes: number;
}

export interface AdmissionSummary {
  totalBeds: number;
  occupiedBeds: number;
  occupancyPercent: number;
  wardBreakdown: WardOccupancy[];
}

export interface WardOccupancy {
  wardId: string;
  wardName: string;
  totalBeds: number;
  occupiedBeds: number;
  occupancyPercent: number;
}

export interface Bottleneck {
  id: string;
  type: "LONG_WAIT" | "QUEUE_OVERFLOW" | "DISCHARGE_BLOCKED" | "BED_SHORTAGE" | "STAFF_SHORTAGE";
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  description: string;
  affectedQueueId: string | null;
  affectedWardId: string | null;
  detectedAt: string;
  resolved: boolean;
}

export interface QueueTelemetry {
  queueId: string;
  queueName: string;
  timestamps: string[];
  waitingCounts: number[];
  avgWaitMinutes: number[];
}

// ---------------------------------------------------------------------------
// Types — Tasks
// ---------------------------------------------------------------------------

export interface TaskCreateRequest {
  journeyId?: string;
  workspaceId: string;
  title: string;
  description?: string;
  priority: "LOW" | "NORMAL" | "HIGH" | "URGENT";
  assigneeId?: string;
  dueAt?: string;
}

export interface Task {
  id: string;
  journeyId: string | null;
  workspaceId: string;
  title: string;
  description: string | null;
  priority: "LOW" | "NORMAL" | "HIGH" | "URGENT";
  status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  assigneeId: string | null;
  assigneeName: string | null;
  createdById: string;
  createdAt: string;
  dueAt: string | null;
  completedAt: string | null;
}

// ---------------------------------------------------------------------------
// Types — Patient Timeline
// ---------------------------------------------------------------------------

export interface PatientTimelineEntry {
  id: string;
  journeyId: string;
  type: string;
  summary: string;
  timestamp: string;
  actorId: string;
  actorName: string;
}

export interface PatientTimeline {
  cpid: string;
  entries: PatientTimelineEntry[];
  totalEntries: number;
}

// ---------------------------------------------------------------------------
// API Functions
// ---------------------------------------------------------------------------

const V1 = "/v1";

export const pctApi = {
  // -- Work Session --
  startShift: (facilityId: string, workspaceId: string, dutyMode: "CLINICAL" | "ADMIN" | "VIRTUAL") =>
    apiClient.post<WorkSession>(`${V1}/work/start`, { facilityId, workspaceId, dutyMode }),

  endShift: () =>
    apiClient.post<WorkSession>(`${V1}/work/end`),

  getWorkContext: () =>
    apiClient.get<WorkContext>(`${V1}/work/context`),

  // -- Journeys --
  startJourney: (patientCpid: string, facilityId: string) =>
    apiClient.post<Journey>(`${V1}/journeys/start`, { patientCpid, facilityId }),

  getJourney: (journeyId: string) =>
    apiClient.get<Journey>(`${V1}/journeys/${encodeURIComponent(journeyId)}`),

  recordTriage: (journeyId: string, acuity: number, vitals?: TriageRequest["vitals"], notes?: string) =>
    apiClient.post<Journey>(
      `${V1}/journeys/${encodeURIComponent(journeyId)}/triage`,
      { acuity, vitals, notes },
    ),

  routePatient: (journeyId: string, targetQueueId: string, priority?: "NORMAL" | "HIGH" | "URGENT") =>
    apiClient.post<QueueItem>(
      `${V1}/journeys/${encodeURIComponent(journeyId)}/route`,
      { targetQueueId, priority: priority ?? "NORMAL" },
    ),

  // -- Queues --
  getQueues: (facilityId?: string, workspaceId?: string) => {
    const params = new URLSearchParams();
    if (facilityId) params.set("facilityId", facilityId);
    if (workspaceId) params.set("workspaceId", workspaceId);
    const qs = params.toString();
    return apiClient.get<Queue[]>(`${V1}/queues${qs ? `?${qs}` : ""}`);
  },

  callNext: (queueId: string) =>
    apiClient.post<QueueItem>(`${V1}/queues/${encodeURIComponent(queueId)}/call-next`),

  getQueueItems: (queueId: string) =>
    apiClient.get<QueueItem[]>(`${V1}/queues/${encodeURIComponent(queueId)}/items`),

  updateQueueItemStatus: (itemId: string, status: QueueItemStatus) =>
    apiClient.post<QueueItem>(
      `${V1}/queue-items/${encodeURIComponent(itemId)}/status`,
      { status },
    ),

  transferQueueItem: (itemId: string, targetQueueId: string, reason: string) =>
    apiClient.post<QueueItem>(
      `${V1}/queue-items/${encodeURIComponent(itemId)}/transfer`,
      { targetQueueId, reason },
    ),

  escalateQueueItem: (itemId: string, request: QueueEscalateRequest) =>
    apiClient.post<QueueItem>(
      `${V1}/queue-items/${encodeURIComponent(itemId)}/escalate`,
      request,
    ),

  // -- Encounters --
  startEncounter: (journeyId: string) =>
    apiClient.post<Encounter>(`${V1}/journeys/${encodeURIComponent(journeyId)}/encounter/start`),

  completeEncounter: (encounterId: string) =>
    apiClient.post<Encounter>(`${V1}/encounters/${encodeURIComponent(encounterId)}/complete`),

  getEncounter: (encounterId: string) =>
    apiClient.get<Encounter>(`${V1}/encounters/${encodeURIComponent(encounterId)}`),

  getPatientTimeline: (cpid: string) =>
    apiClient.get<PatientTimeline>(`${V1}/patient/${encodeURIComponent(cpid)}/timeline`),

  // -- Admissions --
  requestAdmission: (journeyId: string, wardId: string, reason: string, expectedLos?: number) =>
    apiClient.post<Admission>(
      `${V1}/journeys/${encodeURIComponent(journeyId)}/admit`,
      { wardId, reason, expectedLos },
    ),

  approveAdmission: (admissionId: string) =>
    apiClient.post<Admission>(`${V1}/admissions/${encodeURIComponent(admissionId)}/approve`),

  admitPatient: (admissionId: string) =>
    apiClient.post<Admission>(`${V1}/admissions/${encodeURIComponent(admissionId)}/admit`),

  assignBed: (admissionId: string, bedId: string) =>
    apiClient.post<Admission>(
      `${V1}/admissions/${encodeURIComponent(admissionId)}/assign-bed`,
      { bedId },
    ),

  transferAdmission: (admissionId: string, targetWardId: string, targetBedId?: string, reason?: string) =>
    apiClient.post<Admission>(
      `${V1}/admissions/${encodeURIComponent(admissionId)}/transfer`,
      { targetWardId, targetBedId, reason },
    ),

  // -- Discharge --
  startDischarge: (journeyId: string) =>
    apiClient.post<DischargeCase>(`${V1}/journeys/${encodeURIComponent(journeyId)}/discharge/start`),

  getDischargeStatus: (caseId: string) =>
    apiClient.get<DischargeCase>(`${V1}/discharge/${encodeURIComponent(caseId)}/status`),

  clearDischargeBlocker: (caseId: string, blockerId: string) =>
    apiClient.post<DischargeCase>(
      `${V1}/discharge/${encodeURIComponent(caseId)}/clear-blocker`,
      { blockerId },
    ),

  completeDischarge: (caseId: string) =>
    apiClient.post<DischargeCase>(`${V1}/discharge/${encodeURIComponent(caseId)}/complete`),

  // -- Death --
  recordDeath: (journeyId: string, data: DeathRecordRequest) =>
    apiClient.post<DeathCase>(
      `${V1}/journeys/${encodeURIComponent(journeyId)}/death/record`,
      data,
    ),

  updateUbomiStatus: (caseId: string, ubomiStatus: string) =>
    apiClient.post<DeathCase>(
      `${V1}/death/${encodeURIComponent(caseId)}/ubomi-status`,
      { ubomiStatus },
    ),

  completeDeath: (caseId: string) =>
    apiClient.post<DeathCase>(`${V1}/death/${encodeURIComponent(caseId)}/complete`),

  // -- Control Tower --
  getControlTower: (facilityId: string) =>
    apiClient.get<ControlTowerData>(`${V1}/ops/control-tower?facilityId=${encodeURIComponent(facilityId)}`),

  getBottlenecks: (facilityId: string) =>
    apiClient.get<Bottleneck[]>(`${V1}/ops/bottlenecks?facilityId=${encodeURIComponent(facilityId)}`),

  getQueueTelemetry: (facilityId: string) =>
    apiClient.get<QueueTelemetry[]>(`${V1}/ops/queues/telemetry?facilityId=${encodeURIComponent(facilityId)}`),

  // -- Tasks --
  createTask: (data: TaskCreateRequest) =>
    apiClient.post<Task>(`${V1}/tasks`, data),

  completeTask: (taskId: string) =>
    apiClient.post<Task>(`${V1}/tasks/${encodeURIComponent(taskId)}/complete`),

  getMyTasks: () =>
    apiClient.get<Task[]>(`${V1}/tasks/my`),

  getWorkspaceTasks: (workspaceId: string) =>
    apiClient.get<Task[]>(`${V1}/tasks/workspace/${encodeURIComponent(workspaceId)}`),

  getJourneyTasks: (journeyId: string) =>
    apiClient.get<Task[]>(`${V1}/tasks/journey/${encodeURIComponent(journeyId)}`),
};
