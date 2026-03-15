/**
 * Household Service — Outreach household registry and community visits.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/households/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type {
  Household,
  CommunityVisit,
  HouseholdVisitStatus,
  ScreeningRecord,
  ImmunizationRecord,
} from "../types";

interface HouseholdResource {
  id: string;
  type: "Household";
  attributes: {
    head_of_household: string;
    members: {
      id: string;
      patient_id: string;
      name: string;
      relationship: string;
      date_of_birth: string;
      sex: string;
    }[];
    address: string;
    gps_latitude: number;
    gps_longitude: number;
    facility_id: string;
    last_visit_date?: string;
    next_visit_date?: string;
    risk_category: string;
    created_at: string;
  };
}

function mapHousehold(r: HouseholdResource): Household {
  return {
    id: r.id,
    headOfHousehold: r.attributes.head_of_household,
    members: r.attributes.members.map((m) => ({
      id: m.id,
      patientId: m.patient_id,
      name: m.name,
      relationship: m.relationship,
      dateOfBirth: m.date_of_birth,
      sex: m.sex as "M" | "F" | "O",
    })),
    address: r.attributes.address,
    gpsLatitude: r.attributes.gps_latitude,
    gpsLongitude: r.attributes.gps_longitude,
    facilityId: r.attributes.facility_id,
    lastVisitDate: r.attributes.last_visit_date,
    nextVisitDate: r.attributes.next_visit_date,
    riskCategory: r.attributes.risk_category as Household["riskCategory"],
    createdAt: r.attributes.created_at,
  };
}

export async function getHouseholds(
  facilityId: string,
  page = 0,
  size = 20
): Promise<{ households: Household[]; totalElements: number }> {
  const params = new URLSearchParams({
    facility_id: facilityId,
    page: String(page),
    size: String(size),
  });
  const response = await apiClient.get<{
    data: HouseholdResource[];
    meta: { page: { total_elements: number } };
  }>(`/internal/v1/mobile/provider/households?${params.toString()}`);
  return {
    households: response.data.data.map(mapHousehold),
    totalElements: response.data.meta.page.total_elements,
  };
}

export async function getHousehold(id: string): Promise<Household> {
  const response = await apiClient.get<{ data: HouseholdResource }>(
    `/internal/v1/mobile/provider/households/${id}`
  );
  return mapHousehold(response.data.data);
}

export interface RegisterHouseholdInput {
  headOfHousehold: string;
  address: string;
  gpsLatitude: number;
  gpsLongitude: number;
  facilityId: string;
  members: {
    patientId: string;
    name: string;
    relationship: string;
    dateOfBirth: string;
    sex: string;
  }[];
}

export async function registerHousehold(input: RegisterHouseholdInput): Promise<Household> {
  const response = await apiClient.post<{ data: HouseholdResource }>(
    "/internal/v1/mobile/provider/households",
    {
      head_of_household: input.headOfHousehold,
      address: input.address,
      gps_latitude: input.gpsLatitude,
      gps_longitude: input.gpsLongitude,
      facility_id: input.facilityId,
      members: input.members.map((m) => ({
        patient_id: m.patientId,
        name: m.name,
        relationship: m.relationship,
        date_of_birth: m.dateOfBirth,
        sex: m.sex,
      })),
    }
  );
  return mapHousehold(response.data.data);
}

interface CommunityVisitResource {
  id: string;
  type: "CommunityVisit";
  attributes: {
    household_id: string;
    visit_date: string;
    visit_type: string;
    status: HouseholdVisitStatus;
    findings?: string;
    referrals?: string[];
    gps_latitude: number;
    gps_longitude: number;
    visited_by: string;
    created_at: string;
  };
}

function mapCommunityVisit(r: CommunityVisitResource): CommunityVisit {
  return {
    id: r.id,
    householdId: r.attributes.household_id,
    visitDate: r.attributes.visit_date,
    visitType: r.attributes.visit_type as CommunityVisit["visitType"],
    status: r.attributes.status,
    findings: r.attributes.findings,
    referrals: r.attributes.referrals,
    gpsLatitude: r.attributes.gps_latitude,
    gpsLongitude: r.attributes.gps_longitude,
    visitedBy: r.attributes.visited_by,
    createdAt: r.attributes.created_at,
  };
}

export interface RecordCommunityVisitInput {
  householdId: string;
  visitType: CommunityVisit["visitType"];
  findings?: string;
  gpsLatitude: number;
  gpsLongitude: number;
}

export async function recordCommunityVisit(input: RecordCommunityVisitInput): Promise<CommunityVisit> {
  const response = await apiClient.post<{ data: CommunityVisitResource }>(
    "/internal/v1/mobile/provider/households/visits",
    {
      household_id: input.householdId,
      visit_type: input.visitType,
      findings: input.findings,
      gps_latitude: input.gpsLatitude,
      gps_longitude: input.gpsLongitude,
    }
  );
  return mapCommunityVisit(response.data.data);
}

export async function getVisitsForHousehold(householdId: string): Promise<CommunityVisit[]> {
  const response = await apiClient.get<{ data: CommunityVisitResource[] }>(
    `/internal/v1/mobile/provider/households/${householdId}/visits`
  );
  return response.data.data.map(mapCommunityVisit);
}

export async function recordScreening(input: {
  patientId: string;
  screeningType: string;
  results: Record<string, unknown>;
}): Promise<ScreeningRecord> {
  const response = await apiClient.post<{ data: { id: string; attributes: ScreeningRecord } }>(
    "/internal/v1/mobile/provider/screenings",
    {
      patient_id: input.patientId,
      screening_type: input.screeningType,
      results: input.results,
    }
  );
  return { ...response.data.data.attributes, id: response.data.data.id };
}

export async function recordImmunization(input: {
  patientId: string;
  vaccineName: string;
  vaccineCode: string;
  doseNumber: number;
  batchNumber: string;
  site: string;
}): Promise<ImmunizationRecord> {
  const response = await apiClient.post<{ data: { id: string; attributes: ImmunizationRecord } }>(
    "/internal/v1/mobile/provider/immunizations",
    {
      patient_id: input.patientId,
      vaccine_name: input.vaccineName,
      vaccine_code: input.vaccineCode,
      dose_number: input.doseNumber,
      batch_number: input.batchNumber,
      site: input.site,
    }
  );
  return { ...response.data.data.attributes, id: response.data.data.id };
}
