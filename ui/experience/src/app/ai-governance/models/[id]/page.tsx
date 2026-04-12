"use client";

/**
 * AI Model detail — versions, use cases, approval history.
 * Route: /ai-governance/models/[id]
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  Cpu,
  History,
  Loader2,
  Plus,
  Shield,
  ShieldOff,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAddAiModelVersion,
  useAiModel,
  useApproveAiModel,
  useWithdrawAiModel,
} from "@/hooks/queries/useAiGovernance";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

function readStrArray(v: unknown): string[] {
  if (!Array.isArray(v)) return [];
  return v.filter((x): x is string => typeof x === "string" && x.length > 0);
}

function readRecordArray(v: unknown): Record<string, unknown>[] {
  if (!Array.isArray(v)) return [];
  return v.filter((x) => x && typeof x === "object") as Record<string, unknown>[];
}

const STATUS_BADGE: Record<string, string> = {
  APPROVED: "bg-emerald-100 text-emerald-800",
  PENDING: "bg-amber-100 text-amber-800",
  DRAFT: "bg-slate-100 text-slate-700",
  WITHDRAWN: "bg-gray-200 text-gray-600",
};

export default function AiModelDetailPage() {
  const params = useParams();
  const modelId = typeof params?.id === "string" ? params.id : "";
  const { data, isLoading, isError } = useAiModel(modelId || undefined);
  const approveM = useApproveAiModel();
  const withdrawM = useWithdrawAiModel();
  const addVersionM = useAddAiModelVersion();

  const [showVersionForm, setShowVersionForm] = useState(false);
  const [verForm, setVerForm] = useState({
    version: "",
    notes: "",
    artifactUri: "",
  });

  const row = useMemo(() => asRecord(data), [data]);

  const versions = useMemo(() => {
    const v = row.versions ?? row.versionList ?? row.modelVersions;
    return readRecordArray(v);
  }, [row]);

  const approvedUse = useMemo(() => {
    const v = row.approvedUseCases ?? row.approvedUses ?? row.allowedUseCases;
    return readStrArray(v);
  }, [row]);

  const prohibitedUse = useMemo(() => {
    const v = row.prohibitedUseCases ?? row.prohibitedUses ?? row.deniedUseCases;
    return readStrArray(v);
  }, [row]);

  const approvalHistory = useMemo(() => {
    const v = row.approvalHistory ?? row.history ?? row.statusHistory;
    return readRecordArray(v);
  }, [row]);

  const status = readStr(row, "status", "approvalStatus").toUpperCase() || "DRAFT";

  const submitVersion = () => {
    if (!modelId || !verForm.version.trim()) return;
    addVersionM.mutate(
      {
        modelId,
        body: {
          version: verForm.version.trim(),
          notes: verForm.notes.trim(),
          artifactUri: verForm.artifactUri.trim(),
        },
      },
      {
        onSuccess: () => {
          setShowVersionForm(false);
          setVerForm({ version: "", notes: "", artifactUri: "" });
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title={readStr(row, "name", "modelName") || "Model"}
        subtitle="Version lineage, permitted use, and approval audit"
        icon={<Cpu className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link
            href="/ai-governance"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-800"
          >
            <ArrowLeft className="h-4 w-4" />
            Model registry
          </Link>
        </div>

        {isLoading && (
          <div className="flex items-center justify-center gap-2 py-20 text-gray-500">
            <Loader2 className="h-6 w-6 animate-spin" /> Loading model…
          </div>
        )}

        {isError && (
          <p className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
            Could not load model <code className="rounded bg-white px-1">{modelId}</code>. Ensure GET
            /internal/v1/ai/models/{"{id}"} is available.
          </p>
        )}

        {!isLoading && !isError && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-start justify-between gap-4 rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
              <div className="space-y-1 text-sm">
                <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Status</p>
                <span
                  className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGE[status] ?? "bg-gray-100 text-gray-700"}`}
                >
                  {status}
                </span>
                <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Type</p>
                <p className="text-gray-900">{readStr(row, "type", "modelType") || "—"}</p>
                <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Owner</p>
                <p className="text-gray-900">{readStr(row, "owner", "ownerId") || "—"}</p>
                <p className="mt-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Description</p>
                <p className="max-w-xl text-gray-700">{readStr(row, "description", "summary") || "—"}</p>
              </div>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={approveM.isPending || !modelId}
                  onClick={() => approveM.mutate(modelId)}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-900 hover:bg-emerald-100 disabled:opacity-50"
                >
                  <Shield className="h-4 w-4" />
                  Approve
                </button>
                <button
                  type="button"
                  disabled={withdrawM.isPending || !modelId}
                  onClick={() => withdrawM.mutate(modelId)}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-800 hover:bg-gray-50 disabled:opacity-50"
                >
                  <ShieldOff className="h-4 w-4" />
                  Withdraw
                </button>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-base font-semibold text-gray-900">Versions</h2>
              <button
                type="button"
                onClick={() => setShowVersionForm((s) => !s)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-cyan-600 px-3 py-2 text-sm font-medium text-white hover:bg-cyan-700"
              >
                <Plus className="h-4 w-4" />
                Add Version
              </button>
            </div>

            {showVersionForm && (
              <div className="rounded-xl border-2 border-cyan-200 bg-cyan-50/40 p-4 space-y-3 text-sm">
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block">
                    <span className="text-xs font-medium text-gray-600">Version label</span>
                    <input
                      className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2"
                      value={verForm.version}
                      onChange={(e) => setVerForm((f) => ({ ...f, version: e.target.value }))}
                      placeholder="e.g. 2.1.0"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-medium text-gray-600">Artifact URI</span>
                    <input
                      className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-xs"
                      value={verForm.artifactUri}
                      onChange={(e) => setVerForm((f) => ({ ...f, artifactUri: e.target.value }))}
                      placeholder="s3://… or oci://…"
                    />
                  </label>
                </div>
                <label className="block">
                  <span className="text-xs font-medium text-gray-600">Notes</span>
                  <textarea
                    className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2"
                    rows={2}
                    value={verForm.notes}
                    onChange={(e) => setVerForm((f) => ({ ...f, notes: e.target.value }))}
                    placeholder="Training cut-off, eval metrics, change log…"
                  />
                </label>
                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setShowVersionForm(false)}
                    className="rounded-lg px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    disabled={addVersionM.isPending || !verForm.version.trim()}
                    onClick={submitVersion}
                    className="rounded-lg bg-cyan-600 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {addVersionM.isPending ? "Saving…" : "Save version"}
                  </button>
                </div>
                {addVersionM.isError && (
                  <p className="text-xs text-red-700">
                    POST /internal/v1/ai/models/{"{id}"}/versions must be implemented on the BFF.
                  </p>
                )}
              </div>
            )}

            <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
              <table className="min-w-full text-sm">
                <thead className="border-b border-gray-100 bg-gray-50 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-4 py-3">Version</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Artifact</th>
                    <th className="px-4 py-3">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {versions.length === 0 && (
                    <tr>
                      <td colSpan={4} className="px-4 py-8 text-center text-gray-500">
                        No versions returned for this model.
                      </td>
                    </tr>
                  )}
                  {versions.map((v, i) => (
                    <tr key={readStr(v, "id", "versionId") || String(i)} className="hover:bg-gray-50/80">
                      <td className="px-4 py-3 font-medium">{readStr(v, "version", "label", "tag")}</td>
                      <td className="px-4 py-3">
                        <span className="text-xs text-gray-700">{readStr(v, "status", "state")}</span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600 truncate max-w-[200px]">
                        {readStr(v, "artifactUri", "artifact_uri", "uri")}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-600">{readStr(v, "createdAt", "created_at")}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="rounded-xl border border-emerald-100 bg-emerald-50/30 p-4">
                <h3 className="text-sm font-semibold text-emerald-900">Approved use cases</h3>
                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-gray-800">
                  {approvedUse.length === 0 && <li className="text-gray-500 list-none -ml-5">None listed.</li>}
                  {approvedUse.map((u) => (
                    <li key={u}>{u}</li>
                  ))}
                </ul>
              </div>
              <div className="rounded-xl border border-red-100 bg-red-50/30 p-4">
                <h3 className="text-sm font-semibold text-red-900">Prohibited use cases</h3>
                <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-gray-800">
                  {prohibitedUse.length === 0 && <li className="text-gray-500 list-none -ml-5">None listed.</li>}
                  {prohibitedUse.map((u) => (
                    <li key={u}>{u}</li>
                  ))}
                </ul>
              </div>
            </div>

            <div>
              <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-gray-900">
                <History className="h-4 w-4 text-gray-500" />
                Approval history
              </h2>
              <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
                <table className="min-w-full text-sm">
                  <thead className="border-b border-gray-100 bg-gray-50 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                    <tr>
                      <th className="px-4 py-3">When</th>
                      <th className="px-4 py-3">Actor</th>
                      <th className="px-4 py-3">Action</th>
                      <th className="px-4 py-3">Notes</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {approvalHistory.length === 0 && (
                      <tr>
                        <td colSpan={4} className="px-4 py-8 text-center text-gray-500">
                          No approval history in payload.
                        </td>
                      </tr>
                    )}
                    {approvalHistory.map((h, i) => (
                      <tr key={readStr(h, "id", "eventId") || String(i)}>
                        <td className="px-4 py-3 text-xs text-gray-600">
                          {readStr(h, "at", "timestamp", "createdAt")}
                        </td>
                        <td className="px-4 py-3">{readStr(h, "actor", "actorId", "by")}</td>
                        <td className="px-4 py-3 font-medium">{readStr(h, "action", "type", "status")}</td>
                        <td className="px-4 py-3 text-gray-600">{readStr(h, "notes", "comment", "reason")}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
