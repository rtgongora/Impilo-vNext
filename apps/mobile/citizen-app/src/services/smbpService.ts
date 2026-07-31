/**
 * SMBP (self-measured blood pressure) series verdict — Citizen mobile (RMNP W12).
 *
 * Backend: experience-bff ConfidentialMaternityController
 *   GET /internal/v1/confidential/maternity/smbp/verdict?planId=…&dangerSignPresent=…
 *     -> { data: <CKP verdict>, meta: { plan_id, raw_reading_count, paired_reading_count, ... } }
 *     502 READINGS_UNAVAILABLE / CKP_UNAVAILABLE when the series could not be read or assessed.
 *
 * `dangerSignPresent` is omitted (never coerced to `false`) whenever the caller has not asked her
 * about a danger sign — see docs/clinical/rmnp/citizen-pregnancy-smbp-mobile-contract.md §4. A
 * fetch of "her plans" also lives here: mobile has no monitoring-plan listing screen of its own,
 * and the verdict is meaningless without the `planId` it is scoped to.
 *
 * `X-Actor-ID` (mobile-trust) already carries her Health ID on every request; the plans lane
 * (`/internal/v1/telemonitoring/my/plans`) resolves the patient from it server-side, and the
 * maternity lane resolves her CPID from the same header when the BFF is given the `"me"` sentinel
 * — this service never needs, and never sends, a raw CPID (Identity Contract §12).
 */

import { apiClient, ApiError } from "@impilo/mobile-api-client";

const TELEMONITORING = "/internal/v1/telemonitoring";
const MATERNITY = "/internal/v1/confidential/maternity";

export interface MonitoringPlanSummary {
  id: string;
  programmeCode: string;
  status: string;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : null;
}

/** Her active/pending remote-monitoring plans. Never throws for an ordinary empty list. */
export async function fetchMyMonitoringPlans(): Promise<MonitoringPlanSummary[]> {
  const response = await apiClient.get<unknown>(`${TELEMONITORING}/my/plans`);
  const raw = Array.isArray(response.data) ? response.data : [];
  return raw
    .map((r) => {
      const rec = asRecord(r);
      if (!rec || typeof rec.id !== "string") return null;
      return {
        id: rec.id,
        programmeCode: typeof rec.programmeCode === "string" ? rec.programmeCode : "",
        status: typeof rec.status === "string" ? rec.status : "",
      };
    })
    .filter((p): p is MonitoringPlanSummary => p !== null);
}

export type SmbpVerdict = "URGENT" | "ELEVATED_REFER" | "CONTROLLED" | "INSUFFICIENT_DATA";

export interface SmbpAlert {
  code: string;
  name: string;
  severity: string;
  message: string;
  requiredAction?: string;
  monitoringInstruction?: string;
}

export interface SmbpSummary {
  readingCount: number;
  meanCount: number;
  meanSystolic: number | null;
  meanDiastolic: number | null;
  maxSystolic: number | null;
  maxDiastolic: number | null;
  anySevere: boolean;
  elevatedMean: boolean;
  sufficient: boolean;
}

export interface SmbpVerdictResult {
  /** URGENT | ELEVATED_REFER | CONTROLLED | INSUFFICIENT_DATA — never inferred, only what CKP said. */
  verdict: SmbpVerdict;
  message: string;
  note: string;
  summary: SmbpSummary;
  alerts: SmbpAlert[];
  planId: string;
  rawReadingCount: number;
  pairedReadingCount: number;
}

/**
 * Thrown for the 502 READINGS_UNAVAILABLE / CKP_UNAVAILABLE outcomes — an outage, never a
 * clinical answer. Callers must not render {@link SmbpVerdict}-shaped UI (a badge, a "controlled"
 * banner) when they catch this; it means the series could not be read or assessed at all.
 */
export class SmbpUnavailableError extends Error {
  public readonly code: "READINGS_UNAVAILABLE" | "CKP_UNAVAILABLE";

  constructor(code: "READINGS_UNAVAILABLE" | "CKP_UNAVAILABLE", message: string) {
    super(message);
    this.name = "SmbpUnavailableError";
    this.code = code;
  }
}

export function isSmbpUnavailableError(err: unknown): err is SmbpUnavailableError {
  return err instanceof SmbpUnavailableError;
}

function normalizeAlert(raw: unknown): SmbpAlert | null {
  const r = asRecord(raw);
  if (!r || typeof r.code !== "string" || typeof r.message !== "string") return null;
  return {
    code: r.code,
    name: typeof r.name === "string" ? r.name : r.code,
    severity: typeof r.severity === "string" ? r.severity : "UNKNOWN",
    message: r.message,
    requiredAction: typeof r.requiredAction === "string" ? r.requiredAction : undefined,
    monitoringInstruction:
      typeof r.monitoringInstruction === "string" ? r.monitoringInstruction : undefined,
  };
}

function normalizeSummary(raw: unknown): SmbpSummary {
  const r = asRecord(raw) ?? {};
  return {
    readingCount: typeof r.readingCount === "number" ? r.readingCount : 0,
    meanCount: typeof r.meanCount === "number" ? r.meanCount : 0,
    meanSystolic: typeof r.meanSystolic === "number" ? r.meanSystolic : null,
    meanDiastolic: typeof r.meanDiastolic === "number" ? r.meanDiastolic : null,
    maxSystolic: typeof r.maxSystolic === "number" ? r.maxSystolic : null,
    maxDiastolic: typeof r.maxDiastolic === "number" ? r.maxDiastolic : null,
    anySevere: r.anySevere === true,
    elevatedMean: r.elevatedMean === true,
    sufficient: r.sufficient === true,
  };
}

const KNOWN_VERDICTS: SmbpVerdict[] = ["URGENT", "ELEVATED_REFER", "CONTROLLED", "INSUFFICIENT_DATA"];

/**
 * Fetches the SMBP series verdict for one monitoring plan.
 *
 * `dangerSignPresent` must stay `undefined` unless she was actually asked — an absent value
 * reaches CKP as UNKNOWN, never as a denial. Rejects with {@link SmbpUnavailableError} for the
 * two upstream-outage codes; any other rejection is a plain {@link ApiError}/network failure.
 */
export async function fetchSmbpVerdict(
  planId: string,
  dangerSignPresent?: boolean,
): Promise<SmbpVerdictResult> {
  const params = new URLSearchParams({ planId });
  if (dangerSignPresent !== undefined) {
    params.set("dangerSignPresent", String(dangerSignPresent));
  }

  try {
    const response = await apiClient.get<{ data?: unknown; meta?: unknown }>(
      `${MATERNITY}/smbp/verdict?${params.toString()}`,
    );
    const body = response.data;
    const data = asRecord(body?.data);
    const meta = asRecord(body?.meta) ?? {};
    const verdictRaw = data?.verdict;
    const verdict: SmbpVerdict =
      typeof verdictRaw === "string" && KNOWN_VERDICTS.includes(verdictRaw as SmbpVerdict)
        ? (verdictRaw as SmbpVerdict)
        // An unrecognised verdict is treated as insufficient, never as controlled — the safe
        // direction when this client cannot understand what CKP said.
        : "INSUFFICIENT_DATA";
    const alertsRaw = Array.isArray(data?.alerts) ? data.alerts : [];
    return {
      verdict,
      message: typeof data?.message === "string" ? data.message : "",
      note: typeof data?.note === "string" ? data.note : "",
      summary: normalizeSummary(data?.summary),
      alerts: alertsRaw.map(normalizeAlert).filter((a): a is SmbpAlert => a !== null),
      planId: typeof meta.plan_id === "string" ? meta.plan_id : planId,
      rawReadingCount: typeof meta.raw_reading_count === "number" ? meta.raw_reading_count : 0,
      pairedReadingCount:
        typeof meta.paired_reading_count === "number" ? meta.paired_reading_count : 0,
    };
  } catch (err) {
    if (err instanceof ApiError && (err.code === "READINGS_UNAVAILABLE" || err.code === "CKP_UNAVAILABLE")) {
      throw new SmbpUnavailableError(err.code, err.message);
    }
    throw err;
  }
}
