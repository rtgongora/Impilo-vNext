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
