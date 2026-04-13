/**
 * Report Service — Fetch available reports and report data.
 *
 * Backend: experience-bff /internal/v1/mobile/provider/reports/*
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { ReportSummary, ReportData, ReportDataEntry } from "../types";

const V1 = "/internal/v1/mobile/provider/reports";

/* ── Report List ────────────────────────────────────────────────────── */

interface ReportResource {
  id: string;
  name: string;
  category: string;
  description?: string;
  last_run_at?: string;
}

function mapReportSummary(r: ReportResource): ReportSummary {
  return {
    id: r.id,
    name: r.name,
    category: r.category as ReportSummary["category"],
    description: r.description,
    lastRunAt: r.last_run_at,
  };
}

export async function fetchReportList(): Promise<ReportSummary[]> {
  const response = await apiClient.get<{ data: ReportResource[] }>(V1);
  return response.data.data.map(mapReportSummary);
}

/* ── Report Data ────────────────────────────────────────────────────── */

interface ReportDataResource {
  report_id: string;
  report_name: string;
  generated_at: string;
  entries: { key: string; value: string | number; unit?: string }[];
}

function mapReportData(r: ReportDataResource): ReportData {
  return {
    reportId: r.report_id,
    reportName: r.report_name,
    generatedAt: r.generated_at,
    entries: r.entries.map(
      (e): ReportDataEntry => ({
        key: e.key,
        value: e.value,
        unit: e.unit,
      })
    ),
  };
}

export async function fetchReportData(reportId: string): Promise<ReportData> {
  const response = await apiClient.get<{ data: ReportDataResource }>(
    `${V1}/${reportId}/data`
  );
  return mapReportData(response.data.data);
}
