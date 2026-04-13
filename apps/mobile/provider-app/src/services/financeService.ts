/**
 * Finance Service — Revenue summary, claims, and payments.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/finance/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { RevenueSummary, Claim, Payment } from "../types";

const V1 = "/internal/v1/mobile/provider/finance";

/* ── Revenue Summary ────────────────────────────────────────────────── */

interface RevenueSummaryResource {
  today_revenue: number;
  week_total: number;
  month_total: number;
  currency: string;
  updated_at: string;
}

function mapRevenueSummary(r: RevenueSummaryResource): RevenueSummary {
  return {
    todayRevenue: r.today_revenue,
    weekTotal: r.week_total,
    monthTotal: r.month_total,
    currency: r.currency,
    updatedAt: r.updated_at,
  };
}

export async function fetchRevenueSummary(): Promise<RevenueSummary> {
  const response = await apiClient.get<{ data: RevenueSummaryResource }>(
    `${V1}/summary`
  );
  return mapRevenueSummary(response.data.data);
}

/* ── Pending Claims ─────────────────────────────────────────────────── */

interface ClaimResource {
  id: string;
  patient_id: string;
  patient_name: string;
  amount: number;
  currency: string;
  status: string;
  submitted_at: string;
  resolved_at?: string;
}

function mapClaim(r: ClaimResource): Claim {
  return {
    id: r.id,
    patientId: r.patient_id,
    patientName: r.patient_name,
    amount: r.amount,
    currency: r.currency,
    status: r.status as Claim["status"],
    submittedAt: r.submitted_at,
    resolvedAt: r.resolved_at,
  };
}

export async function fetchPendingClaims(): Promise<Claim[]> {
  const response = await apiClient.get<{ data: ClaimResource[] }>(
    `${V1}/claims?status=PENDING`
  );
  return response.data.data.map(mapClaim);
}

/* ── Recent Payments ────────────────────────────────────────────────── */

interface PaymentResource {
  id: string;
  patient_id: string;
  patient_name: string;
  amount: number;
  currency: string;
  status: string;
  paid_at: string;
  reference?: string;
}

function mapPayment(r: PaymentResource): Payment {
  return {
    id: r.id,
    patientId: r.patient_id,
    patientName: r.patient_name,
    amount: r.amount,
    currency: r.currency,
    status: r.status as Payment["status"],
    paidAt: r.paid_at,
    reference: r.reference,
  };
}

export async function fetchRecentPayments(): Promise<Payment[]> {
  const response = await apiClient.get<{ data: PaymentResource[] }>(
    `${V1}/payments?limit=20`
  );
  return response.data.data.map(mapPayment);
}
