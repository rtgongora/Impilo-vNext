/**
 * Madi — Blood Donation, Transfusion & Haemovigilance
 *
 * Frontend API surface for services/madi-service. All requests go through
 * `apiClient` so trust headers, correlation IDs, and step-up handling apply
 * uniformly. The Madi backend mounts at /internal/v1/madi.
 */

import { apiClient } from "./api-client";

// ============================================================================
// Domain types (mirror madi-service entities / responses)
// ============================================================================

export type ScreeningResult = "PASS" | "FAIL" | "DEFERRED";

export type ComponentType =
  | "WHOLE_BLOOD"
  | "RED_CELLS"
  | "PLATELETS"
  | "PLASMA"
  | "CRYOPRECIPITATE";

export type CrossmatchResultStatus = "COMPATIBLE" | "INCOMPATIBLE" | "INCONCLUSIVE";

export interface DonorProfile {
  donorId: string;
  tenantId?: string;
  personCpid: string;
  bloodGroup: string;
  rhFactor?: string | null;
  status?: string | null;
  facilityId?: string | null;
  jurisdiction?: string | null;
  registeredBy?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface DonationDrive {
  driveId: string;
  tenantId?: string;
  title: string;
  description?: string | null;
  driveType: string;
  status?: string | null;
  facilityId?: string | null;
  locationRef?: string | null;
  ndilaSiteRef?: string | null;
  startAt: string;
  endAt: string;
  capacity?: number | null;
  createdBy?: string | null;
  createdAt?: string;
}

export interface BloodInventoryBalance {
  id?: number;
  bloodBankId?: string;
  inventoryItemCode?: string;
  bloodGroup?: string;
  componentType?: string;
  quantityAvailable?: number;
  quantityReserved?: number;
  updatedAt?: string;
}

export interface MadiDashboardMetrics {
  scope?: string;
  facilityId?: string;
  driveId?: string;
  donors?: number;
  activeDrives?: number;
  pendingOrders?: number;
  openOrders?: number;
  availableUnits?: number;
  totalUnits?: number;
  openHaemovigilanceCases?: number;
  title?: string;
  status?: string;
  capacity?: number;
  [key: string]: unknown;
}

export interface MadiForecastRow {
  bloodGroup?: string;
  componentType?: string;
  currentAvailable?: number;
  baselineDemand30d?: number;
  projectedDemand30d?: number;
  projectedSupply30d?: number;
  epidemiologyMultiplier?: number;
  netGap?: number;
  risk?: "LOW" | "MEDIUM" | "HIGH" | string;
}

export interface MadiThirtyDayForecast {
  horizonDays?: number;
  generatedAt?: string;
  facilityId?: string;
  epidemiologyMultiplier?: number;
  epidemiologySignals?: Array<Record<string, unknown>>;
  rows?: MadiForecastRow[];
}

export interface MadiZiboComponentPins {
  jurisdiction?: string;
  releaseTrain?: string;
  defaultJurisdiction?: string;
  components?: Record<string, {
    label?: string;
    canonical_url?: string;
    canonicalUrl?: string;
    pinned_version?: string;
    pinnedVersion?: string;
    code?: string;
  }>;
}

export interface EmergencyRedistribution {
  redistributionId?: string;
  sourceFacilityId?: string;
  targetFacilityId?: string;
  bloodGroup?: string;
  componentType?: string;
  unitsRequested?: number;
  unitsAllocated?: number;
  priority?: string;
  status?: string;
  reason?: string;
  requestedBy?: string;
  approvedBy?: string;
  requestedAt?: string;
  approvedAt?: string;
  fulfilledAt?: string;
  nhumeDeliveryId?: string;
  handoffStatus?: string;
  handoffAt?: string;
  handoffError?: string;
  nhumeDeepLinkPath?: string;
}

export interface DonorHistory {
  donations?: Array<Record<string, unknown>>;
  screenings?: Array<Record<string, unknown>>;
  deferrals?: Array<Record<string, unknown>>;
  [key: string]: unknown;
}

export interface DonorEligibility {
  eligible?: boolean;
  nextEligibleAt?: string | null;
  reason?: string | null;
  [key: string]: unknown;
}

// ============================================================================
// Request payloads
// ============================================================================

export interface RegisterDonorPayload {
  person_cpid: string;
  blood_group: string;
  rh_factor?: string;
  registered_by?: string;
}

export interface DonorScreeningPayload {
  drive_id?: string;
  result: ScreeningResult;
  notes?: string;
  screened_by?: string;
}

export interface DonorDeferralPayload {
  reason_code: string;
  deferred_until?: string;
  notes?: string;
  deferred_by?: string;
}

export interface DonorPreferencesPayload {
  channel: string;
  enabled?: boolean;
  locale?: string;
  updated_by?: string;
}

export interface DonorFeedbackPayload {
  drive_id?: string;
  rating?: number;
  comments?: string;
}

export interface CreateDrivePayload {
  title: string;
  description?: string;
  drive_type: string;
  location_ref?: string;
  ndila_site_ref?: string;
  start_at: string;
  end_at: string;
  capacity?: number;
  created_by?: string;
}

export interface DriveRegisterPayload {
  donor_id: string;
  slot_id?: string;
}

export interface DriveScreenPayload {
  donor_id: string;
  result: ScreeningResult;
  screened_by?: string;
}

export interface RecordDonationPayload {
  donor_id: string;
  bag_number: string;
  volume_ml?: number;
  collected_by?: string;
}

export interface RegisterBloodBankPayload {
  facility_id: string;
  bank_name: string;
  bank_type: string;
}

export interface StockMovementPayload {
  unit_id?: string;
  inventory_item_code: string;
  movement_type: string;
  quantity?: number;
  reference_type?: string;
  reference_id?: string;
  moved_by?: string;
}

export interface StockReconciliationPayload {
  variance_count?: number;
  notes?: string;
  reconciled_by?: string;
}

export interface WastagePayload {
  unit_id: string;
  reason_code: string;
  recorded_by?: string;
}

export interface ReturnPayload {
  unit_id: string;
  reason_code: string;
  returned_by?: string;
}

export interface CreateBloodOrderPayload {
  patient_cpid: string;
  blood_group: string;
  component_type: ComponentType | string;
  units_requested?: number;
  oros_order_ref?: string;
  ordering_provider?: string;
  items?: Array<{
    component_type: string;
    blood_group: string;
    quantity?: number;
  }>;
}

export interface CollectSamplePayload {
  sample_number: string;
  collected_by?: string;
}

export interface CrossmatchPayload {
  sample_id: string;
  unit_id: string;
  result: CrossmatchResultStatus;
  tested_by?: string;
}

export interface ReserveUnitPayload {
  unit_id: string;
  reserved_by?: string;
}

export interface IssueUnitPayload {
  unit_id: string;
  reservation_id?: string;
  issued_by?: string;
}

export interface StartTransfusionPayload {
  order_id?: string;
  issue_id?: string;
  patient_cpid: string;
  started_by?: string;
  ward_id?: string;
}

export interface TransfusionObservationPayload {
  observation_type: string;
  value_numeric?: number;
  value_text?: string;
  unit?: string;
  observed_by?: string;
}

export interface CompleteTransfusionPayload {
  outcome_status: string;
  outcome_notes?: string;
  recorded_by?: string;
}

export interface VerifyTransfusionPayload {
  verified_by?: string;
}

export interface ProcessingReceivePayload {
  unit_id: string;
}

export interface ProcessingTestPayload {
  test_results_json: string;
}

export interface ProcessingQuarantinePayload {
  reason: string;
}

export interface ProcessingReleasePayload {
  released_by?: string;
}

export interface PrepareComponentPayload {
  unit_id: string;
  component_type: ComponentType | string;
  volume_ml?: number;
  product_id?: string;
}

export interface ReportReactionPayload {
  episode_id: string;
  reaction_type: string;
  severity: string;
  description?: string;
  reported_by?: string;
}

export interface OpenHaemovigilanceCasePayload {
  reaction_id: string;
  assigned_to?: string;
}

export interface InvestigateCasePayload {
  notes?: string;
  assigned_to?: string;
}

export interface CloseCasePayload {
  notes?: string;
}

export interface DonorPreScreeningPayload {
  answers: Record<string, boolean>;
}

export interface DonorPreScreeningResult {
  screeningId?: string;
  donorId?: string;
  outcome?: string;
  safeMessage?: string;
  answersJson?: Record<string, unknown>;
  createdAt?: string;
}

export interface PreVerifyTransfusionPayload {
  patient_cpid: string;
  blood_unit_id: string;
  patient_method: string;
  patient_biometric_ref?: string;
  unit_method: string;
  unit_scan_ref?: string;
  verified_by?: string;
}

export interface BloodFridge {
  fridgeId?: string;
  fridgeCode?: string;
  temperatureC?: number;
  alarmStatus?: string;
  lastReadingAt?: string;
  iotDeviceRef?: string;
  targetTempMinC?: number;
  targetTempMaxC?: number;
  status?: string;
  bloodBankId?: string;
}

export interface BloodFridgeReading {
  readingId?: string;
  temperatureC?: number;
  alarmStatus?: string;
  recordedAt?: string;
  iotReadingRef?: string;
}

export interface BloodOrderDetail {
  order?: Record<string, unknown>;
  orosDeepLinkPath?: string | null;
  butanoServiceRequestHint?: string | null;
}

export interface NationalHaemovigilanceDashboard {
  reactionsByType?: Record<string, number>;
  reactionsBySeverity?: Record<string, number>;
  casesByStatus?: Record<string, number>;
  casesByFacility?: Record<string, number>;
  openCases?: number;
  closedCases30d?: number;
  trendIndicator?: "UP" | "DOWN" | "STABLE" | string;
}

export interface SyncConflictPayload {
  drive_id: string;
  local_op_id: string;
  local_state?: Record<string, unknown>;
  server_state?: Record<string, unknown>;
}

export interface ResolveSyncConflictPayload {
  drive_id: string;
  resolution: "KEEP_LOCAL" | "USE_SERVER" | "MERGE";
  resolved_by?: string;
}

// ============================================================================
// Client surface
// ============================================================================

const BASE = "/internal/v1/madi";

/** Unwrap BFF `{ data, meta }` envelopes from MADI proxy routes. */
export function unwrapMadiData<T>(response: unknown): T {
  if (response && typeof response === "object" && "data" in response) {
    return (response as { data: T }).data;
  }
  return response as T;
}

export const madiApi = {
  // Dashboard
  dashboard: (params?: {
    scope?: "local" | "facility" | "central" | "drive";
    facility_id?: string;
    drive_id?: string;
  }) => {
    const query = new URLSearchParams();
    if (params?.scope) query.set("scope", params.scope);
    if (params?.facility_id) query.set("facility_id", params.facility_id);
    if (params?.drive_id) query.set("drive_id", params.drive_id);
    const qs = query.toString();
    return apiClient.get<MadiDashboardMetrics>(`${BASE}/dashboard${qs ? `?${qs}` : ""}`);
  },

  centralBankMetrics: () =>
    apiClient.get<MadiDashboardMetrics>(`${BASE}/central-bank/metrics`),

  dashboardForecast: async (facilityId?: string) =>
    unwrapMadiData<MadiThirtyDayForecast>(
      await apiClient.get(
        `${BASE}/dashboard/forecast${facilityId ? `?facility_id=${encodeURIComponent(facilityId)}` : ""}`,
      ),
    ),

  listEmergencyRedistributions: async () =>
    unwrapMadiData<EmergencyRedistribution[]>(
      await apiClient.get(`${BASE}/central-bank/emergency-redistributions`),
    ),

  requestEmergencyRedistribution: async (body: {
    source_facility_id: string;
    target_facility_id: string;
    blood_group: string;
    component_type: string;
    units_requested: number;
    reason?: string;
    requested_by?: string;
    priority?: string;
  }) =>
    unwrapMadiData<EmergencyRedistribution>(
      await apiClient.post(`${BASE}/central-bank/emergency-redistributions`, body),
    ),

  approveEmergencyRedistribution: async (
    redistributionId: string,
    body: { approved_by: string; units_allocated?: number; status?: string },
  ) =>
    unwrapMadiData<EmergencyRedistribution>(
      await apiClient.post(
        `${BASE}/central-bank/emergency-redistributions/${encodeURIComponent(redistributionId)}/approve`,
        body,
      ),
    ),

  initiateEmergencyHandoff: async (redistributionId: string, body?: { initiated_by?: string }) =>
    unwrapMadiData<EmergencyRedistribution>(
      await apiClient.post(
        `${BASE}/central-bank/emergency-redistributions/${encodeURIComponent(redistributionId)}/handoff`,
        body ?? {},
      ),
    ),

  componentTerminologyPins: async (jurisdiction = "ZW") =>
    unwrapMadiData<MadiZiboComponentPins>(
      await apiClient.get(`${BASE}/terminology/component-pins?jurisdiction=${encodeURIComponent(jurisdiction)}`),
    ),

  // Donors
  registerDonor: (body: RegisterDonorPayload) =>
    apiClient.post<DonorProfile>(`${BASE}/donors`, body),

  donorByPerson: (personCpid: string) =>
    apiClient.get<DonorProfile>(
      `${BASE}/donors/by-person?person_cpid=${encodeURIComponent(personCpid)}`,
    ),

  screenDonor: (donorId: string, body: DonorScreeningPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/donors/${donorId}/screenings`, body),

  deferDonor: (donorId: string, body: DonorDeferralPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/donors/${donorId}/deferrals`, body),

  updateDonorPreferences: (donorId: string, body: DonorPreferencesPayload) =>
    apiClient.put<Record<string, unknown>>(`${BASE}/donors/${donorId}/preferences`, body),

  submitDonorFeedback: (donorId: string, body: DonorFeedbackPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/donors/${donorId}/feedback`, body),

  donorHistory: (donorId: string) =>
    apiClient.get<DonorHistory>(`${BASE}/donors/${donorId}/history`),

  donorNextEligibility: (donorId: string) =>
    apiClient.get<DonorEligibility>(`${BASE}/donors/${donorId}/next-eligibility`),

  submitDonorPreScreening: async (donorId: string, body: DonorPreScreeningPayload) =>
    unwrapMadiData<DonorPreScreeningResult>(
      await apiClient.post(`${BASE}/donors/${donorId}/pre-screening`, body),
    ),

  latestDonorPreScreening: async (donorId: string) =>
    unwrapMadiData<DonorPreScreeningResult | null>(
      await apiClient.get(`${BASE}/donors/${donorId}/pre-screening/latest`),
    ),

  drivesNearMe: (ndilaSiteRef: string, radiusKm = 25) =>
    apiClient.get<Array<Record<string, unknown>>>(
      `${BASE}/donors/drives-near-me?ndila_site_ref=${encodeURIComponent(ndilaSiteRef)}&radius_km=${radiusKm}`,
    ),

  // Donation drives
  listDrives: () => apiClient.get<DonationDrive[]>(`${BASE}/drives`),

  createDrive: (body: CreateDrivePayload) =>
    apiClient.post<DonationDrive>(`${BASE}/drives`, body),

  publishDrive: (driveId: string, body?: { published_by?: string }) =>
    apiClient.post<DonationDrive>(`${BASE}/drives/${driveId}/publish`, body ?? {}),

  registerForDrive: (driveId: string, body: DriveRegisterPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/drives/${driveId}/register`, body),

  screenAtDrive: (driveId: string, body: DriveScreenPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/drives/${driveId}/screen`, body),

  recordDonation: (driveId: string, body: RecordDonationPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/drives/${driveId}/donations`, body),

  closeDrive: (driveId: string) =>
    apiClient.post<DonationDrive>(`${BASE}/drives/${driveId}/close`, {}),

  // Blood banks
  registerBloodBank: (body: RegisterBloodBankPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/blood-banks`, body),

  bloodBankStock: (bloodBankId: string) =>
    apiClient.get<BloodInventoryBalance[]>(`${BASE}/blood-banks/${bloodBankId}/stock`),

  recordStockMovement: (bloodBankId: string, body: StockMovementPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/blood-banks/${bloodBankId}/movements`, body),

  reconcileStock: (bloodBankId: string, body: StockReconciliationPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/blood-banks/${bloodBankId}/reconciliations`, body),

  recordWastage: (bloodBankId: string, body: WastagePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/blood-banks/${bloodBankId}/wastage`, body),

  recordReturn: (bloodBankId: string, body: ReturnPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/blood-banks/${bloodBankId}/returns`, body),

  listBloodFridges: async (bloodBankId: string) =>
    unwrapMadiData<BloodFridge[]>(
      await apiClient.get(`${BASE}/blood-banks/fridges?blood_bank_id=${encodeURIComponent(bloodBankId)}`),
    ),

  fridgeReadings: async (fridgeId: string) =>
    unwrapMadiData<BloodFridgeReading[]>(
      await apiClient.get(`${BASE}/blood-banks/fridges/${fridgeId}/readings`),
    ),

  syncFridgeIot: async (fridgeId: string) =>
    unwrapMadiData<BloodFridge>(
      await apiClient.post(`${BASE}/blood-banks/fridges/${fridgeId}/sync-iot`, {}),
    ),

  // Blood orders
  createBloodOrder: (body: CreateBloodOrderPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/orders`, body),

  submitBloodOrder: (orderId: string) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/orders/${orderId}/submit`, {}),

  collectOrderSample: (orderId: string, body: CollectSamplePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/orders/${orderId}/samples`, body),

  crossmatchOrder: (orderId: string, body: CrossmatchPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/orders/${orderId}/crossmatch`, body),

  reserveOrderUnit: (orderId: string, body: ReserveUnitPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/orders/${orderId}/reserve`, body),

  issueOrderUnit: (orderId: string, body: IssueUnitPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/orders/${orderId}/issue`, body),

  bloodOrderDetail: async (orderId: string) =>
    unwrapMadiData<BloodOrderDetail>(await apiClient.get(`${BASE}/orders/${orderId}`)),

  // Transfusions
  startTransfusion: (body: StartTransfusionPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/transfusions`, body),

  transfusionEpisode: async (episodeId: string) =>
    unwrapMadiData<Record<string, unknown>>(await apiClient.get(`${BASE}/transfusions/${episodeId}`)),

  preVerifyTransfusion: async (episodeId: string, body: PreVerifyTransfusionPayload) =>
    unwrapMadiData<Record<string, unknown>>(
      await apiClient.post(`${BASE}/transfusions/${episodeId}/pre-verify`, body),
    ),

  recordTransfusionObservation: (episodeId: string, body: TransfusionObservationPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/transfusions/${episodeId}/observations`, body),

  completeTransfusion: (episodeId: string, body: CompleteTransfusionPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/transfusions/${episodeId}/complete`, body),

  verifyTransfusion: (episodeId: string, body?: VerifyTransfusionPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/transfusions/${episodeId}/verify`, body ?? {}),

  // Processing
  receiveProcessingBatch: (body: ProcessingReceivePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/processing/receive`, body),

  recordProcessingTest: (batchId: string, body: ProcessingTestPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/processing/${batchId}/test`, body),

  quarantineProcessingBatch: (batchId: string, body: ProcessingQuarantinePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/processing/${batchId}/quarantine`, body),

  releaseProcessingBatch: (batchId: string, body?: ProcessingReleasePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/processing/${batchId}/release`, body ?? {}),

  prepareComponent: (body: PrepareComponentPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/processing/components`, body),

  // Haemovigilance
  reportReaction: (body: ReportReactionPayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/haemovigilance/reactions`, body),

  openHaemovigilanceCase: (body: OpenHaemovigilanceCasePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/haemovigilance/cases`, body),

  investigateCase: (caseId: string, body?: InvestigateCasePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/haemovigilance/cases/${caseId}/investigate`, body ?? {}),

  closeHaemovigilanceCase: (caseId: string, body?: CloseCasePayload) =>
    apiClient.post<Record<string, unknown>>(`${BASE}/haemovigilance/cases/${caseId}/close`, body ?? {}),

  nationalHaemovigilanceDashboard: async () =>
    unwrapMadiData<NationalHaemovigilanceDashboard>(
      await apiClient.get(`${BASE}/haemovigilance/national-dashboard`),
    ),

  recordDriveSyncConflict: async (body: SyncConflictPayload) =>
    unwrapMadiData<Record<string, unknown>>(
      await apiClient.post(`${BASE}/drives/sync-conflicts`, body),
    ),

  resolveDriveSyncConflict: async (conflictId: string, body: ResolveSyncConflictPayload) =>
    unwrapMadiData<Record<string, unknown>>(
      await apiClient.post(`${BASE}/drives/sync-conflicts/${conflictId}/resolve`, body),
    ),
};
