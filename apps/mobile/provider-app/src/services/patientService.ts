/**
 * Patient Service — API client for patient-related BFF endpoints.
 *
 * Backend: experience-bff /internal/v1/patients/*
 *          experience-bff /internal/v1/mobile/provider/search/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import { fetchPage, type PaginationParams } from "@impilo/mobile-api-client";
import type { Patient } from "../types";

interface PatientListResponse {
  data: PatientResource[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

interface PatientResource {
  id: string;
  type: "Patient";
  attributes: {
    cpid: string;
    given_name: string;
    family_name: string;
    date_of_birth: string;
    sex: string;
    national_id: string;
    phone: string;
    status: string;
    facility_id: string;
    created_at: string;
    updated_at: string;
  };
}

function mapPatient(r: PatientResource): Patient {
  return {
    id: r.id,
    cpid: r.attributes.cpid,
    givenName: r.attributes.given_name,
    familyName: r.attributes.family_name,
    dateOfBirth: r.attributes.date_of_birth,
    sex: r.attributes.sex as Patient["sex"],
    nationalId: r.attributes.national_id,
    phone: r.attributes.phone,
    status: r.attributes.status,
    facilityId: r.attributes.facility_id,
    createdAt: r.attributes.created_at,
    updatedAt: r.attributes.updated_at,
  };
}

export async function searchPatients(
  query: string,
  pagination?: PaginationParams
): Promise<{ patients: Patient[]; totalElements: number; totalPages: number }> {
  const params = new URLSearchParams();
  if (query) params.set("search", query);
  if (pagination) {
    params.set("page", String(pagination.page ?? 0));
    params.set("size", String(pagination.size ?? 20));
  }
  const response = await apiClient.get<PatientListResponse>(
    `/internal/v1/patients?${params.toString()}`
  );
  return {
    patients: response.data.data.map(mapPatient),
    totalElements: response.data.meta.page.total_elements,
    totalPages: response.data.meta.page.total_pages,
  };
}

export async function getPatient(id: string): Promise<Patient> {
  const response = await apiClient.get<{ data: PatientResource }>(
    `/internal/v1/patients/${id}`
  );
  return mapPatient(response.data.data);
}

export async function searchPatientsByNationalId(nationalId: string): Promise<Patient[]> {
  const params = new URLSearchParams({ search: nationalId });
  const response = await apiClient.get<PatientListResponse>(
    `/internal/v1/patients?${params.toString()}`
  );
  return response.data.data.map(mapPatient);
}

export async function searchPatientsByQR(qrData: string): Promise<Patient> {
  const response = await apiClient.post<{ data: PatientResource }>(
    "/internal/v1/mobile/provider/search/qr",
    { qr_data: qrData }
  );
  return mapPatient(response.data.data);
}

export async function getPatientsByFacility(
  facilityId: string,
  pagination?: PaginationParams
): Promise<{ patients: Patient[]; totalElements: number; totalPages: number }> {
  const page = pagination?.page ?? 0;
  const size = pagination?.size ?? 20;
  const response = await fetchPage<PatientListResponse>(
    `/internal/v1/patients?facility_id=${facilityId}`,
    { page, size }
  );
  return {
    patients: response.data.data.map(mapPatient),
    totalElements: response.data.meta.page.total_elements,
    totalPages: response.data.meta.page.total_pages,
  };
}
