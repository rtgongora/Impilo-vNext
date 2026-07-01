/**
 * Budget Service — Provider/finance mobile slice for Intelligent Budgeting.
 *
 * Backend: experience-bff ManagedBudgetBffController → COSTA (system of record)
 *   GET  /internal/v1/finance/budgets/managed?year=&facility_id=
 *   GET  /internal/v1/finance/budgets/managed/{id}/variance
 *   POST /internal/v1/finance/budgets/managed/{id}/approve
 *
 * COSTA responses use the shared ApiResponse envelope { success, data, ... },
 * so the real payload is on response.data.data.
 */

import { apiClient } from "@impilo/mobile-api-client";

interface ApiEnvelope<T> {
  success: boolean;
  data: T;
  correlationId?: string;
}

export interface BudgetSummary {
  budgetId: string;
  title: string;
  status: string;
  scopeLevel: string;
  periodYear: number;
  currency: string;
}

export interface BudgetVariance {
  budgeted: number;
  committed: number;
  actual: number;
  available: number;
  varianceAmount: number;
  variancePct: number | null;
  classification: string;
  driverNote: string;
  asOfDate: string;
}

const BASE = "/internal/v1/finance/budgets/managed";

export async function listBudgets(year: number, facilityId?: string | null): Promise<BudgetSummary[]> {
  let path = `${BASE}?year=${year}`;
  if (facilityId) path += `&facility_id=${encodeURIComponent(facilityId)}`;
  const res = await apiClient.get<ApiEnvelope<BudgetSummary[]>>(path);
  return res.data?.data ?? [];
}

export async function getVariance(budgetId: string): Promise<BudgetVariance | null> {
  const res = await apiClient.get<ApiEnvelope<BudgetVariance>>(`${BASE}/${budgetId}/variance`);
  return res.data?.data ?? null;
}

/** Approval task — approve a budget that is under review (SoD enforced server-side). */
export async function approveBudget(budgetId: string): Promise<BudgetSummary> {
  const res = await apiClient.post<ApiEnvelope<BudgetSummary>>(`${BASE}/${budgetId}/approve`, {});
  return res.data.data;
}

/** Statuses at which the mobile approval task is offered. */
export function isApprovable(status: string): boolean {
  return status === "UNDER_REVIEW";
}
