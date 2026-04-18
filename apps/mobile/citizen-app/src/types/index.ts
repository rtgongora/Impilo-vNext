/**
 * Citizen App Types — Domain models for citizen-facing features.
 */

export type CitizenTab = "home" | "personal" | "social" | "marketplace" | "messaging" | "telehealth";

export interface CitizenProfile {
  cpid: string;
  givenName: string;
  familyName: string;
  dateOfBirth: string;
  sex: string;
  nationalId?: string;
  phone?: string;
  email?: string;
  avatarUrl?: string;
  preferredLanguage: string;
  facilityId?: string;
  facilityName?: string;
}

export interface Appointment {
  id: string;
  facilityId: string;
  facilityName: string;
  providerId?: string;
  providerName?: string;
  appointmentType: string;
  status: AppointmentStatus;
  scheduledAt: string;
  duration: number;
  reason?: string;
  notes?: string;
  createdAt: string;
}

export type AppointmentStatus =
  | "SCHEDULED"
  | "CONFIRMED"
  | "CHECKED_IN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "NO_SHOW";

export interface Prescription {
  id: string;
  medicationName: string;
  dosage: string;
  frequency: string;
  duration: string;
  quantity: number;
  status: PrescriptionStatus;
  prescribedBy: string;
  facilityName: string;
  refillsRemaining: number;
  lastFilledAt?: string;
  createdAt: string;
}

export type PrescriptionStatus = "ACTIVE" | "PENDING" | "DISPENSED" | "EXPIRED" | "CANCELLED";

export interface LabResult {
  id: string;
  testName: string;
  category: string;
  status: LabResultStatus;
  value?: string;
  unit?: string;
  referenceRange?: string;
  interpretation?: string;
  orderedBy: string;
  facilityName: string;
  collectedAt?: string;
  resultAt?: string;
  createdAt: string;
}

export type LabResultStatus = "ORDERED" | "COLLECTED" | "PROCESSING" | "COMPLETED" | "CANCELLED";

export interface CoverageInfo {
  id: string;
  planName: string;
  planType: string;
  memberId: string;
  /**
   * Back-compat alias used by older tests/screens.
   */
  memberNumber?: string;
  status: string;
  effectiveFrom: string;
  effectiveTo?: string;
  copay?: number;
  deductible?: number;
  outOfPocketMax?: number;
  currency: string;
}

export interface ConsentPreference {
  id: string;
  category: string;
  description: string;
  granted: boolean;
  grantedAt?: string;
  revokedAt?: string;
}

export interface FeedItem {
  id: string;
  type: FeedItemType;
  title: string;
  body: string;
  imageUrl?: string;
  author?: string;
  category: string;
  publishedAt: string;
  likesCount: number;
  commentsCount: number;
  liked: boolean;
  actionUrl?: string;
}

export type FeedItemType = "ANNOUNCEMENT" | "HEALTH_TIP" | "CAMPAIGN" | "REMINDER" | "COMMUNITY";

export interface MarketplaceService {
  id: string;
  name: string;
  description: string;
  category: string;
  facilityId: string;
  facilityName: string;
  price?: number;
  currency: string;
  available: boolean;
  rating?: number;
  imageUrl?: string;
}

export interface ServiceRequest {
  id: string;
  serviceId: string;
  serviceName: string;
  facilityName: string;
  status: ServiceRequestStatus;
  requestedAt: string;
  scheduledAt?: string;
  completedAt?: string;
  notes?: string;
  trackingNumber?: string;
}

export type ServiceRequestStatus =
  | "PENDING"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export interface TelehealthSession {
  id: string;
  providerId: string;
  providerName: string;
  sessionType: string;
  status: TelehealthStatus;
  scheduledAt: string;
  startedAt?: string;
  endedAt?: string;
  roomUrl?: string;
  notes?: string;
}

export type TelehealthStatus =
  | "REQUESTED"
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

/* ── Medical Records ── */

export type RecordType = "LAB_REPORT" | "CLINICAL_NOTE" | "DISCHARGE_SUMMARY" | "IMAGING";

export interface MedicalRecord {
  id: string;
  title: string;
  type: RecordType;
  date: string;
  provider: string;
  facilityName: string;
  summary?: string;
  documentUrl?: string;
  createdAt: string;
}

/* ── Reminders ── */

export type ReminderType = "MEDICATION" | "APPOINTMENT" | "LAB_CHECK" | "FOLLOW_UP";
export type Recurrence = "ONCE" | "DAILY" | "WEEKLY";

export interface Reminder {
  id: string;
  title: string;
  type: ReminderType;
  description?: string;
  dateTime: string;
  recurrence: Recurrence;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

/* ── Health Timeline ── */

export type TimelineEntryType =
  | "ENCOUNTER"
  | "LAB_RESULT"
  | "PRESCRIPTION"
  | "APPOINTMENT"
  | "IMMUNIZATION";

export interface TimelineEntry {
  id: string;
  type: TimelineEntryType;
  title: string;
  description?: string;
  date: string;
  facilityName?: string;
  provider?: string;
  metadata?: Record<string, unknown>;
}

/* ── Health ID ── */

export interface HealthId {
  id: string;
  healthIdNumber: string;
  status: string;
  issuedAt: string;
  expiresAt?: string;
  qrCodeData?: string;
  photoUrl?: string;
  bloodType?: string;
  allergiesSummary?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
}

/* ── Wellness ── */

export interface WellnessActivity {
  id: string;
  activityDate: string;
  steps: number;
  caloriesBurned: number;
  activeMinutes: number;
  distanceKm: number;
  sleepHours?: number;
  sleepQuality?: string;
  waterMl: number;
}

export interface WellnessVital {
  id: string;
  vitalType: string;
  value: number;
  unit: string;
  measuredAt: string;
  source: string;
  notes?: string;
}

export interface MoodEntry {
  id: string;
  moodScore: number;
  energyLevel?: number;
  stressLevel?: number;
  notes?: string;
  loggedAt: string;
}

export interface WellnessChallenge {
  id: string;
  title: string;
  description?: string;
  challengeType: string;
  targetValue: number;
  targetUnit: string;
  startDate: string;
  endDate: string;
  participantCount: number;
  status: string;
}

/* ── Health Wallet ── */

export interface HealthWallet {
  id: string;
  balance: number;
  currency: string;
  status: string;
}

export interface WalletTransaction {
  id: string;
  transactionType: string;
  amount: number;
  currency: string;
  description?: string;
  reference?: string;
  status: string;
  createdAt: string;
}

/* ── Emergency SOS ── */

export interface EmergencyContact {
  id: string;
  name: string;
  relationship: string;
  phone: string;
  isPrimary: boolean;
}

/* ── Remote Monitoring ── */

export interface MonitoringDevice {
  id: string;
  deviceName: string;
  deviceType: string;
  manufacturer?: string;
  model?: string;
  connectionType: string;
  status: string;
  lastSyncAt?: string;
  batteryLevel?: number;
}

/* ── Queue Status ── */

export interface QueueTicket {
  id: string;
  facilityName: string;
  ticketNumber: string;
  serviceType: string;
  status: string;
  position?: number;
  estimatedWait?: number;
  joinedAt: string;
  calledAt?: string;
}

/* ── Clubs ── */

export interface WellnessClub {
  id: string;
  name: string;
  description?: string;
  clubType: string;
  category: string;
  memberCount: number;
  maxMembers?: number;
  isPublic: boolean;
  meetingSchedule?: string;
  location?: string;
  imageUrl?: string;
  status: string;
}

/* ── Professional Pages ── */

export interface ProfessionalPage {
  id: string;
  displayName: string;
  title?: string;
  specialty?: string;
  bio?: string;
  photoUrl?: string;
  facilityName?: string;
  yearsExperience?: number;
  rating?: number;
  reviewCount: number;
  isAccepting: boolean;
  consultationFee?: number;
  currency?: string;
}

/* ── Crowdfunding ── */

export interface CrowdfundingCampaign {
  id: string;
  title: string;
  story: string;
  category: string;
  goalAmount: number;
  raisedAmount: number;
  currency: string;
  donorCount: number;
  imageUrl?: string;
  status: string;
  verified: boolean;
  endsAt?: string;
}

/* ── Finance ── */

export interface Transaction {
  id: string;
  date: string;
  description: string;
  amount: number;
  currency: string;
  type: "CREDIT" | "DEBIT";
  status: string;
  reference?: string;
  category?: string;
}

export interface Balance {
  amount: number;
  currency: string;
  updatedAt: string;
}

export interface PendingCharge {
  id: string;
  description: string;
  amount: number;
  currency: string;
  facilityName?: string;
  chargeDate: string;
  status: string;
}

/* ── Wellness Goals ── */

export type GoalPriority = "LOW" | "MEDIUM" | "HIGH";

export interface WellnessGoal {
  id: string;
  title: string;
  description?: string;
  targetValue: number;
  currentValue: number;
  unit?: string;
  priority: GoalPriority;
  targetDate: string;
  status: string;
  createdAt: string;
}

/* ── Wellness Programs ── */

export interface WellnessProgram {
  id: string;
  name: string;
  description: string;
  duration: string;
  category: string;
  imageUrl?: string;
  status: string;
}

export interface Enrollment {
  id: string;
  programId: string;
  programName: string;
  progress: number;
  enrolledAt: string;
  status: string;
}

/* ── Marketplace Cart ── */

export interface CartItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  currency: string;
  imageUrl?: string;
}

export interface Cart {
  id: string;
  items: CartItem[];
  totalItems: number;
  totalAmount: number;
  currency: string;
}

/* ── Allergies ── */

export interface Allergy {
  id: string;
  allergen: string;
  reaction: string;
  severity: AllergySeverity;
  onsetDate?: string;
  recordedAt: string;
  recordedBy?: string;
  facilityName?: string;
}

export type AllergySeverity = "MILD" | "MODERATE" | "SEVERE" | "LIFE_THREATENING";

/* ── Conditions ── */

export interface Condition {
  id: string;
  name: string;
  code?: string;
  status: ConditionStatus;
  onsetDate?: string;
  recordedAt: string;
  resolvedAt?: string;
  recordedBy?: string;
  facilityName?: string;
  notes?: string;
}

export type ConditionStatus = "ACTIVE" | "RESOLVED" | "INACTIVE" | "REMISSION";

/* ── Immunizations ── */

export interface Immunization {
  id: string;
  vaccineName: string;
  vaccineCode?: string;
  doseNumber: number;
  totalDoses?: number;
  administeredAt: string;
  administeredBy?: string;
  facilityName?: string;
  lotNumber?: string;
  expirationDate?: string;
  nextDoseDate?: string;
  notes?: string;
}

/* ── Referrals ── */

export interface Referral {
  id: string;
  fromFacilityId: string;
  fromFacilityName: string;
  toFacilityId?: string;
  toFacilityName?: string;
  toProviderId?: string;
  toProviderName?: string;
  specialty?: string;
  reason: string;
  status: ReferralStatus;
  requestedAt: string;
  acceptedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  notes?: string;
}

export type ReferralStatus = "PENDING" | "ACCEPTED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";

/* ── Care Plans ── */

export interface CarePlan {
  id: string;
  name: string;
  description?: string;
  status: CarePlanStatus;
  startDate: string;
  endDate?: string;
  createdAt: string;
  updatedAt?: string;
  createdBy?: string;
  goals: CarePlanGoal[];
  interventions: CarePlanIntervention[];
}

export type CarePlanStatus = "DRAFT" | "ACTIVE" | "ON_HOLD" | "COMPLETED" | "CANCELLED";

export interface CarePlanGoal {
  id: string;
  description: string;
  targetDate?: string;
  achievedAt?: string;
  status: "PENDING" | "IN_PROGRESS" | "ACHIEVED" | "NOT_ACHIEVED";
}

export interface CarePlanIntervention {
  id: string;
  description: string;
  type: string;
  frequency?: string;
  startDate?: string;
  endDate?: string;
  status: "PENDING" | "ACTIVE" | "COMPLETED" | "SKIPPED";
}

/* ── Provider Discovery ── */

export interface Provider {
  id: string;
  displayName: string;
  title?: string;
  specialty?: string;
  facilityId?: string;
  facilityName?: string;
  photoUrl?: string;
  rating?: number;
  reviewCount: number;
  yearsExperience?: number;
  isAccepting: boolean;
  consultationFee?: number;
  currency?: string;
  languages?: string[];
  nextAvailable?: string;
}

/* ── Care Team ── */

export interface CareTeamMember {
  id: string;
  role: string;
  name: string;
  specialty?: string;
  facilityName?: string;
  phone?: string;
  email?: string;
  photoUrl?: string;
  isPrimary: boolean;
}

/* ── ID Recovery ── */

export interface IdRecoveryRequest {
  id: string;
  status: "PENDING" | "VERIFIED" | "COMPLETED" | "EXPIRED" | "FAILED";
  method: "SMS" | "EMAIL" | "VIDEO_CALL" | "IN_PERSON";
  requestedAt: string;
  verifiedAt?: string;
  completedAt?: string;
  expiresAt: string;
  deliveryChannel?: string;
}

/* ── Delegation ── */

export interface Delegate {
  id: string;
  delegateId: string;
  delegateName: string;
  delegateRelationship?: string;
  permissions: DelegatePermission[];
  grantedAt: string;
  expiresAt?: string;
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
}

export type DelegatePermission =
  | "VIEW_RECORDS"
  | "VIEW_APPOINTMENTS"
  | "MANAGE_APPOINTMENTS"
  | "VIEW_PRESCRIPTIONS"
  | "RECEIVE_NOTIFICATIONS"
  | "ACCESS_TELEHEALTH";

/* ── Assessments ── */

export interface Assessment {
  id: string;
  name: string;
  description?: string;
  category: string;
  status: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";
  startedAt?: string;
  completedAt?: string;
  score?: number;
  maxScore?: number;
  resultSummary?: string;
}

export interface AssessmentResponse {
  id: string;
  assessmentId: string;
  questionId: string;
  answer: string;
  responseAt: string;
}

/* ── Growth Charts ── */

export interface GrowthMeasurement {
  id: string;
  date: string;
  ageMonths: number;
  weightKg?: number;
  heightCm?: number;
  headCircumferenceCm?: number;
  bmi?: number;
  percentile?: number;
  growthChartType: "WEIGHT_FOR_AGE" | "HEIGHT_FOR_AGE" | "BMI_FOR_AGE" | "HEAD_CIRCUMFERENCE";
}

export interface GrowthChart {
  id: string;
  childId: string;
  chartType: GrowthMeasurement["growthChartType"];
  measurements: GrowthMeasurement[];
  standardDeviations: number[];
}

/* ── Maternity ── */

export interface MaternityRecord {
  id: string;
  status: "PREGNANT" | "POSTPARTUM";
  dueDate?: string;
  calculatedDueDate?: string;
  conceptionDate?: string;
  lastMenstrualPeriod?: string;
  riskLevel?: "LOW" | "MODERATE" | "HIGH";
  prenatalVisits: number;
  currentWeek?: number;
  currentTrimester?: number;
  createdAt: string;
}

export interface PrenatalVisit {
  id: string;
  maternityRecordId: string;
  visitDate: string;
  gestationalWeek: number;
  providerName?: string;
  facilityName?: string;
  systolicBp?: number;
  diastolicBp?: number;
  weightKg?: number;
  fundalHeightCm?: number;
  fetalHeartRate?: number;
  notes?: string;
}

/* ── Wellness Coaching ── */

export interface WellnessCoach {
  id: string;
  displayName: string;
  specialty: string;
  bio?: string;
  photoUrl?: string;
  rating?: number;
  sessionCount: number;
  languages?: string[];
  availableSlots?: string[];
}

export interface CoachingSession {
  id: string;
  coachId: string;
  coachName: string;
  patientId: string;
  scheduledAt: string;
  durationMinutes: number;
  status: "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  topic?: string;
  notes?: string;
}

export interface CoachingGoal {
  id: string;
  coachId: string;
  patientId: string;
  title: string;
  description?: string;
  targetDate: string;
  progress: number;
  status: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "ABANDONED";
  createdAt: string;
}
