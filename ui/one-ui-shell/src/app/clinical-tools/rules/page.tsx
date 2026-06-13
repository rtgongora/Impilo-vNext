"use client";

/**
 * Extension rules engine — list, create, evaluate test facts.
 * Route: /clinical-tools/rules
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  Braces,
  ClipboardCheck,
  Loader2,
  Plus,
  Scale,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateExtensionRule,
  useEvaluateExtensionRule,
  useExtensionRules,
} from "@/hooks/queries/useClinicalExtensions";

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

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-primary-hover",
  DRAFT: "bg-neutral-100 text-foreground",
  DISABLED: "bg-neutral-100 text-muted-foreground",
};

export default function ClinicalRulesEnginePage() {
  const rulesQ = useExtensionRules(0, 100);
  const createM = useCreateExtensionRule();
  const evalM = useEvaluateExtensionRule();

  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState({
    name: "",
    condition: "",
    action: "",
    status: "DRAFT",
  });

  const [selectedRuleId, setSelectedRuleId] = useState("");
  const [factsJson, setFactsJson] = useState('{\n  "bpSystolic": 142,\n  "hasAllergy": false\n}');
  const [evalResult, setEvalResult] = useState<string>("");

  const rules = useMemo(() => (rulesQ.data ?? []).map(asRecord), [rulesQ.data]);

  const submitCreate = () => {
    if (!createForm.name.trim()) return;
    createM.mutate(
      {
        name: createForm.name.trim(),
        condition: createForm.condition.trim(),
        action: createForm.action.trim(),
        status: createForm.status,
      },
      {
        onSuccess: () => {
          setShowCreate(false);
          setCreateForm({ name: "", condition: "", action: "", status: "DRAFT" });
        },
      },
    );
  };

  const runEvaluate = () => {
    if (!selectedRuleId) {
      setEvalResult("Select a rule id from the table (or paste id).");
      return;
    }
    let facts: Record<string, unknown>;
    try {
      facts = JSON.parse(factsJson) as Record<string, unknown>;
    } catch {
      setEvalResult("Invalid JSON in facts panel.");
      return;
    }
    setEvalResult("");
    evalM.mutate(
      { ruleId: selectedRuleId, facts },
      {
        onSuccess: (res) => {
          setEvalResult(JSON.stringify(res, null, 2));
        },
        onError: (e) => {
          setEvalResult(String(e));
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Rules engine"
        subtitle="Governed clinical extension rules — define conditions, actions, and dry-run evaluation"
        icon={<Scale className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/clinical-tools"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Clinical tools
          </Link>
          <span className="mx-2 text-muted-foreground">·</span>
          <Link href="/clinical-tools/forms" className="text-sm text-cyan-700 hover:underline">
            Form schema builder
          </Link>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <p className="text-sm text-muted-foreground">Backed by GET/POST /internal/v1/extensions/rules and evaluate endpoint.</p>
          <button
            type="button"
            onClick={() => setShowCreate((s) => !s)}
            className="inline-flex items-center gap-2 rounded-lg bg-pink-600 px-3 py-2 text-sm font-medium text-white hover:bg-pink-700"
          >
            <Plus className="h-4 w-4" />
            Create Rule
          </button>
        </div>

        {showCreate && (
          <div className="mb-6 rounded-xl border-2 border-pink-200 bg-pink-50/40 p-4 space-y-3 text-sm">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block sm:col-span-2">
                <span className="text-xs font-medium text-muted-foreground">Name</span>
                <input
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                  value={createForm.name}
                  onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))}
                  placeholder="Hypertension escalation"
                />
              </label>
              <label className="block sm:col-span-2">
                <span className="text-xs font-medium text-muted-foreground">Condition</span>
                <textarea
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
                  rows={2}
                  value={createForm.condition}
                  onChange={(e) => setCreateForm((f) => ({ ...f, condition: e.target.value }))}
                  placeholder="Expression or DSL the engine understands"
                />
              </label>
              <label className="block sm:col-span-2">
                <span className="text-xs font-medium text-muted-foreground">Action</span>
                <textarea
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
                  rows={2}
                  value={createForm.action}
                  onChange={(e) => setCreateForm((f) => ({ ...f, action: e.target.value }))}
                  placeholder="e.g. CDS_ALERT / severity=critical"
                />
              </label>
              <label className="block">
                <span className="text-xs font-medium text-muted-foreground">Status</span>
                <select
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                  value={createForm.status}
                  onChange={(e) => setCreateForm((f) => ({ ...f, status: e.target.value }))}
                >
                  <option value="DRAFT">DRAFT</option>
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="DISABLED">DISABLED</option>
                </select>
              </label>
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowCreate(false)}
                className="rounded-lg px-3 py-1.5 text-sm text-muted-foreground hover:bg-neutral-100"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={createM.isPending || !createForm.name.trim()}
                onClick={submitCreate}
                className="rounded-lg bg-pink-600 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {createM.isPending ? "Saving…" : "Save rule"}
              </button>
            </div>
            {createM.isError && (
              <p className="text-xs text-danger">
                Create failed — BFF may not expose POST /internal/v1/extensions/rules yet.
              </p>
            )}
          </div>
        )}

        <div className="grid gap-6 lg:grid-cols-5">
          <div className="lg:col-span-3 overflow-hidden rounded-xl border border-border bg-card shadow-sm">
            {rulesQ.isLoading && (
              <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading rules…
              </div>
            )}
            {rulesQ.isError && (
              <p className="p-6 text-sm text-danger">Could not load rules from the BFF.</p>
            )}
            {!rulesQ.isLoading && !rulesQ.isError && (
              <table className="min-w-full text-sm">
                <thead className="border-b border-border bg-background text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3">Name</th>
                    <th className="px-4 py-3">Condition</th>
                    <th className="px-4 py-3">Action</th>
                    <th className="px-4 py-3">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {rules.length === 0 && (
                    <tr>
                      <td colSpan={4} className="px-4 py-10 text-center text-muted-foreground">
                        No rules returned.
                      </td>
                    </tr>
                  )}
                  {rules.map((r, i) => {
                    const id = readStr(r, "id", "ruleId", "rule_id") || String(i);
                    const st = readStr(r, "status", "state").toUpperCase() || "DRAFT";
                    return (
                      <tr
                        key={id}
                        className={`cursor-pointer hover:bg-pink-50/50 ${selectedRuleId === id ? "bg-pink-50/80" : ""}`}
                        onClick={() => setSelectedRuleId(id)}
                      >
                        <td className="px-4 py-3 font-medium text-foreground">{readStr(r, "name", "title")}</td>
                        <td className="px-4 py-3 text-xs text-muted-foreground max-w-[180px] truncate">
                          {readStr(r, "condition", "when", "predicate")}
                        </td>
                        <td className="px-4 py-3 text-xs text-muted-foreground max-w-[180px] truncate">
                          {readStr(r, "action", "then", "outcome")}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[st] ?? "bg-neutral-100 text-foreground"}`}
                          >
                            {st}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>

          <div className="lg:col-span-2 space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <ClipboardCheck className="h-4 w-4 text-pink-600" />
              Evaluate rule (test)
            </h3>
            <label className="block text-xs font-medium text-muted-foreground">
              Rule id
              <input
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
                value={selectedRuleId}
                onChange={(e) => setSelectedRuleId(e.target.value)}
                placeholder="Click a row or paste id"
              />
            </label>
            <label className="block text-xs font-medium text-muted-foreground">
              Facts (JSON)
              <textarea
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
                rows={10}
                value={factsJson}
                onChange={(e) => setFactsJson(e.target.value)}
              />
            </label>
            <button
              type="button"
              disabled={evalM.isPending}
              onClick={runEvaluate}
              className="w-full rounded-lg bg-neutral-900 px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
            >
              {evalM.isPending ? "Evaluating…" : "Run evaluation"}
            </button>
            {(evalResult || evalM.isError) && (
              <div className="rounded-lg border border-border bg-background p-3">
                <p className="mb-1 flex items-center gap-1 text-xs font-medium text-muted-foreground">
                  <Braces className="h-3 w-3" /> Result
                </p>
                <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-all text-xs text-foreground">
                  {evalM.isError ? String(evalM.error) : evalResult}
                </pre>
              </div>
            )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
