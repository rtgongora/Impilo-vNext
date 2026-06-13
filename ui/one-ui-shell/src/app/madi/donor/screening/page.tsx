"use client";

import Link from "next/link";
import { useState } from "react";
import { ClipboardCheck, Loader2, AlertTriangle, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { MadiDonorAssistPanel } from "@/components/madi/MadiDonorAssistPanel";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDonorByPerson, useSubmitDonorPreScreening } from "@/hooks/queries/useMadi";

const QUESTIONS = [
  { id: "recent_illness", label: "Have you felt unwell in the last 7 days?" },
  { id: "recent_travel_malaria", label: "Travelled to a malaria area recently?" },
  { id: "low_hemoglobin_symptoms", label: "Dizziness, unusual tiredness, or breathlessness?" },
  { id: "recent_tattoo", label: "Tattoo or piercing in the last 3 months?" },
  { id: "on_antibiotics", label: "Taking antibiotics now?" },
  { id: "pregnant_or_recent_birth", label: "Pregnant or gave birth in the last 6 months?" },
] as const;

export default function DonorScreeningPage() {
  const user = useAuthStore((s) => s.user);
  const personCpid = user?.healthId || user?.id || "";
  const { data: donor, isPending, isError } = useDonorByPerson(personCpid || undefined);
  const submit = useSubmitDonorPreScreening(donor?.donorId ?? "");
  const [answers, setAnswers] = useState<Record<string, boolean>>({});
  const [outcome, setOutcome] = useState<string | null>(null);
  const [safeMessage, setSafeMessage] = useState<string | null>(null);

  function toggle(id: string, value: boolean) {
    setAnswers((prev) => ({ ...prev, [id]: value }));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!donor?.donorId) return;
    const payload: Record<string, boolean> = {};
    QUESTIONS.forEach((q) => {
      payload[q.id] = !!answers[q.id];
    });
    submit.mutate(
      { answers: payload },
      {
        onSuccess: (row) => {
          setOutcome(row.outcome ?? null);
          setSafeMessage(row.safeMessage ?? null);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Wellness check before donating"
        subtitle="A short self-check — not a medical diagnosis. A nurse confirms eligibility at the drive."
        icon={<ClipboardCheck className="h-6 w-6" />}
      >
        <div className="mb-6">
          <MadiDonorAssistPanel op="prescreening_guide" />
        </div>

        {isPending && (
          <div className="flex items-center gap-2 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading…
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-4 text-sm flex gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0" /> Could not verify donor status.
          </div>
        )}

        {!isPending && !donor && (
          <div className="rounded-2xl border border-dashed border-border p-8 text-center">
            <p className="text-sm text-muted-foreground">Register first, then complete your wellness check.</p>
            <Link href="/madi/donor/register" className="mt-3 inline-block text-sm font-medium text-rose-600">
              Register as donor →
            </Link>
          </div>
        )}

        {donor && (
          <form onSubmit={handleSubmit} className="space-y-4 max-w-lg">
            {QUESTIONS.map((q) => (
              <fieldset key={q.id} className="rounded-xl border border-border bg-card p-4">
                <legend className="text-sm font-medium text-foreground px-1">{q.label}</legend>
                <div className="mt-2 flex gap-4 text-sm">
                  <label className="flex items-center gap-2">
                    <input
                      type="radio"
                      name={q.id}
                      checked={answers[q.id] === true}
                      onChange={() => toggle(q.id, true)}
                    />
                    Yes
                  </label>
                  <label className="flex items-center gap-2">
                    <input
                      type="radio"
                      name={q.id}
                      checked={answers[q.id] === false}
                      onChange={() => toggle(q.id, false)}
                    />
                    No
                  </label>
                </div>
              </fieldset>
            ))}
            <button
              type="submit"
              disabled={submit.isPending || QUESTIONS.some((q) => answers[q.id] === undefined)}
              className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {submit.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Submit wellness check
            </button>
          </form>
        )}

        {outcome && (
          <div className="mt-6 space-y-4">
            <div
              className={`rounded-2xl border p-4 flex gap-3 ${
                outcome === "PROCEED_TO_DRIVE"
                  ? "border-green-200 bg-green-50"
                  : "border-warning/35 bg-warning-soft"
              }`}
            >
              <CheckCircle2 className="h-5 w-5 shrink-0 text-foreground" />
              <div>
                <p className="text-sm font-medium text-foreground">
                  {outcome === "PROCEED_TO_DRIVE"
                    ? "You may be ready to visit a drive"
                    : outcome === "DEFER_SAFELY"
                      ? "It may be better to wait before donating"
                      : "Please speak with blood bank staff"}
                </p>
                {safeMessage && <p className="mt-1 text-sm text-foreground">{safeMessage}</p>}
              </div>
            </div>
            <MadiDonorAssistPanel
              op="interpret_prescreening_outcome"
              context={{ prescreening_outcome: outcome }}
              label="Explain my result"
            />
            {outcome === "PROCEED_TO_DRIVE" && (
              <Link
                href="/madi/donor/drives"
                className="inline-flex rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white"
              >
                Find a donation drive
              </Link>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
