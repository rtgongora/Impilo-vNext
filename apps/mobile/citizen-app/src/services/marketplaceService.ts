/**
 * Marketplace Service — Service discovery, booking, and order tracking.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/marketplace/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { MarketplaceService, ServiceRequest } from "../types";

const V1 = "/internal/v1/mobile/citizen/marketplace";

// ─── Development seed data (shown when backend is unavailable) ────────────────
const SEED_SERVICES: MarketplaceService[] = [
  { id: "svc-001", name: "General Practitioner Consultation", description: "Comprehensive general health assessment with an experienced GP. Includes vital signs, history taking, physical exam, and personalised treatment plan.", category: "CONSULTATION", facilityId: "fac-001", facilityName: "Parirenyatwa Group of Hospitals", price: 25, currency: "USD", available: true, rating: 4.8 },
  { id: "svc-002", name: "Specialist Cardiology Review", description: "Detailed cardiac evaluation by a board-certified cardiologist. ECG analysis, risk stratification, and management plan included.", category: "CONSULTATION", facilityId: "fac-002", facilityName: "Harare Central Hospital", price: 75, currency: "USD", available: true, rating: 4.9 },
  { id: "svc-003", name: "Paediatric Consultation", description: "Child health consultation for ages 0–18. Growth monitoring, vaccinations, and developmental milestones assessment.", category: "CONSULTATION", facilityId: "fac-003", facilityName: "Sally Mugabe Children's Hospital", price: 30, currency: "USD", available: true, rating: 4.7 },
  { id: "svc-004", name: "Mental Health Assessment", description: "Confidential psychological assessment and counselling session with a registered clinical psychologist.", category: "CONSULTATION", facilityId: "fac-011", facilityName: "ZADHR Wellness Centre", price: 60, currency: "USD", available: true, rating: 4.8 },
  { id: "svc-005", name: "Full Blood Count (FBC)", description: "Complete blood analysis including RBC, WBC, platelets, haemoglobin, and differential count. Results in 4–6 hours.", category: "LAB_TESTS", facilityId: "fac-004", facilityName: "National Blood Service Zimbabwe", price: 15, currency: "USD", available: true, rating: 4.6 },
  { id: "svc-006", name: "HIV Rapid Test & Counselling", description: "Confidential rapid HIV testing with pre- and post-test counselling from trained counsellors. Results in 20 minutes.", category: "LAB_TESTS", facilityId: "fac-001", facilityName: "Parirenyatwa Group of Hospitals", price: 10, currency: "USD", available: true, rating: 4.9 },
  { id: "svc-007", name: "Comprehensive Metabolic Panel", description: "Blood chemistry tests covering kidney function, liver enzymes, electrolytes, and glucose levels.", category: "LAB_TESTS", facilityId: "fac-005", facilityName: "Lancet Laboratories Zimbabwe", price: 35, currency: "USD", available: true, rating: 4.7 },
  { id: "svc-008", name: "Malaria Rapid Antigen Test", description: "Fast and accurate malaria antigen detection. Results available within 20 minutes. Includes clinical review.", category: "LAB_TESTS", facilityId: "fac-006", facilityName: "MOHCC Community Clinic", price: 8, currency: "USD", available: true, rating: 4.5 },
  { id: "svc-009", name: "Chronic Medication Delivery", description: "Monthly prescription refill delivered to your door. Covers hypertension, diabetes, asthma, and other chronic conditions.", category: "PHARMACY", facilityId: "fac-007", facilityName: "Clicks Pharmacy Harare", price: 5, currency: "USD", available: true, rating: 4.4 },
  { id: "svc-010", name: "Medication Review & Counselling", description: "Pharmacist-led review of your current medications for interactions, adherence optimisation, and side effect management.", category: "PHARMACY", facilityId: "fac-008", facilityName: "Goodlife Pharmacy Avondale", price: 20, currency: "USD", available: true, rating: 4.6 },
  { id: "svc-011", name: "Chest X-Ray with Report", description: "Digital chest radiograph with formal radiologist report. Turnaround time 24–48 hours. Digital copy included.", category: "IMAGING", facilityId: "fac-009", facilityName: "Avenues Clinic Radiology", price: 40, currency: "USD", available: true, rating: 4.8 },
  { id: "svc-012", name: "Abdominal Ultrasound", description: "Diagnostic ultrasound of abdominal organs including liver, kidneys, gallbladder, and spleen. Report within 24 hours.", category: "IMAGING", facilityId: "fac-009", facilityName: "Avenues Clinic Radiology", price: 55, currency: "USD", available: true, rating: 4.7 },
  { id: "svc-013", name: "Annual Wellness Screening", description: "Comprehensive annual check including BMI, blood pressure, cholesterol, diabetes screening, and lifestyle coaching.", category: "WELLNESS", facilityId: "fac-010", facilityName: "Wellness Centre Borrowdale", price: 45, currency: "USD", available: true, rating: 4.9 },
  { id: "svc-014", name: "Nutrition & Diet Consultation", description: "Personalised dietary assessment and meal planning with a registered dietitian. Follow-up plan included.", category: "WELLNESS", facilityId: "fac-010", facilityName: "Wellness Centre Borrowdale", price: 35, currency: "USD", available: true, rating: 4.7 },
  { id: "svc-015", name: "Home Nursing Visit", description: "Professional nurse visit for wound care, medication administration, or post-operative monitoring at your home.", category: "HOME_CARE", facilityId: "fac-012", facilityName: "Zimbabwe Home-Based Care Association", price: 30, currency: "USD", available: true, rating: 4.6 },
  { id: "svc-016", name: "Physiotherapy Home Session", description: "In-home physiotherapy for rehabilitation, mobility improvement, and pain management by a qualified physiotherapist.", category: "HOME_CARE", facilityId: "fac-013", facilityName: "ProPhysio Zimbabwe", price: 50, currency: "USD", available: true, rating: 4.8 },
];

const SEED_REQUESTS: ServiceRequest[] = [
  { id: "req-001", serviceId: "svc-001", serviceName: "General Practitioner Consultation", facilityName: "Parirenyatwa Group of Hospitals", status: "CONFIRMED", requestedAt: "2026-06-10T08:30:00Z", scheduledAt: "2026-06-14T10:00:00Z", trackingNumber: "ZW-2026-001234" },
  { id: "req-002", serviceId: "svc-005", serviceName: "Full Blood Count (FBC)", facilityName: "National Blood Service Zimbabwe", status: "COMPLETED", requestedAt: "2026-06-01T09:00:00Z", completedAt: "2026-06-01T14:00:00Z", trackingNumber: "ZW-2026-001100" },
  { id: "req-003", serviceId: "svc-013", serviceName: "Annual Wellness Screening", facilityName: "Wellness Centre Borrowdale", status: "PENDING", requestedAt: "2026-06-12T07:00:00Z" },
];

export type { MarketplaceService, ServiceRequest };

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchServices(params: {
  category?: string;
  search?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: MarketplaceService[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.category) query.push(`category=${encodeURIComponent(params.category)}`);
  if (params.search) query.push(`search=${encodeURIComponent(params.search)}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  try {
    const response = await apiClient.get<PagedResult<MarketplaceService>>(`${V1}/services${qs}`);
    const result = response.data;
    return {
      items: result.data,
      totalElements: result.meta.page.total_elements,
      hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
    };
  } catch {
    let items = SEED_SERVICES;
    if (params.category) items = items.filter((s) => s.category === params.category);
    if (params.search) {
      const q = params.search.toLowerCase();
      items = items.filter(
        (s) =>
          s.name.toLowerCase().includes(q) ||
          s.description.toLowerCase().includes(q) ||
          s.facilityName.toLowerCase().includes(q),
      );
    }
    return { items, totalElements: items.length, hasNext: false };
  }
}

export async function fetchServiceDetail(id: string): Promise<MarketplaceService> {
  const response = await apiClient.get<{ data: MarketplaceService }>(
    `${V1}/services/${encodeURIComponent(id)}`
  );
  return response.data.data;
}

export async function requestService(params: {
  serviceId: string;
  preferredDate?: string;
  notes?: string;
}): Promise<ServiceRequest> {
  const response = await apiClient.post<{ data: ServiceRequest }>(`${V1}/requests`, params);
  return response.data.data;
}

export async function fetchServiceRequests(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: ServiceRequest[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  try {
    const response = await apiClient.get<PagedResult<ServiceRequest>>(`${V1}/requests${qs}`);
    const result = response.data;
    return {
      items: result.data,
      totalElements: result.meta.page.total_elements,
      hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
    };
  } catch {
    const items = params.status
      ? SEED_REQUESTS.filter((r) => r.status === params.status)
      : SEED_REQUESTS;
    return { items, totalElements: items.length, hasNext: false };
  }
}

export async function fetchServiceRequest(id: string): Promise<ServiceRequest> {
  const response = await apiClient.get<{ data: ServiceRequest }>(
    `${V1}/requests/${encodeURIComponent(id)}`
  );
  return response.data.data;
}

export async function cancelServiceRequest(id: string, reason?: string): Promise<void> {
  await apiClient.post(`${V1}/requests/${encodeURIComponent(id)}/cancel`, { reason });
}
