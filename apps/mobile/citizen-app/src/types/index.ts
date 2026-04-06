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
