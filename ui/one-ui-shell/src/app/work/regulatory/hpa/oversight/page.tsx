"use client";

/**
 * HPA oversight (ROM-W10). The Health Professions Authority's cross-council intelligence
 * environment. HPA does NOT operate any council's desk — it sees consolidated aggregates across
 * all nine organisations, and an individual council case only through an explicit, recorded
 * escalation grant.
 *
 * Route: /work/regulatory/hpa/oversight
 */

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, ShieldAlert, ShieldCheck } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useRegulatoryOrganisations } from "@/hooks/queries/useRegulatoryOrganisations";

// The HPA organisation is resolved from org-registry rather than restated here. The deterministic
// V007 literal remains only as the pre-load fallback: this page addresses HPA before the registry
// answers, and a blank id would look like a broken page rather than a loading one.
const HPA_ORG_ID_FALLBACK = "a5000000-0000-4000-8000-000000000001";

type Row = Record<string, unknown>;

/** Tolerant unwrap: raw array, or {data:[...]}, or {data:{rows|result:[...]}}, or {rows|result:[...]}. */
function unwrapRows(payload: unknown): Row[] {
  const asRowArray = (v: unknown): Row[] | null =>
    Array.isArray(v) ? (v.filter((x) => x && typeof x === "object" && !Array.isArray(x)) as Row[]) : null;

  const direct = asRowArray(payload);
  if (direct) return direct;

  if (payload && typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    const fromData = asRowArray(obj.data);
    if (fromData) return fromData;
    const fromRows = asRowArray(obj.rows);
    if (fromRows) return fromRows;
    const fromResult = asRowArray(obj.result);
    if (fromResult) return fromResult;
    if (obj.data && typeof obj.data === "object") {
      const inner = obj.data as Record<string, unknown>;
      const innerRows = asRowArray(inner.rows);
      if (innerRows) return innerRows;
      const innerResult = asRowArray(inner.result);
      if (innerResult) return innerResult;
    }
  }
  return [];
}

function columnsOf(rows: Row[]): string[] {
  const cols: string[] = [];
  for (const row of rows) {
    for (const key of Object.keys(row)) {
      if (!cols.includes(key)) cols.push(key);
    }
  }
  return cols;
}

function renderCell(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

interface OversightGrant {
  id: string;
  councilId: string;
  subjectType: string;
  subjectRef: string;
  grantedToOrgId: string;
  reason: string;
  status: string;
}

/** Tolerant unwrap for the grants list (array or {data:[...]}). */
function unwrapGrants(payload: unknown): OversightGrant[] {
  const pick = (v: unknown): OversightGrant[] | null =>
    Array.isArray(v) ? (v as OversightGrant[]) : null;
  const direct = pick(payload);
  if (direct) return direct;
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = pick((payload as { data?: unknown }).data);
    if (inner) return inner;
  }
  return [];
}

const SUBJECT_TYPES = ["DISCIPLINARY_CASE", "APPLICATION", "APPEAL", "PROVIDER"] as const;
type SubjectType = (typeof SUBJECT_TYPES)[number];

const INDICATORS_KEY = "reg.oversight.cross_council_indicators";

export default function HpaOversightPage() {
  // (a) Consolidated indicators
  const [rows, setRows] = useState<Row[]>([]);
  const [indicatorsLoading, setIndicatorsLoading] = useState(false);
  const [indicatorsError, setIndicatorsError] = useState<string | null>(null);
  const [indicatorsRan, setIndicatorsRan] = useState(false);

  // (b) Escalation grants
  const [grants, setGrants] = useState<OversightGrant[]>([]);
  const [grantsLoading, setGrantsLoading] = useState(false);
  const [grantsError, setGrantsError] = useState<string | null>(null);

  const [councilId, setCouncilId] = useState("");
  const [subjectType, setSubjectType] = useState<SubjectType>("DISCIPLINARY_CASE");
  const [subjectRef, setSubjectRef] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const runIndicators = useCallback(async () => {
    setIndicatorsLoading(true);
    setIndicatorsError(null);
    setIndicatorsRan(false);
    try {
      const payload = await apiClient.post<unknown>(
        `/internal/v1/regulatory/console/reports/${INDICATORS_KEY}/run`,
        {},
      );
      setRows(unwrapRows(payload));
    } catch {
      setIndicatorsError(
        "Consolidated indicators could not be loaded yet — the source tables may still be awaiting deployment.",
      );
    } finally {
      setIndicatorsRan(true);
      setIndicatorsLoading(false);
    }
  }, []);

  const loadGrants = useCallback(async () => {
    setGrantsLoading(true);
    setGrantsError(null);
    try {
      const payload = await apiClient.get<unknown>(
        `/internal/v1/regulatory/console/oversight/grants?orgId=${encodeURIComponent(HPA_ORG_ID)}`,
      );
      setGrants(unwrapGrants(payload));
    } catch {
      setGrantsError("Could not load escalation grants. Please try again.");
    } finally {
      setGrantsLoading(false);
    }
  }, []);

  useEffect(() => {
    void runIndicators();
    void loadGrants();
  }, [runIndicators, loadGrants]);

  const createGrant = useCallback(async () => {
    if (!councilId.trim() || !subjectRef.trim() || !reason.trim()) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await apiClient.post<unknown>("/internal/v1/regulatory/console/oversight/grants", {
        councilId: councilId.trim(),
        subjectType,
        subjectRef: subjectRef.trim(),
        grantedToOrgId: HPA_ORG_ID,
        reason: reason.trim(),
      });
      setCouncilId("");
      setSubjectRef("");
      setReason("");
      setSubjectType("DISCIPLINARY_CASE");
      await loadGrants();
    } catch {
      setSubmitError("Could not record the escalation grant. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }, [councilId, subjectType, subjectRef, reason, loadGrants]);

  const columns = useMemo(() => columnsOf(rows), [rows]);
  const { councils, authority } = useRegulatoryOrganisations();
  const HPA_ORG_ID = authority?.id ?? HPA_ORG_ID_FALLBACK;

  return (
    <AppLayout>
      <PageShell
        title="HPA oversight"
        subtitle="Cross-council intelligence and escalations"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-8 p-5 sm:p-6">
          {/* (a) Consolidated indicators */}
          <section className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-foreground">Consolidated indicators</h2>
              <span className="text-[11px] text-muted-foreground">{INDICATORS_KEY}</span>
            </div>

            <div className="flex items-start gap-2 rounded-lg border border-purple-200 bg-purple-50 p-3 text-xs text-purple-900">
              <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0" />
              <span>
                HPA sees consolidated aggregates across all nine organisations — not any council&apos;s
                operational desk.
              </span>
            </div>

            {indicatorsLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading indicators…
              </div>
            ) : indicatorsError ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                {indicatorsError}
              </div>
            ) : indicatorsRan && rows.length === 0 ? (
              <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
                No data yet — the cross-council indicators returned no rows (tables may be awaiting
                deployment).
              </div>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-border">
                <table className="w-full text-left text-xs">
                  <thead className="bg-muted/50 text-muted-foreground">
                    <tr>
                      {columns.map((c) => (
                        <th key={c} className="whitespace-nowrap px-3 py-2 font-semibold">
                          {c}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((row, i) => (
                      <tr key={i} className="border-t border-border">
                        {columns.map((c) => (
                          <td key={c} className="whitespace-nowrap px-3 py-2 text-foreground">
                            {renderCell(row[c])}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          {/* (b) Escalation grants */}
          <section className="space-y-3">
            <h2 className="text-sm font-semibold text-foreground">Escalation grants</h2>

            <div className="flex items-start gap-2 rounded-lg border border-purple-200 bg-purple-50 p-3 text-xs text-purple-900">
              <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
              <span>
                HPA sees a council&apos;s individual case only through an explicit, recorded escalation
                grant.
              </span>
            </div>

            {grantsLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading grants…
              </div>
            ) : grantsError ? (
              <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {grantsError}
              </div>
            ) : grants.length === 0 ? (
              <p className="text-sm text-muted-foreground">No active escalation grants.</p>
            ) : (
              <div className="space-y-2">
                {grants.map((g) => (
                  <div key={g.id} className="rounded-xl border border-border bg-card p-3 text-xs">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-foreground">
                        {g.subjectType.replace(/_/g, " ")} · {g.subjectRef}
                      </span>
                      <span className="rounded-full border border-border px-2 py-0.5 text-[10px] text-muted-foreground">
                        {g.status}
                      </span>
                    </div>
                    <div className="mt-1 text-muted-foreground">
                      Council: <span className="text-foreground">{g.councilId}</span>
                    </div>
                    {g.reason && <div className="mt-0.5 text-muted-foreground">{g.reason}</div>}
                  </div>
                ))}
              </div>
            )}

            <div className="rounded-2xl border border-border bg-card p-4">
              <h3 className="mb-3 text-sm font-semibold text-foreground">Record an escalation grant</h3>
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="text-xs font-medium text-muted-foreground">
                  Council
                  <select
                    value={councilId}
                    onChange={(e) => setCouncilId(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm text-foreground"
                  >
                    <option value="">Select a council…</option>
                    {councils.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.code} — {c.name}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="text-xs font-medium text-muted-foreground">
                  Subject type
                  <select
                    value={subjectType}
                    onChange={(e) => setSubjectType(e.target.value as SubjectType)}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm text-foreground"
                  >
                    {SUBJECT_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </label>

                <label className="text-xs font-medium text-muted-foreground">
                  Subject reference
                  <input
                    value={subjectRef}
                    onChange={(e) => setSubjectRef(e.target.value)}
                    placeholder="Case / application / provider ref"
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm text-foreground"
                  />
                </label>

                <label className="text-xs font-medium text-muted-foreground">
                  Granted to
                  <input
                    value={HPA_ORG_ID}
                    readOnly
                    className="mt-1 w-full rounded-lg border border-border bg-muted/40 px-3 py-2 text-sm text-muted-foreground"
                  />
                </label>

                <label className="text-xs font-medium text-muted-foreground sm:col-span-2">
                  Reason
                  <textarea
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    rows={2}
                    placeholder="Why HPA needs sight of this individual case."
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm text-foreground"
                  />
                </label>
              </div>

              {submitError && (
                <div className="mt-2 rounded-lg border border-red-200 bg-red-50 p-2 text-xs text-red-800">
                  {submitError}
                </div>
              )}

              <button
                type="button"
                onClick={() => void createGrant()}
                disabled={submitting || !councilId.trim() || !subjectRef.trim() || !reason.trim()}
                className="mt-3 inline-flex items-center gap-2 rounded-lg bg-purple-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-purple-700 disabled:opacity-50"
              >
                {submitting && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                {submitting ? "Recording…" : "Record grant"}
              </button>
            </div>
          </section>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
