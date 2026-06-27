"use client";

/**
 * Create Diagnostic Order — compose and submit a diagnostic/imaging order (criterion A).
 * Route: /diagnostics/orders/new | Zone: lab | Guard: facility
 *
 * Real write path: UI → BFF (/internal/v1/diagnostics/orders/draft + submit) → OROS → DB.
 */

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateDiagnosticOrder,
  type DiagnosticOrder,
} from "@/hooks/queries/useDiagnosticsOrders";

const ORDER_TYPES = ["IMAGING", "LAB", "PROCEDURE"] as const;
const MODALITIES = ["XR", "US", "CT", "MRI", "MG", "FL", "DEXA", "ECG", "ECHO", "ENDO", "OTHER"] as const;
const PRIORITIES = ["ROUTINE", "URGENT", "STAT", "ASAP"] as const;
const SPECIMEN_TYPES = ["blood", "serum", "plasma", "urine", "CSF", "sputum", "swab", "stool", "tissue"] as const;
const FASTING = ["UNKNOWN", "FASTING", "NON_FASTING"] as const;

export default function CreateDiagnosticOrderPage() {
  const [patientCpid, setPatientCpid] = useState("");
  const [orderType, setOrderType] = useState<string>("IMAGING");
  const [priority, setPriority] = useState<string>("ROUTINE");
  const [code, setCode] = useState("");
  const [modality, setModality] = useState<string>("XR");
  const [procedureCode, setProcedureCode] = useState("");
  const [specimenType, setSpecimenType] = useState<string>("blood");
  const [fasting, setFasting] = useState<string>("UNKNOWN");
  const [clinicalNotes, setClinicalNotes] = useState("");
  const [referringProviderId, setReferringProviderId] = useState("");
  const [created, setCreated] = useState<DiagnosticOrder | null>(null);

  const createMut = useCreateDiagnosticOrder();
  const isImaging = orderType === "IMAGING";
  const isLab = orderType === "LAB";
  const canSubmit = patientCpid.trim().length > 0 && code.trim().length > 0 && !createMut.isPending;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setCreated(null);
    createMut.mutate(
      {
        patientCpid: patientCpid.trim(),
        orderType,
        priority,
        clinicalNotes:
          (isLab && fasting !== "UNKNOWN" ? `Fasting: ${fasting}. ` : "") +
            (clinicalNotes.trim() || "") || undefined,
        referringProviderId: referringProviderId.trim() || undefined,
        items: [
          {
            code: code.trim(),
            modality: isImaging ? modality : undefined,
            procedureCode: isImaging && procedureCode.trim() ? procedureCode.trim() : undefined,
            specimenType: isLab ? specimenType : undefined,
          },
        ],
      },
      { onSuccess: (order) => setCreated(order) },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Create Diagnostic Order" subtitle="Compose and submit a diagnostic or imaging order">
        <form onSubmit={submit} className="max-w-2xl space-y-4">
          <Field label="Patient CPID" required>
            <input value={patientCpid} onChange={(e) => setPatientCpid(e.target.value)}
              className="impilo-pill-input w-full" aria-label="Patient CPID" placeholder="CPID-…" />
          </Field>

          <div className="grid grid-cols-2 gap-4">
            <Field label="Order type">
              <select value={orderType} onChange={(e) => setOrderType(e.target.value)} className="impilo-pill-input w-full" aria-label="Order type">
                {ORDER_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            <Field label="Priority">
              <select value={priority} onChange={(e) => setPriority(e.target.value)} className="impilo-pill-input w-full" aria-label="Priority">
                {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
              </select>
            </Field>
          </div>

          <Field label="Order/procedure code" required>
            <input value={code} onChange={(e) => setCode(e.target.value)} className="impilo-pill-input w-full"
              aria-label="Order code" placeholder="e.g. CHEST-XR" />
          </Field>

          {isImaging && (
            <div className="grid grid-cols-2 gap-4">
              <Field label="Modality">
                <select value={modality} onChange={(e) => setModality(e.target.value)} className="impilo-pill-input w-full" aria-label="Modality">
                  {MODALITIES.map((m) => <option key={m} value={m}>{m}</option>)}
                </select>
              </Field>
              <Field label="Procedure code (optional)">
                <input value={procedureCode} onChange={(e) => setProcedureCode(e.target.value)} className="impilo-pill-input w-full" aria-label="Procedure code" />
              </Field>
            </div>
          )}

          {isLab && (
            <div className="grid grid-cols-2 gap-4">
              <Field label="Specimen type">
                <select value={specimenType} onChange={(e) => setSpecimenType(e.target.value)} className="impilo-pill-input w-full" aria-label="Specimen type">
                  {SPECIMEN_TYPES.map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </Field>
              <Field label="Fasting status">
                <select value={fasting} onChange={(e) => setFasting(e.target.value)} className="impilo-pill-input w-full" aria-label="Fasting status">
                  {FASTING.map((f) => <option key={f} value={f}>{f}</option>)}
                </select>
              </Field>
            </div>
          )}

          <Field label="Referring provider ID (optional)">
            <input value={referringProviderId} onChange={(e) => setReferringProviderId(e.target.value)} className="impilo-pill-input w-full" aria-label="Referring provider ID" />
          </Field>

          <Field label="Indication / clinical notes">
            <textarea value={clinicalNotes} onChange={(e) => setClinicalNotes(e.target.value)}
              className="impilo-pill-input w-full min-h-[80px] rounded-2xl py-2" aria-label="Clinical notes" />
          </Field>

          <div className="flex items-center gap-3">
            <button type="submit" disabled={!canSubmit}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50">
              {createMut.isPending ? (
                <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Submitting…</span>
              ) : "Submit order"}
            </button>
            {createMut.isError && (
              <span className="text-sm text-danger" role="alert">Failed to submit order. Please retry.</span>
            )}
          </div>
        </form>

        {created && (
          <div className="mt-6 max-w-2xl rounded-lg border border-success/40 bg-success-soft p-4 text-sm" role="status">
            <p className="font-medium">Order submitted</p>
            <p className="text-muted-foreground">
              Order <span className="font-mono">{created.orderId}</span>
              {created.accessionNumber ? <> · accession <span className="font-mono">{created.accessionNumber}</span></> : null}
              {created.imagingState ? <> · {created.imagingState}</> : null}
            </p>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium">
        {label}{required ? <span className="text-danger"> *</span> : null}
      </span>
      {children}
    </label>
  );
}
