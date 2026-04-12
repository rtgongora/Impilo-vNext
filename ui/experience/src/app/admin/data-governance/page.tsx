"use client";

/**
 * Data governance admin — export/access requests, rules, lineage.
 * Route: /admin/data-governance
 */

import { useMemo, useState } from "react";
import {
  AlertTriangle,
  BookMarked,
  FileOutput,
  GitBranch,
  Loader2,
  ShieldCheck,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useApproveExport,
  useCreateExportRequest,
  useDataLineage,
  useDenyExport,
  useExportRequests,
  useGovernanceRules,
} from "@/hooks/queries/useDataGovernance";
import type { AccessRequestResource } from "@/hooks/queries/useDataAccessGovernance";

type TabId = "exports" | "rules" | "lineage";

function requestStatusClass(status: string): string {
  const u = status.toUpperCase();
  if (u === "APPROVED") return "bg-emerald-100 text-emerald-900";
  if (u === "DENIED") return "bg-rose-100 text-rose-900";
  if (u === "PENDING") return "bg-amber-100 text-amber-900";
  return "bg-slate-100 text-slate-700";
}

function ruleRowLabel(row: unknown): string {
  const r = row as Record<string, unknown>;
  return String(r.name ?? r.ruleName ?? r.id ?? r.code ?? "Rule");
}

function lineageRowLabel(row: unknown): string {
  const r = row as Record<string, unknown>;
  const attrs = r.attributes as Record<string, unknown> | undefined;
  return String(r.name ?? attrs?.name ?? r.datasetName ?? r.id ?? "Dataset");
}

export default function DataGovernanceAdminPage() {
  const [tab, setTab] = useState<TabId>("exports");
  const exportQ = useExportRequests();
  const rulesQ = useGovernanceRules();
  const lineageQ = useDataLineage();
  const approveM = useApproveExport();
  const denyM = useDenyExport();
  const createEvalM = useCreateExportRequest();

  const [dataset, setDataset] = useState("national_indicators_v1");
  const [purpose, setPurpose] = useState("OPERATIONS");

  const requests = useMemo(() => exportQ.data ?? [], [exportQ.data]);

  const errorBanner = (err: unknown) => {
    const msg =
      err && typeof err === "object" && "error" in err
        ? String((err as { error?: { message?: string } }).error?.message ?? "Request failed")
        : "Request failed";
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800 flex items-center gap-2">
        <AlertTriangle className="h-4 w-4 shrink-0" />
        {msg}
      </div>
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Data governance"
        subtitle="Export requests, active governance rules, and dataset lineage (DAGS + data-governance-service)"
      >
        <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-2 mb-6">
          {(
            [
              ["exports", "Export requests", FileOutput],
              ["rules", "Governance rules", BookMarked],
              ["lineage", "Data lineage", GitBranch],
            ] as const
          ).map(([id, label, Icon]) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              className={`inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                tab === id ? "bg-indigo-100 text-indigo-900" : "text-slate-600 hover:bg-slate-100"
              }`}
            >
              <Icon className="h-4 w-4" />
              {label}
            </button>
          ))}
        </div>

        {tab === "exports" && (
          <div className="space-y-6">
            <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
              <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-slate-500" />
                Request export evaluation
              </h2>
              <p className="text-xs text-slate-500">
                Submits an export decision request to data-governance-service via{" "}
                <code className="rounded bg-slate-100 px-1">POST /internal/v1/ai-governance/exports/evaluate</code>.
              </p>
              <div className="flex flex-wrap gap-3">
                <input
                  className="rounded-md border border-slate-300 px-2 py-1.5 text-sm min-w-[200px]"
                  value={dataset}
                  onChange={(e) => setDataset(e.target.value)}
                  placeholder="Dataset key"
                />
                <input
                  className="rounded-md border border-slate-300 px-2 py-1.5 text-sm min-w-[200px]"
                  value={purpose}
                  onChange={(e) => setPurpose(e.target.value)}
                  placeholder="Purpose of use"
                />
                <button
                  type="button"
                  disabled={createEvalM.isPending}
                  onClick={() => createEvalM.mutate({ dataset, purposeOfUse: purpose })}
                  className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
                >
                  {createEvalM.isPending ? "Submitting…" : "Evaluate export"}
                </button>
              </div>
              {createEvalM.isError && errorBanner(createEvalM.error)}
              {createEvalM.isSuccess && (
                <pre className="text-xs bg-slate-50 border border-slate-200 rounded-md p-3 overflow-x-auto max-h-48">
                  {JSON.stringify(createEvalM.data, null, 2)}
                </pre>
              )}
            </div>

            <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
              <div className="border-b border-slate-100 px-4 py-3">
                <h2 className="text-sm font-semibold text-slate-900">Access & export requests (DAGS)</h2>
              </div>
              {exportQ.isLoading && (
                <p className="px-4 py-6 text-sm text-slate-500 flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading…
                </p>
              )}
              {exportQ.isError && <div className="p-4">{errorBanner(exportQ.error)}</div>}
              {!exportQ.isLoading && requests.length === 0 && (
                <p className="px-4 py-6 text-sm text-slate-500">No access requests returned.</p>
              )}
              {requests.length > 0 && (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm min-w-[640px]">
                    <thead className="bg-slate-50 text-left text-xs text-slate-600">
                      <tr>
                        <th className="px-3 py-2">Status</th>
                        <th className="px-3 py-2">Requester</th>
                        <th className="px-3 py-2">Resource</th>
                        <th className="px-3 py-2">Purpose</th>
                        <th className="px-3 py-2">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {requests.map((r: AccessRequestResource) => {
                        const st = r.attributes.status;
                        return (
                          <tr key={r.id} className="border-t border-slate-100">
                            <td className="px-3 py-2">
                              <span
                                className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${requestStatusClass(st)}`}
                              >
                                {st}
                              </span>
                            </td>
                            <td className="px-3 py-2">
                              <div className="font-medium text-slate-900">{r.attributes.requesterName}</div>
                              <div className="text-xs text-slate-500">{r.attributes.requesterId}</div>
                            </td>
                            <td className="px-3 py-2 text-slate-700">
                              {r.attributes.resourceType}:{r.attributes.resourceId}
                            </td>
                            <td className="px-3 py-2 text-slate-600">{r.attributes.purpose}</td>
                            <td className="px-3 py-2">
                              {st === "PENDING" && (
                                <div className="flex flex-wrap gap-1">
                                  <button
                                    type="button"
                                    disabled={approveM.isPending}
                                    onClick={() => approveM.mutate({ id: r.id })}
                                    className="rounded-md bg-emerald-700 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-800 disabled:opacity-50"
                                  >
                                    Approve
                                  </button>
                                  <button
                                    type="button"
                                    disabled={denyM.isPending}
                                    onClick={() => denyM.mutate({ id: r.id, notes: "Denied from governance console" })}
                                    className="rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-50"
                                  >
                                    Deny
                                  </button>
                                </div>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
              {(approveM.isError || denyM.isError) && (
                <div className="p-4 space-y-2">
                  {approveM.isError && errorBanner(approveM.error)}
                  {denyM.isError && errorBanner(denyM.error)}
                </div>
              )}
            </div>
          </div>
        )}

        {tab === "rules" && (
          <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
            {rulesQ.isLoading && (
              <p className="px-4 py-6 text-sm text-slate-500 flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading rules…
              </p>
            )}
            {rulesQ.isError && <div className="p-4">{errorBanner(rulesQ.error)}</div>}
            {!rulesQ.isLoading && (rulesQ.data?.length ?? 0) === 0 && (
              <p className="px-4 py-6 text-sm text-slate-500">No governance rules returned.</p>
            )}
            {(rulesQ.data?.length ?? 0) > 0 && (
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs text-slate-600">
                  <tr>
                    <th className="px-3 py-2">Rule</th>
                    <th className="px-3 py-2">Effect</th>
                    <th className="px-3 py-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {rulesQ.data!.map((row, i) => {
                    const r = row as Record<string, unknown>;
                    return (
                      <tr key={String(r.id ?? i)} className="border-t border-slate-100">
                        <td className="px-3 py-2 font-medium">{ruleRowLabel(row)}</td>
                        <td className="px-3 py-2">{String(r.effect ?? r.decision ?? "—")}</td>
                        <td className="px-3 py-2">
                          <span className="inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-800">
                            {String(r.status ?? r.state ?? "ACTIVE")}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        )}

        {tab === "lineage" && (
          <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
            {lineageQ.isLoading && (
              <p className="px-4 py-6 text-sm text-slate-500 flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading datasets…
              </p>
            )}
            {lineageQ.isError && <div className="p-4">{errorBanner(lineageQ.error)}</div>}
            {!lineageQ.isLoading && (lineageQ.data?.length ?? 0) === 0 && (
              <p className="px-4 py-6 text-sm text-slate-500">No datasets for lineage view.</p>
            )}
            {(lineageQ.data?.length ?? 0) > 0 && (
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs text-slate-600">
                  <tr>
                    <th className="px-3 py-2">Dataset</th>
                    <th className="px-3 py-2">Provenance</th>
                    <th className="px-3 py-2">Last snapshot</th>
                  </tr>
                </thead>
                <tbody>
                  {lineageQ.data!.map((row, i) => {
                    const r = row as Record<string, unknown>;
                    const id = String(r.id ?? r.datasetId ?? i);
                    return (
                      <tr key={id} className="border-t border-slate-100">
                        <td className="px-3 py-2 font-medium">{lineageRowLabel(row)}</td>
                        <td className="px-3 py-2 text-slate-600">
                          {String(r.sourceSystem ?? r.upstream ?? "MOHCC · National spine")}
                        </td>
                        <td className="px-3 py-2 text-slate-600">{String(r.updatedAt ?? r.lastSnapshot ?? "—")}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
