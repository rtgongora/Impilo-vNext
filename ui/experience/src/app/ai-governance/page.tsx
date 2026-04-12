"use client";

/**
 * AI Model Registry — Health Intelligence Plane (models, inference log, drift).
 * Route: /ai-governance
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  Activity,
  ArrowLeft,
  Bot,
  Cpu,
  Loader2,
  Plus,
  Radio,
  ShieldAlert,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAiDriftEvents,
  useAiInferenceRecords,
  useAiModels,
  useApproveAiModel,
  useRegisterAiModel,
  useWithdrawAiModel,
} from "@/hooks/queries/useAiGovernance";

type Tab = "models" | "inference" | "drift";

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

function readNum(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

const STATUS_BADGE: Record<string, string> = {
  APPROVED: "bg-emerald-100 text-emerald-800",
  PENDING: "bg-amber-100 text-amber-800",
  DRAFT: "bg-slate-100 text-slate-700",
  WITHDRAWN: "bg-gray-200 text-gray-600",
  REJECTED: "bg-red-100 text-red-800",
};

const SEVERITY_BADGE: Record<string, string> = {
  HIGH: "bg-red-100 text-red-800",
  MEDIUM: "bg-amber-100 text-amber-800",
  LOW: "bg-blue-100 text-blue-800",
};

export default function AiModelRegistryPage() {
  const [tab, setTab] = useState<Tab>("models");
  const [showRegister, setShowRegister] = useState(false);
  const [regForm, setRegForm] = useState({ name: "", description: "", type: "CLASSIFIER", owner: "" });

  const modelsQ = useAiModels(0, 100);
  const infQ = useAiInferenceRecords(0, 100);
  const driftQ = useAiDriftEvents(0, 100);
  const registerM = useRegisterAiModel();
  const approveM = useApproveAiModel();
  const withdrawM = useWithdrawAiModel();

  const models = useMemo(() => (modelsQ.data ?? []).map(asRecord), [modelsQ.data]);
  const inferences = useMemo(() => (infQ.data ?? []).map(asRecord), [infQ.data]);
  const drifts = useMemo(() => (driftQ.data ?? []).map(asRecord), [driftQ.data]);

  const submitRegister = () => {
    if (!regForm.name.trim()) return;
    registerM.mutate(
      {
        name: regForm.name.trim(),
        description: regForm.description.trim(),
        type: regForm.type,
        owner: regForm.owner.trim(),
      },
      {
        onSuccess: () => {
          setShowRegister(false);
          setRegForm({ name: "", description: "", type: "CLASSIFIER", owner: "" });
        },
      },
    );
  };

  const tabs: { id: Tab; label: string; icon: typeof Bot }[] = [
    { id: "models", label: "Models", icon: Bot },
    { id: "inference", label: "Inference Log", icon: Radio },
    { id: "drift", label: "Drift Alerts", icon: ShieldAlert },
  ];

  return (
    <AppLayout>
      <PageShell
        title="AI Model Registry"
        subtitle="Governed models, inference audit trail, and drift monitoring for the Health Intelligence Plane"
        icon={<Cpu className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link
            href="/access"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-800"
          >
            <ArrowLeft className="h-4 w-4" />
            Access &amp; governance
          </Link>
          <span className="text-gray-300">|</span>
          <Link href="/guidance" className="text-sm text-cyan-700 hover:underline">
            Clinical guidance hub
          </Link>
        </div>

        <div className="flex gap-1 mb-6 border-b border-gray-200 overflow-x-auto">
          {tabs.map((t) => {
            const Icon = t.icon;
            return (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                  tab === t.id
                    ? "border-cyan-600 text-cyan-700"
                    : "border-transparent text-gray-500 hover:text-gray-800"
                }`}
              >
                <Icon className="h-4 w-4" />
                {t.label}
              </button>
            );
          })}
        </div>

        {tab === "models" && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-gray-600">Registered models and approval posture.</p>
              <button
                type="button"
                onClick={() => setShowRegister((s) => !s)}
                className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-3 py-2 text-sm font-medium text-white hover:bg-cyan-700"
              >
                <Plus className="h-4 w-4" />
                Register Model
              </button>
            </div>

            {showRegister && (
              <div className="rounded-xl border-2 border-cyan-200 bg-cyan-50/40 p-4 space-y-3 text-sm">
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block">
                    <span className="text-xs font-medium text-gray-600">Name</span>
                    <input
                      className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2"
                      value={regForm.name}
                      onChange={(e) => setRegForm((f) => ({ ...f, name: e.target.value }))}
                      placeholder="e.g. edliz-triage-v2"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-medium text-gray-600">Type</span>
                    <select
                      className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2"
                      value={regForm.type}
                      onChange={(e) => setRegForm((f) => ({ ...f, type: e.target.value }))}
                    >
                      <option value="CLASSIFIER">Classifier</option>
                      <option value="REGRESSION">Regression</option>
                      <option value="LLM">LLM</option>
                      <option value="EMBEDDING">Embedding</option>
                    </select>
                  </label>
                </div>
                <label className="block">
                  <span className="text-xs font-medium text-gray-600">Owner</span>
                  <input
                    className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2"
                    value={regForm.owner}
                    onChange={(e) => setRegForm((f) => ({ ...f, owner: e.target.value }))}
                    placeholder="Team or actor id"
                  />
                </label>
                <label className="block">
                  <span className="text-xs font-medium text-gray-600">Description</span>
                  <textarea
                    className="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2"
                    rows={2}
                    value={regForm.description}
                    onChange={(e) => setRegForm((f) => ({ ...f, description: e.target.value }))}
                    placeholder="Purpose, training data summary, risk tier…"
                  />
                </label>
                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setShowRegister(false)}
                    className="rounded-lg px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-100"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    disabled={registerM.isPending || !regForm.name.trim()}
                    onClick={submitRegister}
                    className="rounded-lg bg-cyan-600 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                  >
                    {registerM.isPending ? "Saving…" : "Save"}
                  </button>
                </div>
                {registerM.isError && (
                  <p className="text-xs text-red-700">
                    Registration failed — ensure BFF exposes POST /internal/v1/ai/models.
                  </p>
                )}
              </div>
            )}

            <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
              {modelsQ.isLoading && (
                <div className="flex items-center justify-center gap-2 py-16 text-gray-500">
                  <Loader2 className="h-5 w-5 animate-spin" /> Loading models…
                </div>
              )}
              {modelsQ.isError && (
                <p className="p-6 text-sm text-red-700">
                  Could not load models. Wire GET /internal/v1/ai/models on the experience BFF.
                </p>
              )}
              {!modelsQ.isLoading && !modelsQ.isError && (
                <table className="min-w-full text-sm">
                  <thead className="border-b border-gray-100 bg-gray-50 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                    <tr>
                      <th className="px-4 py-3">Name</th>
                      <th className="px-4 py-3">Type</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Owner</th>
                      <th className="px-4 py-3 text-right">Versions</th>
                      <th className="px-4 py-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {models.length === 0 && (
                      <tr>
                        <td colSpan={6} className="px-4 py-10 text-center text-gray-500">
                          No models registered yet.
                        </td>
                      </tr>
                    )}
                    {models.map((row, i) => {
                      const id = readStr(row, "id", "modelId", "model_id") || String(i);
                      const status = readStr(row, "status", "approvalStatus").toUpperCase() || "DRAFT";
                      const versions = readNum(row, "versionsCount", "versionCount", "versions");
                      return (
                        <tr key={id} className="hover:bg-gray-50/80">
                          <td className="px-4 py-3">
                            <Link
                              href={`/ai-governance/models/${encodeURIComponent(id)}`}
                              className="font-medium text-cyan-800 hover:underline"
                            >
                              {readStr(row, "name", "modelName")}
                            </Link>
                          </td>
                          <td className="px-4 py-3 text-gray-700">{readStr(row, "type", "modelType")}</td>
                          <td className="px-4 py-3">
                            <span
                              className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[status] ?? "bg-gray-100 text-gray-700"}`}
                            >
                              {status || "—"}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-gray-600">{readStr(row, "owner", "ownerId")}</td>
                          <td className="px-4 py-3 text-right tabular-nums text-gray-800">{versions || "—"}</td>
                          <td className="px-4 py-3 text-right">
                            <div className="flex justify-end gap-2">
                              <button
                                type="button"
                                disabled={approveM.isPending}
                                onClick={() => approveM.mutate(id)}
                                className="rounded-md border border-emerald-200 bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-800 hover:bg-emerald-100 disabled:opacity-50"
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                disabled={withdrawM.isPending}
                                onClick={() => withdrawM.mutate(id)}
                                className="rounded-md border border-gray-200 bg-white px-2 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                              >
                                Withdraw
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        )}

        {tab === "inference" && (
          <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
            {infQ.isLoading && (
              <div className="flex items-center justify-center gap-2 py-16 text-gray-500">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading inference log…
              </div>
            )}
            {infQ.isError && (
              <p className="p-6 text-sm text-red-700">
                Could not load inference records. Wire GET /internal/v1/ai/inference-records.
              </p>
            )}
            {!infQ.isLoading && !infQ.isError && (
              <table className="min-w-full text-sm">
                <thead className="border-b border-gray-100 bg-gray-50 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-4 py-3">Model</th>
                    <th className="px-4 py-3">Version</th>
                    <th className="px-4 py-3">Class</th>
                    <th className="px-4 py-3">Workflow</th>
                    <th className="px-4 py-3">Subject</th>
                    <th className="px-4 py-3 text-right">Score</th>
                    <th className="px-4 py-3">Human review</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {inferences.length === 0 && (
                    <tr>
                      <td colSpan={7} className="px-4 py-10 text-center text-gray-500">
                        No inference records.
                      </td>
                    </tr>
                  )}
                  {inferences.map((row, i) => (
                    <tr key={readStr(row, "id", "recordId") || String(i)} className="hover:bg-gray-50/80">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {readStr(row, "model", "modelName", "modelId")}
                      </td>
                      <td className="px-4 py-3 text-gray-700">{readStr(row, "version", "modelVersion")}</td>
                      <td className="px-4 py-3">
                        <span className="rounded bg-indigo-50 px-2 py-0.5 text-xs font-mono text-indigo-800">
                          {readStr(row, "safetyClass", "class", "riskClass") || "—"}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600">
                        {readStr(row, "workflowRef", "workflow_ref", "workflowId")}
                      </td>
                      <td className="px-4 py-3 truncate max-w-[140px] text-gray-700">
                        {readStr(row, "subject", "subjectRef", "patientRef")}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums">{readNum(row, "score", "confidence")}</td>
                      <td className="px-4 py-3">
                        <span className="text-xs text-gray-700">
                          {readStr(row, "humanReviewStatus", "reviewStatus", "human_review")}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {tab === "drift" && (
          <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
            {driftQ.isLoading && (
              <div className="flex items-center justify-center gap-2 py-16 text-gray-500">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading drift events…
              </div>
            )}
            {driftQ.isError && (
              <p className="p-6 text-sm text-red-700">
                Could not load drift events. Wire GET /internal/v1/ai/drift-events.
              </p>
            )}
            {!driftQ.isLoading && !driftQ.isError && (
              <table className="min-w-full text-sm">
                <thead className="border-b border-gray-100 bg-gray-50 text-left text-xs font-semibold uppercase tracking-wide text-gray-500">
                  <tr>
                    <th className="px-4 py-3">Model</th>
                    <th className="px-4 py-3">Metric</th>
                    <th className="px-4 py-3">Value vs threshold</th>
                    <th className="px-4 py-3">Severity</th>
                    <th className="px-4 py-3">Detected</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {drifts.length === 0 && (
                    <tr>
                      <td colSpan={5} className="px-4 py-10 text-center text-gray-500">
                        No drift alerts.
                      </td>
                    </tr>
                  )}
                  {drifts.map((row, i) => {
                    const sev = readStr(row, "severity", "level").toUpperCase() || "LOW";
                    const val = readStr(row, "value", "metricValue");
                    const thr = readStr(row, "threshold", "thresholdValue");
                    return (
                      <tr key={readStr(row, "id", "eventId") || String(i)} className="hover:bg-gray-50/80">
                        <td className="px-4 py-3 font-medium">{readStr(row, "model", "modelName", "modelId")}</td>
                        <td className="px-4 py-3 text-gray-700">{readStr(row, "metric", "metricName")}</td>
                        <td className="px-4 py-3 font-mono text-xs text-gray-800">
                          {val || "—"}
                          {thr ? ` / ${thr}` : ""}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${SEVERITY_BADGE[sev] ?? "bg-gray-100 text-gray-700"}`}
                          >
                            {sev}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-gray-600 text-xs">
                          {readStr(row, "detectedAt", "detected_at", "createdAt")}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        )}

        <div className="mt-8 rounded-lg border border-dashed border-gray-200 bg-gray-50/80 p-4 text-xs text-gray-600">
          <p className="flex items-center gap-2 font-medium text-gray-800">
            <Activity className="h-4 w-4" />
            Dataset &amp; policy governance
          </p>
          <p className="mt-1">
            For governed datasets, ABAC rules, and policy publication, use the existing data-governance flows or extend
            the BFF under <code className="rounded bg-white px-1">/internal/v1/ai-governance/*</code> alongside this model
            registry.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
