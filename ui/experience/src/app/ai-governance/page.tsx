"use client";

/**
 * AI Governance — Dataset registry, rules, access decisioning, policy, and audit.
 * Route: /ai-governance
 *
 * Bridges to data-governance-service via experience-bff.
 */

import { useState } from "react";
import {
  Shield, Database, Scale, BookOpen, ClipboardList, Plus, Loader2, X, Trash2, CheckCircle, XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type TabId = "datasets" | "rules" | "decisions" | "policy" | "audit";

export default function AiGovernancePage() {
  const [tab, setTab] = useState<TabId>("datasets");

  const tabs: { id: TabId; label: string; icon: typeof Database }[] = [
    { id: "datasets", label: "Datasets", icon: Database },
    { id: "rules", label: "Rules", icon: Scale },
    { id: "decisions", label: "Decisions", icon: Shield },
    { id: "policy", label: "Policy", icon: BookOpen },
    { id: "audit", label: "Audit", icon: ClipboardList },
  ];

  return (
    <AppLayout>
      <PageShell title="AI Governance" icon={<Shield className="w-5 h-5" />}>
        <p className="text-sm text-gray-500 mb-4">Manage data governance controls, access rules, and AI operational oversight</p>

        {/* Overview cards */}
        <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-6">
          {tabs.map((t) => {
            const Icon = t.icon;
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                className={`p-4 rounded-lg border text-left transition-all ${tab === t.id ? "border-blue-500 bg-blue-50" : "border-gray-200 bg-white hover:border-gray-300"}`}>
                <Icon className={`w-5 h-5 mb-2 ${tab === t.id ? "text-blue-600" : "text-gray-400"}`} />
                <p className={`text-sm font-medium ${tab === t.id ? "text-blue-700" : "text-gray-700"}`}>{t.label}</p>
              </button>
            );
          })}
        </div>

        {tab === "datasets" && <DatasetsTab />}
        {tab === "rules" && <RulesTab />}
        {tab === "decisions" && <DecisionsTab />}
        {tab === "policy" && <PolicyTab />}
        {tab === "audit" && <AuditTab />}
      </PageShell>
    </AppLayout>
  );
}

// ── Datasets Tab ────────────────────────────────────────────────────

function DatasetsTab() {
  const queryClient = useQueryClient();
  const { data: datasets = [], isLoading } = useQuery<Array<Record<string, unknown>>>({
    queryKey: ["gov-datasets"],
    queryFn: () => apiClient.get("/internal/v1/ai-governance/datasets"),
  });
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: "", classification: "INTERNAL", description: "" });

  const createMutation = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/ai-governance/datasets", body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["gov-datasets"] }); setShowCreate(false); setForm({ name: "", classification: "INTERNAL", description: "" }); },
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Governed Datasets</h3>
        <button onClick={() => setShowCreate(!showCreate)} className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          <Plus className="w-4 h-4" /> Register Dataset
        </button>
      </div>

      {showCreate && (
        <div className="bg-white rounded-lg border-2 border-blue-200 p-4 space-y-3">
          <input type="text" placeholder="Dataset name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
          <select value={form.classification} onChange={(e) => setForm({ ...form, classification: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm">
            <option value="PUBLIC">Public</option>
            <option value="INTERNAL">Internal</option>
            <option value="CONFIDENTIAL">Confidential</option>
            <option value="RESTRICTED">Restricted</option>
          </select>
          <textarea placeholder="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={2} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
          <div className="flex justify-end gap-2">
            <button onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm text-gray-600">Cancel</button>
            <button onClick={() => createMutation.mutate(form)} disabled={!form.name || createMutation.isPending} className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg disabled:opacity-50">
              {createMutation.isPending ? "Registering..." : "Register"}
            </button>
          </div>
        </div>
      )}

      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr><th className="px-4 py-3 text-left">Name</th><th className="px-4 py-3 text-left">Classification</th><th className="px-4 py-3 text-left">Description</th><th className="px-4 py-3 text-left">Created</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {(datasets as Array<Record<string, string>>).map((d) => (
                <tr key={d.datasetId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-900">{d.name}</td>
                  <td className="px-4 py-3"><ClassBadge value={d.classification} /></td>
                  <td className="px-4 py-3 text-gray-500 truncate max-w-xs">{d.description || "—"}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{d.createdAt ? new Date(d.createdAt).toLocaleDateString() : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {datasets.length === 0 && <p className="text-center py-8 text-gray-400">No datasets registered</p>}
        </div>
      )}
    </div>
  );
}

// ── Rules Tab ───────────────────────────────────────────────────────

function RulesTab() {
  const queryClient = useQueryClient();
  const { data: rules = [], isLoading } = useQuery<Array<Record<string, unknown>>>({
    queryKey: ["gov-rules"],
    queryFn: () => apiClient.get("/internal/v1/ai-governance/rules"),
  });
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: "", description: "", resourcePattern: "*", action: "EXPORT", effect: "DENY", requiredPurpose: "", priority: "100" });

  const createMutation = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/ai-governance/rules", body),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["gov-rules"] }); setShowCreate(false); },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/internal/v1/ai-governance/rules/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["gov-rules"] }),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Governance Rules</h3>
        <button onClick={() => setShowCreate(!showCreate)} className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700">
          <Plus className="w-4 h-4" /> Create Rule
        </button>
      </div>

      {showCreate && (
        <div className="bg-white rounded-lg border-2 border-blue-200 p-4 space-y-3">
          <input type="text" placeholder="Rule name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
          <textarea placeholder="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={2} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
          <div className="grid grid-cols-2 gap-3">
            <input type="text" placeholder="Resource pattern (e.g., patient.*)" value={form.resourcePattern} onChange={(e) => setForm({ ...form, resourcePattern: e.target.value })} className="px-3 py-2 border border-gray-300 rounded-lg text-sm" />
            <input type="text" placeholder="Required purpose" value={form.requiredPurpose} onChange={(e) => setForm({ ...form, requiredPurpose: e.target.value })} className="px-3 py-2 border border-gray-300 rounded-lg text-sm" />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <select value={form.action} onChange={(e) => setForm({ ...form, action: e.target.value })} className="px-3 py-2 border border-gray-300 rounded-lg text-sm">
              <option value="EXPORT">Export</option><option value="READ">Read</option><option value="WRITE">Write</option><option value="DELETE">Delete</option>
            </select>
            <select value={form.effect} onChange={(e) => setForm({ ...form, effect: e.target.value })} className="px-3 py-2 border border-gray-300 rounded-lg text-sm">
              <option value="DENY">Deny</option><option value="ALLOW">Allow</option>
            </select>
            <input type="number" placeholder="Priority" value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })} className="px-3 py-2 border border-gray-300 rounded-lg text-sm" />
          </div>
          <div className="flex justify-end gap-2">
            <button onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm text-gray-600">Cancel</button>
            <button onClick={() => createMutation.mutate({ ...form, priority: Number(form.priority) })} disabled={!form.name || createMutation.isPending} className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg disabled:opacity-50">Create</button>
          </div>
        </div>
      )}

      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : (
        <div className="space-y-2">
          {(rules as Array<Record<string, unknown>>).map((r) => (
            <div key={r.ruleId as string} className="bg-white rounded-lg border border-gray-200 p-4 flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-gray-900">{r.name as string}</p>
                  <span className={`px-2 py-0.5 text-xs rounded ${r.effect === "DENY" ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"}`}>{r.effect as string}</span>
                  <span className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">{r.action as string}</span>
                </div>
                {r.description && <p className="text-sm text-gray-500 mt-1">{r.description as string}</p>}
                <p className="text-xs text-gray-400 mt-1">Pattern: {r.resourcePattern as string} · Priority: {String(r.priority)} {r.requiredPurpose ? `· Purpose: ${r.requiredPurpose}` : ""}</p>
              </div>
              <button onClick={() => deleteMutation.mutate(r.ruleId as string)} className="p-1.5 text-gray-400 hover:text-red-500" title="Deactivate">
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
          {rules.length === 0 && <p className="text-center py-8 text-gray-400">No active rules</p>}
        </div>
      )}
    </div>
  );
}

// ── Decisions Tab ───────────────────────────────────────────────────

function DecisionsTab() {
  const [form, setForm] = useState({ principalId: "", dataset: "", purposeOfUse: "" });
  const [result, setResult] = useState<Record<string, unknown> | null>(null);

  const decideMutation = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/ai-governance/decide", body),
    onSuccess: (data) => setResult(data as Record<string, unknown>),
  });

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Access Decisioning</h3>
      <p className="text-sm text-gray-500">Evaluate whether a principal can access a governed dataset for a given purpose.</p>

      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <input type="text" placeholder="Principal ID (user or service)" value={form.principalId} onChange={(e) => setForm({ ...form, principalId: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        <input type="text" placeholder="Dataset name" value={form.dataset} onChange={(e) => setForm({ ...form, dataset: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        <input type="text" placeholder="Purpose of use (e.g., TREATMENT, RESEARCH)" value={form.purposeOfUse} onChange={(e) => setForm({ ...form, purposeOfUse: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        <button onClick={() => decideMutation.mutate(form)} disabled={!form.principalId || !form.dataset || !form.purposeOfUse || decideMutation.isPending}
          className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg disabled:opacity-50">
          {decideMutation.isPending ? "Evaluating..." : "Evaluate Access"}
        </button>
      </div>

      {result && (
        <div className={`rounded-lg border-2 p-4 ${(result as Record<string, unknown>).decision === "ALLOW" ? "border-green-300 bg-green-50" : "border-red-300 bg-red-50"}`}>
          <div className="flex items-center gap-2 mb-2">
            {(result as Record<string, unknown>).decision === "ALLOW"
              ? <CheckCircle className="w-5 h-5 text-green-600" />
              : <XCircle className="w-5 h-5 text-red-600" />}
            <span className="text-lg font-bold">{(result as Record<string, unknown>).decision as string}</span>
          </div>
          <pre className="text-xs text-gray-600 whitespace-pre-wrap">{JSON.stringify(result, null, 2)}</pre>
        </div>
      )}
    </div>
  );
}

// ── Policy Tab ──────────────────────────────────────────────────────

function PolicyTab() {
  const [form, setForm] = useState({ name: "", version: "", description: "" });
  const [success, setSuccess] = useState(false);

  const publishMutation = useMutation({
    mutationFn: (body: Record<string, string>) => apiClient.post("/internal/v1/ai-governance/policies", body),
    onSuccess: () => { setSuccess(true); setForm({ name: "", version: "", description: "" }); },
  });

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Policy Publication</h3>
      <p className="text-sm text-gray-500">Publish governance policy versions for enforcement across the platform.</p>

      <div className="bg-white rounded-lg border border-gray-200 p-4 space-y-3">
        <input type="text" placeholder="Policy name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        <input type="text" placeholder="Version (e.g., 1.0.0)" value={form.version} onChange={(e) => setForm({ ...form, version: e.target.value })} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        <textarea placeholder="Policy description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={3} className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm" />
        <button onClick={() => { setSuccess(false); publishMutation.mutate(form); }} disabled={!form.name || !form.version || publishMutation.isPending}
          className="px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg disabled:opacity-50">
          {publishMutation.isPending ? "Publishing..." : "Publish Policy"}
        </button>
      </div>

      {success && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 flex items-center gap-2">
          <CheckCircle className="w-5 h-5 text-green-600" />
          <span className="text-sm text-green-700">Policy published successfully</span>
        </div>
      )}
    </div>
  );
}

// ── Audit Tab ───────────────────────────────────────────────────────

function AuditTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({
    queryKey: ["gov-audit"],
    queryFn: () => apiClient.get("/internal/v1/ai-governance/audit"),
  });
  const entries = data?.data ?? [];

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Governance Audit Log</h3>
      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : entries.length === 0 ? (
        <div className="text-center py-12 bg-gray-50 rounded-lg border border-gray-200">
          <ClipboardList className="w-10 h-10 text-gray-300 mx-auto mb-3" />
          <p className="text-sm text-gray-500">No audit entries yet</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
              <tr><th className="px-4 py-3 text-left">Action</th><th className="px-4 py-3 text-left">User</th><th className="px-4 py-3 text-left">Target</th><th className="px-4 py-3 text-left">Timestamp</th></tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {entries.map((e, i) => (
                <tr key={i} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-900">{(e.action ?? e.event_type ?? "—") as string}</td>
                  <td className="px-4 py-3 text-gray-600">{(e.performed_by ?? e.user_id ?? "—") as string}</td>
                  <td className="px-4 py-3 text-gray-500 truncate max-w-xs">{(e.target ?? e.resource ?? "—") as string}</td>
                  <td className="px-4 py-3 text-gray-400 text-xs">{e.created_at ? new Date(e.created_at as string).toLocaleString() : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

// ── Helpers ──────────────────────────────────────────────────────────

function ClassBadge({ value }: { value: string }) {
  const colors: Record<string, string> = {
    PUBLIC: "bg-green-100 text-green-700",
    INTERNAL: "bg-blue-100 text-blue-700",
    CONFIDENTIAL: "bg-orange-100 text-orange-700",
    RESTRICTED: "bg-red-100 text-red-700",
  };
  return <span className={`px-2 py-0.5 text-xs font-medium rounded ${colors[value] ?? "bg-gray-100 text-gray-600"}`}>{value}</span>;
}
