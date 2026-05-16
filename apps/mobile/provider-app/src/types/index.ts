/**
 * Provider App — Domain Types
 *
 * All domain types used across the Provider App modes.
 * Maps directly to BFF API response shapes.
 */

export type AppMode = "provider" | "outreach" | "supervisor" | "offline" | "courier";
export type ProviderTabKey =
  | "dashboard"
  | "patients"
  | "encounter"
  | "results"
  | "queue"
  | "messaging"
  | "tools"
  | "apps"
  | "professional";

export type EncounterStatus = "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
export type TaskStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "ESCALATED" | "OVERDUE";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type VitalType = "BLOOD_PRESSURE" | "HEART_RATE" | "TEMPERATURE" | "RESPIRATORY_RATE" | "OXYGEN_SATURATION" | "WEIGHT" | "HEIGHT" | "BMI" | "BLOOD_GLUCOSE";
export type ReferralStatus = "PENDING" | "ACCEPTED" | "COMPLETED" | "REJECTED";
export type LabOrderStatus = "ORDERED" | "COLLECTED" | "PROCESSING" | "COMPLETED" | "CANCELLED";
export type PrescriptionStatus = "ACTIVE" | "DISPENSED" | "CANCELLED" | "EXPIRED";
export type HouseholdVisitStatus = "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "MISSED";
export type ScreeningStatus = "PENDING" | "COMPLETED" | "REFERRED";
export type TicketStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
export type StockLevel = "ADEQUATE" | "LOW" | "CRITICAL" | "OUT_OF_STOCK";
export type SyncItemStatus = "PENDING" | "SYNCING" | "SYNCED" | "FAILED" | "CONFLICT";

export interface Patient {
  id: string;
  cpid: string;
  givenName: string;
  familyName: string;
  dateOfBirth: string;
  sex: "M" | "F" | "O";
  nationalId: string;
  phone: string;
  status: string;
  facilityId: string;
  createdAt: string;
  updatedAt: string;
}

export interface Encounter {
  id: string;
  patientId: string;
  facilityId: string;
  shiftId?: string;
  encounterType: string;
  status: EncounterStatus;
  chiefComplaint?: string;
  diagnosis?: string;
  notes?: string;
  vitals?: VitalReading[];
  startedAt: string;
  endedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface VitalReading {
  id: string;
  encounterId: string;
  type: VitalType;
  value: number;
  unit: string;
  measuredAt: string;
  measuredBy: string;
}

export interface Diagnosis {
  id: string;
  encounterId: string;
  icdCode: string;
  icdDescription: string;
  isPrimary: boolean;
  notes?: string;
  diagnosedAt: string;
}

export interface Prescription {
  id: string;
  encounterId: string;
  patientId: string;
  medication: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  instructions?: string;
  status: PrescriptionStatus;
  prescribedAt: string;
  prescribedBy: string;
}

export interface LabOrder {
  id: string;
  encounterId: string;
  patientId: string;
  testName: string;
  testCode: string;
  urgency: "ROUTINE" | "URGENT" | "STAT";
  status: LabOrderStatus;
  results?: LabResult[];
  orderedAt: string;
  orderedBy: string;
}

export interface LabResult {
  id: string;
  labOrderId: string;
  parameter: string;
  value: string;
  unit: string;
  referenceRange: string;
  isAbnormal: boolean;
  completedAt: string;
}

export interface Referral {
  id: string;
  encounterId: string;
  patientId: string;
  fromFacilityId: string;
  toFacilityId: string;
  toFacilityName: string;
  specialty: string;
  reason: string;
  urgency: "ROUTINE" | "URGENT" | "EMERGENCY";
  status: ReferralStatus;
  notes?: string;
  createdAt: string;
}

export interface Task {
  id: string;
  title: string;
  description?: string;
  assigneeId: string;
  assigneeName: string;
  patientId?: string;
  patientName?: string;
  facilityId: string;
  taskType: string;
  priority: TaskPriority;
  status: TaskStatus;
  dueAt?: string;
  completedAt?: string;
  escalatedTo?: string;
  createdAt: string;
}

export interface Household {
  id: string;
  headOfHousehold: string;
  members: HouseholdMember[];
  address: string;
  gpsLatitude: number;
  gpsLongitude: number;
  facilityId: string;
  lastVisitDate?: string;
  nextVisitDate?: string;
  riskCategory: "LOW" | "MEDIUM" | "HIGH";
  createdAt: string;
}

export interface HouseholdMember {
  id: string;
  patientId: string;
  name: string;
  relationship: string;
  dateOfBirth: string;
  sex: "M" | "F" | "O";
}

export interface CommunityVisit {
  id: string;
  householdId: string;
  visitDate: string;
  visitType: "SCREENING" | "FOLLOW_UP" | "IMMUNIZATION" | "ANTENATAL" | "GENERAL";
  status: HouseholdVisitStatus;
  findings?: string;
  referrals?: string[];
  gpsLatitude: number;
  gpsLongitude: number;
  visitedBy: string;
  createdAt: string;
}

export interface ScreeningRecord {
  id: string;
  patientId: string;
  screeningType: string;
  status: ScreeningStatus;
  results: Record<string, unknown>;
  referralId?: string;
  screenedBy: string;
  screenedAt: string;
}

export interface ImmunizationRecord {
  id: string;
  patientId: string;
  vaccineName: string;
  vaccineCode: string;
  doseNumber: number;
  doseDate: string;
  batchNumber: string;
  site: string;
  administeredBy: string;
  nextDueDate?: string;
}

export interface TeamMember {
  id: string;
  name: string;
  role: string;
  facilityId: string;
  status: "ACTIVE" | "ON_LEAVE" | "OFFLINE";
  activeTasks: number;
  completedToday: number;
  lastActive: string;
}

export interface FacilityMetrics {
  facilityId: string;
  facilityName: string;
  date: string;
  patientsSeenToday: number;
  encountersOpen: number;
  encountersClosed: number;
  averageWaitTimeMinutes: number;
  pendingTasks: number;
  overdueEscalations: number;
  stockAlerts: number;
}

export interface StockItem {
  id: string;
  itemName: string;
  itemCode: string;
  category: string;
  currentQuantity: number;
  reorderLevel: number;
  unit: string;
  level: StockLevel;
  facilityId: string;
  lastRestockedAt?: string;
  expiryDate?: string;
}

export interface DispatchRecord {
  id: string;
  itemId: string;
  itemName: string;
  quantity: number;
  fromFacilityId: string;
  toFacilityId: string;
  toFacilityName: string;
  status: "PENDING" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED";
  dispatchedAt?: string;
  deliveredAt?: string;
  createdAt: string;
}

export interface Escalation {
  id: string;
  type: "CLINICAL" | "OPERATIONAL" | "STOCK" | "STAFFING";
  title: string;
  description: string;
  severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  status: "PENDING" | "ACKNOWLEDGED" | "RESOLVED";
  raisedBy: string;
  raisedByName: string;
  assignedTo?: string;
  assignedToName?: string;
  facilityId: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface SupportTicket {
  id: string;
  title: string;
  description: string;
  category: string;
  status: TicketStatus;
  priority: TaskPriority;
  reportedBy: string;
  assignedTo?: string;
  facilityId: string;
  createdAt: string;
  updatedAt: string;
}

export interface SyncQueueItem {
  id: string;
  operationType: "CREATE" | "UPDATE" | "DELETE";
  resourceType: string;
  resourceId: string;
  payload: Record<string, unknown>;
  status: SyncItemStatus;
  attemptCount: number;
  lastAttemptAt?: string;
  errorMessage?: string;
  createdAt: string;
}

export interface ConflictItem {
  id: string;
  resourceType: string;
  resourceId: string;
  localVersion: Record<string, unknown>;
  serverVersion: Record<string, unknown>;
  conflictFields: string[];
  detectedAt: string;
  resolvedAt?: string;
  resolution?: "LOCAL_WINS" | "SERVER_WINS" | "MANUAL_MERGE";
}

export interface EntitlementCheck {
  patientId: string;
  cpid: string;
  patientName: string;
  coverageStatus: "ACTIVE" | "EXPIRED" | "SUSPENDED" | "NOT_FOUND";
  coverageType?: string;
  validUntil?: string;
  benefitsRemaining?: Record<string, number>;
  verifiedAt: string;
  verifiedOffline: boolean;
}

export interface FormSchema {
  id: string;
  name: string;
  version: number;
  fields: FormField[];
  validationRules: Record<string, unknown>;
}

export interface FormField {
  id: string;
  name: string;
  label: string;
  type: "text" | "number" | "date" | "select" | "checkbox" | "textarea" | "vitals";
  required: boolean;
  options?: { value: string; label: string }[];
  validation?: Record<string, unknown>;
}

export interface FormSubmission {
  id: string;
  formSchemaId: string;
  encounterId: string;
  patientId: string;
  data: Record<string, unknown>;
  submittedBy: string;
  submittedAt: string;
}

export interface TelemedicineSession {
  id: string;
  encounterId: string;
  patientId: string;
  providerId: string;
  status: "SCHEDULED" | "WAITING" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  scheduledAt: string;
  startedAt?: string;
  endedAt?: string;
  sessionToken?: string;
  channelId?: string;
}

export interface ProviderMessage {
  id: string;
  conversationId: string;
  senderId: string;
  senderName: string;
  content: string;
  contentType: "TEXT" | "IMAGE" | "FILE";
  sentAt: string;
  readAt?: string;
}

/* ── Facility Admin ─────────────────────────────────────────────────── */

export interface FacilityStats {
  facilityId: string;
  facilityName: string;
  bedCount: number;
  occupancyRate: number;
  staffOnDuty: number;
  queueLength: number;
  updatedAt: string;
}

export type ShiftStatus = "ON_DUTY" | "OFF_DUTY";

export interface StaffMember {
  id: string;
  name: string;
  role: string;
  shiftStatus: ShiftStatus;
  department?: string;
  contactNumber?: string;
}

export interface AuditEntry {
  id: string;
  timestamp: string;
  actorId: string;
  actorName: string;
  action: string;
  resource: string;
  resourceId?: string;
  details?: string;
}

/* ── Reports ────────────────────────────────────────────────────────── */

export type ReportCategory = "Clinical" | "Operational" | "Financial";

export interface ReportSummary {
  id: string;
  name: string;
  category: ReportCategory;
  description?: string;
  lastRunAt?: string;
}

export interface ReportDataEntry {
  key: string;
  value: string | number;
  unit?: string;
}

export interface ReportData {
  reportId: string;
  reportName: string;
  generatedAt: string;
  entries: ReportDataEntry[];
}

/* ── Finance ────────────────────────────────────────────────────────── */

export interface RevenueSummary {
  todayRevenue: number;
  weekTotal: number;
  monthTotal: number;
  currency: string;
  updatedAt: string;
}

export type ClaimStatus = "PENDING" | "APPROVED" | "REJECTED" | "PAID";

export interface Claim {
  id: string;
  patientId: string;
  patientName: string;
  amount: number;
  currency: string;
  status: ClaimStatus;
  submittedAt: string;
  resolvedAt?: string;
}

export type PaymentStatus = "PAID" | "PENDING" | "REJECTED";

export interface Payment {
  id: string;
  patientId: string;
  patientName: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  paidAt: string;
  reference?: string;
}

/* ── Inventory (enhanced) ───────────────────────────────────────────── */

export type InventoryStatus = "OK" | "LOW" | "OUT";

export interface InventoryItem {
  id: string;
  name: string;
  category: string;
  quantity: number;
  unit: string;
  status: InventoryStatus;
  reorderLevel: number;
  facilityId: string;
  lastUpdatedAt?: string;
}

export interface StockAlert {
  id: string;
  itemId: string;
  itemName: string;
  alertType: "LOW_STOCK" | "STOCKOUT" | "EXPIRING";
  severity: "WARNING" | "CRITICAL";
  currentQuantity: number;
  reorderLevel: number;
  createdAt: string;
}

export type RequisitionUrgency = "ROUTINE" | "URGENT" | "EMERGENCY";

export interface Requisition {
  id: string;
  itemId: string;
  itemName: string;
  quantity: number;
  urgency: RequisitionUrgency;
  notes?: string;
  status: "DRAFT" | "SUBMITTED" | "APPROVED" | "FULFILLED" | "REJECTED";
  requestedBy: string;
  createdAt: string;
}
