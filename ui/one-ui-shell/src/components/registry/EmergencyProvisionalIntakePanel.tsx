"use client";

import { useState } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useCreateClientRegistration } from "@/hooks/queries/useClientRegistry";
import { useCreateIntakeSession } from "@/hooks/queries/useRegistryIntake";

const inputClass =
  "w-full rounded-xl border border-border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500";

export function EmergencyProvisionalIntakePanel() {
  const router = useRouter();
  const createSession = useCreateIntakeSession();
  const createClient = useCreateClientRegistration();
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [form, setForm] = useState({
    firstName: "Unknown",
    lastName: "Emergency",
    sex: "UNKNOWN",
    triageNotes: "",
  });

  function startSession() {
    createSession.mutate(
      {
        intakeType: "CLIENT_EMERGENCY_PROVISIONAL",
        targetRegistry: "VITO",
        initiatedChannel: "FACILITY_DESK",
        provenance: "EMERGENCY_REGISTRATION",
        notes: "Emergency provisional intake started from client registration",
      },
      {
        onSuccess: (res) => {
          const id = (res as { data?: { id?: string } }).data?.id;
          if (id) setSessionId(id);
        },
      },
    );
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    createClient.mutate(
      {
        registrationType: "FACILITY_REGISTRATION",
        initiatedChannel: "FACILITY_DESK",
        firstName: form.firstName,
        lastName: form.lastName,
        sex: form.sex,
        notes: [form.triageNotes, sessionId ? `Intake session: ${sessionId}` : null].filter(Boolean).join(" · "),
        issueProvisionalIdentifier: true,
        provenance: {
          intakeMode: "EMERGENCY_PROVISIONAL",
          intakeSessionId: sessionId ?? undefined,
        },
      },
      {
        onSuccess: (response) => {
          router.push(`/registry/clients/${response.data.master.healthId}`);
        },
      },
    );
  }

  const busy = createSession.isPending || createClient.isPending;

  return (
    <section className="rounded-2xl border border-danger/28 bg-danger-soft/50 p-5" data-testid="emergency-provisional-intake-panel">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <AlertTriangle className="h-4 w-4 text-rose-600" />
        Emergency provisional intake
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        Minimal capture with provisional identifier for emergency care — intake session plus VITO registration via BFF.
      </p>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <button
          type="button"
          disabled={createSession.isPending || Boolean(sessionId)}
          onClick={startSession}
          className="rounded-lg border border-rose-300 bg-card px-3 py-1.5 text-xs font-medium text-danger hover:bg-rose-100 disabled:opacity-50"
        >
          {createSession.isPending ? "Starting session…" : sessionId ? "Session active" : "Start intake session"}
        </button>
        {sessionId ? <span className="font-mono text-xs text-rose-800">Session: {sessionId}</span> : null}
      </div>

      <form onSubmit={handleSubmit} className="mt-4 grid gap-3 md:grid-cols-2">
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-muted-foreground">Provisional first name</label>
          <input value={form.firstName} onChange={(e) => setForm((c) => ({ ...c, firstName: e.target.value }))} className={inputClass} />
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-muted-foreground">Provisional last name</label>
          <input value={form.lastName} onChange={(e) => setForm((c) => ({ ...c, lastName: e.target.value }))} className={inputClass} />
        </div>
        <div className="md:col-span-2">
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-muted-foreground">Triage / arrival notes</label>
          <textarea value={form.triageNotes} onChange={(e) => setForm((c) => ({ ...c, triageNotes: e.target.value }))} rows={2} className={inputClass} required />
        </div>
        <div className="md:col-span-2 flex justify-end">
          <button
            type="submit"
            disabled={busy}
            className="inline-flex items-center gap-2 rounded-xl bg-rose-700 px-4 py-2 text-sm font-medium text-white hover:bg-rose-800 disabled:opacity-70"
          >
            {createClient.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Issue provisional client
          </button>
        </div>
      </form>
    </section>
  );
}
