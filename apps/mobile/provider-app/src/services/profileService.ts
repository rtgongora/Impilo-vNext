/**
 * Profile Service — Provider professional profile and schedule management.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/profile/*
 *                         /internal/v1/mobile/provider/schedule
 *                         /internal/v1/mobile/provider/notices
 */

import { apiClient } from "@impilo/mobile-api-client";

// ── Resource shapes (snake_case from API) ──────────────────────────

interface ProfileResource {
  id: string;
  type: "ProviderProfile";
  attributes: {
    given_name: string;
    family_name: string;
    qualification: string;
    specialty: string;
    provider_number: string;
    facility_id: string;
    facility_name: string;
    license_number: string;
    license_authority: string;
    license_expiry: string;
    registration_body: string;
    registration_number: string;
    email: string;
    phone: string;
    office_phone?: string;
    status: string;
    created_at: string;
    updated_at: string;
  };
}

interface ScheduleResource {
  id: string;
  type: "Shift";
  attributes: {
    date: string;
    start_time: string;
    end_time: string;
    facility_id: string;
    facility_name: string;
    department: string;
    shift_type: string;
    status: "ACTIVE" | "UPCOMING" | "COMPLETED" | "CANCELLED";
    notes?: string;
  };
}

interface NoticeResource {
  id: string;
  type: "Notice";
  attributes: {
    title: string;
    body: string;
    severity: "INFO" | "WARNING" | "CRITICAL";
    issued_at: string;
    expires_at?: string;
    read: boolean;
  };
}

// ── Domain types (camelCase for app) ───────────────────────────────

export interface ProviderProfile {
  id: string;
  givenName: string;
  familyName: string;
  qualification: string;
  specialty: string;
  providerNumber: string;
  facilityId: string;
  facilityName: string;
  licenseNumber: string;
  licenseAuthority: string;
  licenseExpiry: string;
  registrationBody: string;
  registrationNumber: string;
  email: string;
  phone: string;
  officePhone?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface Shift {
  id: string;
  date: string;
  startTime: string;
  endTime: string;
  facilityId: string;
  facilityName: string;
  department: string;
  shiftType: string;
  status: "ACTIVE" | "UPCOMING" | "COMPLETED" | "CANCELLED";
  notes?: string;
}

export interface Notice {
  id: string;
  title: string;
  body: string;
  severity: "INFO" | "WARNING" | "CRITICAL";
  issuedAt: string;
  expiresAt?: string;
  read: boolean;
}

export interface UpdateProfileInput {
  email?: string;
  phone?: string;
  office_phone?: string;
}

// ── Mappers ────────────────────────────────────────────────────────

function mapProfile(r: ProfileResource): ProviderProfile {
  return {
    id: r.id,
    givenName: r.attributes.given_name,
    familyName: r.attributes.family_name,
    qualification: r.attributes.qualification,
    specialty: r.attributes.specialty,
    providerNumber: r.attributes.provider_number,
    facilityId: r.attributes.facility_id,
    facilityName: r.attributes.facility_name,
    licenseNumber: r.attributes.license_number,
    licenseAuthority: r.attributes.license_authority,
    licenseExpiry: r.attributes.license_expiry,
    registrationBody: r.attributes.registration_body,
    registrationNumber: r.attributes.registration_number,
    email: r.attributes.email,
    phone: r.attributes.phone,
    officePhone: r.attributes.office_phone,
    status: r.attributes.status,
    createdAt: r.attributes.created_at,
    updatedAt: r.attributes.updated_at,
  };
}

function mapShift(r: ScheduleResource): Shift {
  return {
    id: r.id,
    date: r.attributes.date,
    startTime: r.attributes.start_time,
    endTime: r.attributes.end_time,
    facilityId: r.attributes.facility_id,
    facilityName: r.attributes.facility_name,
    department: r.attributes.department,
    shiftType: r.attributes.shift_type,
    status: r.attributes.status,
    notes: r.attributes.notes,
  };
}

function mapNotice(r: NoticeResource): Notice {
  return {
    id: r.id,
    title: r.attributes.title,
    body: r.attributes.body,
    severity: r.attributes.severity,
    issuedAt: r.attributes.issued_at,
    expiresAt: r.attributes.expires_at,
    read: r.attributes.read,
  };
}

// ── API functions ──────────────────────────────────────────────────

export async function getProfile(): Promise<ProviderProfile> {
  const response = await apiClient.get<{ data: ProfileResource }>(
    "/internal/v1/mobile/provider/profile"
  );
  return mapProfile(response.data.data);
}

export async function updateProfile(
  data: UpdateProfileInput
): Promise<ProviderProfile> {
  const response = await apiClient.patch<{ data: ProfileResource }>(
    "/internal/v1/mobile/provider/profile",
    data
  );
  return mapProfile(response.data.data);
}

export async function getSchedule(): Promise<Shift[]> {
  const response = await apiClient.get<{ data: ScheduleResource[] }>(
    "/internal/v1/mobile/provider/schedule"
  );
  return response.data.data.map(mapShift);
}

export async function getNotices(): Promise<Notice[]> {
  const response = await apiClient.get<{ data: NoticeResource[] }>(
    "/internal/v1/mobile/provider/notices"
  );
  return response.data.data.map(mapNotice);
}
