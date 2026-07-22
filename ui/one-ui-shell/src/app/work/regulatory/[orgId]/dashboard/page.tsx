"use client";

/**
 * Regulatory dashboards (ROM-W9). Governed report definitions by class — a regulator runs a
 * report and sees its rows. Reports run against org-registry / varapi report definitions via the
 * BFF regulatory console; some run against not-yet-deployed tables, so a run may return empty or
 * error — the page renders honestly in every case.
 *
 * Route: /work/regulatory/[orgId]/dashboard
 */

import { useCallback, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, BarChart3, Loader2, Play } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { regulatoryOrg } from "@/lib/regulatory/organisations";

type ReportClass =
  | "OPERATIONAL"
  | "MANAGEMENT"
  | "STATUTORY"
  | "PUBLIC_INTEREST"
  | "OVERSIGHT";

interface ReportDef {
  key: string;
  label: string;
  description: string;
  reportClass: ReportClass;
}

const REPORTS: ReportDef[] = [
  {
    key: "reg.operational.application_queue",
    label: "Application queue",
    description: "Open applications by council and state.",
    reportClass: "OPERATIONAL",
  },
  {
    key: "reg.management.application_tat",
    label: "Application turnaround",
    description: "Turnaround days per council.",
    reportClass: "MANAGEMENT",
  },
  {
    key: "reg.statutory.annual_register_return",
    label: "Annual register return",
    description: "Register entries by register and status.",
    reportClass: "STATUTORY",
  },
  {
    key: "reg.public.good_standing_summary",
    label: "Good standing summary",
    description: "Good-standing counts.",
    reportClass: "PUBLIC_INTEREST",
  },
  {
    key: "reg.oversight.cross_council_indicators",
    label: "Cross-council indicators",
    description: "Per-council open applications and disciplinary indicators.",
    reportClass: "OVERSIGHT",
  },
];

const CLASS_STYLES: Record<ReportClass, string> = {
  OPERATIONAL: "border-teal-200 bg-teal-50 text-teal-700",
  MANAGEMENT: "border-indigo-200 bg-indigo-50 text-indigo-700",
  STATUTORY: "border-amber-200 bg-amber-50 text-amber-800",
  PUBLIC_INTEREST: "border-emerald-200 bg-emerald-50 text-emerald-700",
  OVERSIGHT: "border-purple-200 bg-purple-50 text-purple-700",
};

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

/** Column union across rows (stable order: first-seen). */
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

export default function RegulatoryDashboardPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const org = regulatoryOrg(orgId);

  const [activeKey, setActiveKey] = useState<string | null>(null);
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ran, setRan] = useState(false);

  const runReport = useCallback(async (key: string) => {
    setActiveKey(key);
    setLoading(true);
    setError(null);
    setRan(false);
    setRows([]);
    try {
      const payload = await apiClient.post<unknown>(
        `/internal/v1/regulatory/console/reports/${encodeURIComponent(key)}/run`,
        {},
      );
      setRows(unwrapRows(payload));
    } catch {
      setError(
        "This report could not be run yet — its data source may still be awaiting deployment.",
      );
    } finally {
      setRan(true);
      setLoading(false);
    }
  }, []);

  const activeReport = REPORTS.find((r) => r.key === activeKey);
  const columns = columnsOf(rows);

  return (
    <AppLayout>
      <PageShell
        title="Regulatory dashboards"
        subtitle="Operational, management and statutory reporting"
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <Link
            href={`/work/regulatory/${encodeURIComponent(orgId)}`}
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to workspace
          </Link>

          <div className="flex items-center gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
            <BarChart3 className="h-4 w-4 shrink-0 text-teal-600" />
            <span>
              Reporting context:{" "}
              <b className="text-foreground">{org?.name ?? orgId}</b>
              {org ? ` · ${org.code}` : ""}
            </span>
          </div>

          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {REPORTS.map((r) => (
              <button
                key={r.key}
                type="button"
                onClick={() => void runReport(r.key)}
                className={`rounded-2xl border p-4 text-left transition hover:border-teal-300 ${
                  activeKey === r.key ? "border-teal-400 bg-teal-50/40" : "border-border bg-card"
                }`}
              >
                <div className="mb-2 flex items-center justify-between gap-2">
                  <span
                    className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${CLASS_STYLES[r.reportClass]}`}
                  >
                    {r.reportClass.replace(/_/g, " ")}
                  </span>
                  {loading && activeKey === r.key ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-teal-600" />
                  ) : (
                    <Play className="h-3.5 w-3.5 text-muted-foreground" />
                  )}
                </div>
                <div className="text-sm font-semibold text-foreground">{r.label}</div>
                <p className="mt-0.5 text-xs text-muted-foreground">{r.description}</p>
              </button>
            ))}
          </section>

          <section className="space-y-3">
            {!activeReport ? (
              <p className="text-sm text-muted-foreground">
                Select a report above to run it and view its rows.
              </p>
            ) : (
              <>
                <div className="flex items-center justify-between gap-2">
                  <h2 className="text-sm font-semibold text-foreground">{activeReport.label}</h2>
                  <span className="text-[11px] text-muted-foreground">{activeReport.key}</span>
                </div>

                {loading ? (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" /> Running report…
                  </div>
                ) : error ? (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                    {error}
                  </div>
                ) : ran && rows.length === 0 ? (
                  <div className="rounded-lg border border-border bg-muted/40 p-4 text-sm text-muted-foreground">
                    No data yet — this report returned no rows (its tables may be awaiting deployment).
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
              </>
            )}
          </section>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
