/**
 * Facility Admin Service — Stats, staff roster, and audit log.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/facility/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { FacilityStats, StaffMember, AuditEntry } from "../types";

const V1 = "/internal/v1/mobile/provider/facility";

/* ── Facility Stats ─────────────────────────────────────────────────── */

interface FacilityStatsResource {
  facility_id: string;
  facility_name: string;
  bed_count: number;
  occupancy_rate: number;
  staff_on_duty: number;
  queue_length: number;
  updated_at: string;
}

function mapFacilityStats(r: FacilityStatsResource): FacilityStats {
  return {
    facilityId: r.facility_id,
    facilityName: r.facility_name,
    bedCount: r.bed_count,
    occupancyRate: r.occupancy_rate,
    staffOnDuty: r.staff_on_duty,
    queueLength: r.queue_length,
    updatedAt: r.updated_at,
  };
}

export async function fetchFacilityStats(): Promise<FacilityStats> {
  const response = await apiClient.get<{ data: FacilityStatsResource }>(
    `${V1}/stats`
  );
  return mapFacilityStats(response.data.data);
}

/* ── Staff Roster ───────────────────────────────────────────────────── */

interface StaffResource {
  id: string;
  name: string;
  role: string;
  shift_status: string;
  department?: string;
  contact_number?: string;
}

function mapStaffMember(r: StaffResource): StaffMember {
  return {
    id: r.id,
    name: r.name,
    role: r.role,
    shiftStatus: r.shift_status as StaffMember["shiftStatus"],
    department: r.department,
    contactNumber: r.contact_number,
  };
}

export async function fetchStaffRoster(): Promise<StaffMember[]> {
  const response = await apiClient.get<{ data: StaffResource[] }>(
    `${V1}/staff`
  );
  return response.data.data.map(mapStaffMember);
}

/* ── Audit Log ──────────────────────────────────────────────────────── */

interface AuditResource {
  id: string;
  timestamp: string;
  actor_id: string;
  actor_name: string;
  action: string;
  resource: string;
  resource_id?: string;
  details?: string;
}

function mapAuditEntry(r: AuditResource): AuditEntry {
  return {
    id: r.id,
    timestamp: r.timestamp,
    actorId: r.actor_id,
    actorName: r.actor_name,
    action: r.action,
    resource: r.resource,
    resourceId: r.resource_id,
    details: r.details,
  };
}

export async function fetchAuditLog(params?: {
  limit?: number;
}): Promise<AuditEntry[]> {
  const qs = params?.limit ? `?limit=${params.limit}` : "?limit=50";
  const response = await apiClient.get<{ data: AuditResource[] }>(
    `${V1}/audit${qs}`
  );
  return response.data.data.map(mapAuditEntry);
}
