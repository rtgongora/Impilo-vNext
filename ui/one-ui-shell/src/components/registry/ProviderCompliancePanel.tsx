"use client";

import { useState } from "react";
import { ShieldAlert, Loader2, Plus, Gavel } from "lucide-react";
import {
  useProviderCompliance,
  useCreateComplianceAction,
  useComplianceOp,
  useProviderDisciplinary,
  useOpenDisciplinaryCase,
  useRecordDisciplinaryAction,
  useCloseDisciplinaryCase,
} from "@/hooks/queries/useProviderCompliance";

const COMPLIANCE_TYPES = ["CPD_REQUIRED", "GOOD_STANDING_REQUIRED", "MISSING_DOCUMENT", "VERIFICATION_PENDING", "OTHER"];
const TRIGGERS = ["COMPLAINT", "REGULATORY_BREACH", "LICENCE_NONCOMPLIANCE", "MISREPRESENTATION", "OTHER"];

const STATUS_STYLE: Record<string, string> = {
  OPEN: "bg-amber-100 text-amber-700",
  FULFILLED: "bg-green-100 text-green-700",
  EXEMPTED: "bg-neutral-100 text-neutral-600",
  CLOSED: "bg-neutral-100 text-neutral-600",
};

/** Registrar compliance actions + disciplinary casework for a provider (R2/G9). */
export function ProviderCompliancePanel({ providerPublicId }: { providerPublicId: string }) {
  const compliance = useProviderCompliance(providerPublicId);
  const createAction = useCreateComplianceAction(providerPublicId);
  const complianceOp = useComplianceOp(providerPublicId);
  const disciplinary = useProviderDisciplinary(providerPublicId);
  const openCase = useOpenDisciplinaryCase(providerPublicId);
  const recordAction = useRecordDisciplinaryAction(providerPublicId);
  const closeCase = useCloseDisciplinaryCase(providerPublicId);

  const [showCompliance, setShowCompliance] = useState(false);
  const [compForm, setCompForm] = useState({ complianceActionType: COMPLIANCE_TYPES[0], description: "", dueDate: "" });
  const [showCase, setShowCase] = useState(false);
  const [caseForm, setCaseForm] = useState({ triggerType: TRIGGERS[0], summary: "" });

  return (
    <div className="bg-card rounded-xl border border-border shadow-sm">
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
        <ShieldAlert className="h-4 w-4 text-primary" />
        <h3 className="text-sm font-semibold text-foreground">Compliance &amp; disciplinary</h3>
      </div>

      {/* Compliance actions */}
      <div className="p-4 border-b border-border">
        <div className="mb-2 flex items-center justify-between">
          <p className="text-xs font-semibold uppercase text-muted-foreground">Compliance actions</p>
          <button type="button" onClick={() => setShowCompliance((s) => !s)}
            className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs hover:bg-background">
            <Plus className="h-3 w-3" /> Add
          </button>
        </div>
        {showCompliance && (
          <div className="mb-3 space-y-2 rounded-lg border border-border bg-background p-2">
            <select value={compForm.complianceActionType} onChange={(e) => setCompForm({ ...compForm, complianceActionType: e.target.value })}
              className="w-full rounded border border-border px-2 py-1.5 text-sm bg-card">
              {COMPLIANCE_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
            </select>
            <input value={compForm.description} onChange={(e) => setCompForm({ ...compForm, description: e.target.value })}
              placeholder="Description" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
            <label className="block text-xs text-muted-foreground">Due date
              <input type="date" value={compForm.dueDate} onChange={(e) => setCompForm({ ...compForm, dueDate: e.target.value })}
                className="mt-0.5 w-full rounded border border-border px-2 py-1.5 text-sm" />
            </label>
            <button type="button" disabled={createAction.isPending}
              onClick={() => createAction.mutate(
                { complianceActionType: compForm.complianceActionType, description: compForm.description || undefined, dueDate: compForm.dueDate || undefined },
                { onSuccess: () => { setShowCompliance(false); setCompForm({ complianceActionType: COMPLIANCE_TYPES[0], description: "", dueDate: "" }); } })}
              className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">Create</button>
          </div>
        )}
        {compliance.isPending ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : (compliance.data?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground">No compliance actions.</p>
        ) : (
          <ul className="space-y-1.5">
            {compliance.data!.map((a) => {
              const status = String(a.status ?? "").toUpperCase();
              return (
                <li key={a.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="min-w-0 truncate">{(a.actionType ?? "Action").replace(/_/g, " ")}{a.dueDate ? ` · due ${new Date(a.dueDate).toLocaleDateString()}` : ""}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? "bg-neutral-100 text-neutral-600"}`}>{status || "—"}</span>
                    {status === "OPEN" && (
                      <button type="button" onClick={() => complianceOp.mutate({ actionId: a.id, op: "fulfil", body: { fulfilledDate: new Date().toISOString().slice(0, 10) } })}
                        className="rounded border border-border px-2 py-1 text-xs hover:bg-background">Fulfil</button>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* Disciplinary cases */}
      <div className="p-4">
        <div className="mb-2 flex items-center justify-between">
          <p className="flex items-center gap-1 text-xs font-semibold uppercase text-muted-foreground"><Gavel className="h-3 w-3" /> Disciplinary cases</p>
          <button type="button" onClick={() => setShowCase((s) => !s)}
            className="inline-flex items-center gap-1 rounded-lg border border-border px-2 py-1 text-xs hover:bg-background">
            <Plus className="h-3 w-3" /> Open case
          </button>
        </div>
        {showCase && (
          <div className="mb-3 space-y-2 rounded-lg border border-border bg-background p-2">
            <select value={caseForm.triggerType} onChange={(e) => setCaseForm({ ...caseForm, triggerType: e.target.value })}
              className="w-full rounded border border-border px-2 py-1.5 text-sm bg-card">
              {TRIGGERS.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
            </select>
            <textarea value={caseForm.summary} onChange={(e) => setCaseForm({ ...caseForm, summary: e.target.value })} rows={2}
              placeholder="Case summary" className="w-full rounded border border-border px-2 py-1.5 text-sm" />
            <button type="button" disabled={openCase.isPending}
              onClick={() => openCase.mutate({ triggerType: caseForm.triggerType, summary: caseForm.summary || undefined },
                { onSuccess: () => { setShowCase(false); setCaseForm({ triggerType: TRIGGERS[0], summary: "" }); } })}
              className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50">Open case</button>
          </div>
        )}
        {disciplinary.isPending ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</div>
        ) : (disciplinary.data?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground">No disciplinary cases.</p>
        ) : (
          <ul className="space-y-1.5">
            {disciplinary.data!.map((c) => {
              const status = String(c.status ?? "").toUpperCase();
              return (
                <li key={c.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="min-w-0 truncate">{(c.triggerType ?? "Case").replace(/_/g, " ")}{c.openedDate ? ` · ${new Date(c.openedDate).toLocaleDateString()}` : ""}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? "bg-neutral-100 text-neutral-600"}`}>{status || "—"}</span>
                    {status === "OPEN" && (
                      <>
                        <button type="button" onClick={() => recordAction.mutate({ caseId: c.id, body: { actionType: "SUSPENSION", severity: "HIGH", description: "Interim suspension" } })}
                          className="rounded border border-red-300 px-2 py-1 text-xs text-red-700 hover:bg-red-50">Suspend</button>
                        <button type="button" onClick={() => closeCase.mutate({ caseId: c.id, body: { finalDecision: "RESOLVED" } })}
                          className="rounded border border-border px-2 py-1 text-xs hover:bg-background">Close</button>
                      </>
                    )}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
