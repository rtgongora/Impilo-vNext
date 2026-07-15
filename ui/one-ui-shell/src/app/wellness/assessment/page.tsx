"use client";

import { useMemo, useState } from "react";
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  Loader2,
  ShieldAlert,
  ShieldCheck,
  HeartPulse,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useStartAssessment,
  useSaveAssessmentStep,
  useCompleteAssessment,
  type AssessmentResponseInput,
  type CompletionResult,
} from "@/hooks/queries/useSimbaAssessment";

type Step = "INTRO" | "LIFESTYLE" | "MEASUREMENTS" | "RISK" | "SUMMARY" | "RESULT";

const STEP_ORDER: Step[] = ["INTRO", "LIFESTYLE", "MEASUREMENTS", "RISK", "SUMMARY", "RESULT"];

const BAND_STYLES: Record<string, { label: string; className: string }> = {
  LOW: { label: "Low risk", className: "bg-emerald-50 text-emerald-700 border-emerald-200" },
  MODERATE: { label: "Moderate risk", className: "bg-amber-50 text-amber-700 border-amber-200" },
  HIGH: { label: "High risk", className: "bg-orange-50 text-orange-700 border-orange-200" },
  NEEDS_CLINICAL_REVIEW: {
    label: "Needs clinical review",
    className: "bg-rose-50 text-rose-700 border-rose-200",
  },
  URGENT_RED_FLAG: { label: "Urgent", className: "bg-red-100 text-red-800 border-red-300" },
};

/** Baseline wellness assessment wizard — start → answer → score → result, all persisted in Simba. */
export default function WellnessAssessmentPage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id ?? "";

  const start = useStartAssessment();
  const saveStep = useSaveAssessmentStep();
  const complete = useCompleteAssessment();

  const [assessmentId, setAssessmentId] = useState<string | null>(null);
  const [step, setStep] = useState<Step>("INTRO");
  const [consent, setConsent] = useState(false);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [result, setResult] = useState<CompletionResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const set = (k: string, v: string) => setAnswers((prev) => ({ ...prev, [k]: v }));

  const busy = start.isPending || saveStep.isPending || complete.isPending;

  async function begin() {
    setError(null);
    try {
      const res = await start.mutateAsync({ person_cpid: cpid });
      const id = (res.data as { assessmentId: string }).assessmentId;
      setAssessmentId(id);
      setStep("LIFESTYLE");
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function saveAndAdvance(section: AssessmentResponseInput["section"], keys: string[], next: Step) {
    if (!assessmentId) return;
    setError(null);
    const responses: AssessmentResponseInput[] = keys
      .filter((k) => answers[k] != null && answers[k] !== "")
      .map((k) => {
        const numeric = Number(answers[k]);
        const isNum = !Number.isNaN(numeric) && section === "MEASUREMENT";
        return {
          section,
          question_code: k,
          value_text: isNum ? null : answers[k],
          value_numeric: isNum ? numeric : null,
        };
      });
    try {
      await saveStep.mutateAsync({
        assessmentId,
        step: next,
        consent_captured: step === "INTRO" ? consent : undefined,
        responses,
      });
      setStep(next);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function submit() {
    if (!assessmentId) return;
    setError(null);
    // Ensure consent is recorded before completion.
    try {
      await saveStep.mutateAsync({ assessmentId, consent_captured: true });
      const res = await complete.mutateAsync({ assessmentId });
      setResult(res);
      setStep("RESULT");
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const progress = useMemo(
    () => Math.round(((STEP_ORDER.indexOf(step) + 1) / STEP_ORDER.length) * 100),
    [step],
  );

  return (
    <AppLayout>
      <PageShell
        title="Wellness assessment"
        subtitle="A short, private check-in that gives you a personalised wellness picture."
        icon={<HeartPulse className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-6">
          <div className="h-2 w-full overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-emerald-500 transition-all"
              style={{ width: `${progress}%` }}
              aria-label={`Progress ${progress}%`}
            />
          </div>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}

          {step === "INTRO" && (
            <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
              <h2 className="text-lg font-semibold">Before we start</h2>
              <p className="text-sm text-slate-600">
                Your answers stay private to you and, if you choose, your wellness care team. This
                is a wellness check-in — not a medical diagnosis. If anything urgent shows up, we will
                help you connect with care.
              </p>
              <label className="flex items-start gap-3 text-sm">
                <input
                  type="checkbox"
                  checked={consent}
                  onChange={(e) => setConsent(e.target.checked)}
                  className="mt-1"
                />
                <span>
                  I consent to Simba storing my wellness assessment and using it to personalise my
                  wellness guidance.
                </span>
              </label>
              <button
                type="button"
                disabled={!consent || !cpid || busy}
                onClick={begin}
                className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
                Start assessment
              </button>
            </section>
          )}

          {step === "LIFESTYLE" && (
            <WizardStep
              title="Lifestyle"
              icon={<Activity className="h-5 w-5" />}
              onNext={() => saveAndAdvance("LIFESTYLE", ["tobacco_use"], "MEASUREMENTS")}
              busy={busy}
            >
              <Field label="Do you use tobacco?">
                <Select
                  value={answers.tobacco_use ?? ""}
                  onChange={(v) => set("tobacco_use", v)}
                  options={[
                    ["NEVER", "Never"],
                    ["FORMER", "Former"],
                    ["WEEKLY", "Weekly"],
                    ["DAILY", "Daily"],
                  ]}
                />
              </Field>
            </WizardStep>
          )}

          {step === "MEASUREMENTS" && (
            <WizardStep
              title="Measurements"
              icon={<HeartPulse className="h-5 w-5" />}
              onNext={() =>
                saveAndAdvance(
                  "MEASUREMENT",
                  ["systolic_bp", "bmi", "fasting_glucose", "waist_cm"],
                  "RISK",
                )
              }
              busy={busy}
            >
              <Field label="Systolic blood pressure (mmHg)">
                <NumberInput value={answers.systolic_bp} onChange={(v) => set("systolic_bp", v)} />
              </Field>
              <Field label="Body mass index (BMI)">
                <NumberInput value={answers.bmi} onChange={(v) => set("bmi", v)} />
              </Field>
              <Field label="Fasting glucose (mmol/L)">
                <NumberInput
                  value={answers.fasting_glucose}
                  onChange={(v) => set("fasting_glucose", v)}
                />
              </Field>
              <Field label="Waist circumference (cm)">
                <NumberInput value={answers.waist_cm} onChange={(v) => set("waist_cm", v)} />
              </Field>
            </WizardStep>
          )}

          {step === "RISK" && (
            <WizardStep
              title="Wellbeing"
              icon={<ShieldCheck className="h-5 w-5" />}
              onNext={() =>
                saveAndAdvance("RISK_QUESTION", ["mood_score", "self_harm_thoughts"], "SUMMARY")
              }
              busy={busy}
            >
              <Field label="Over the last 2 weeks, how would you rate your low-mood level (0 calm – 27 severe)?">
                <NumberInput value={answers.mood_score} onChange={(v) => set("mood_score", v)} />
              </Field>
              <Field label="Have you had thoughts of harming yourself?">
                <Select
                  value={answers.self_harm_thoughts ?? ""}
                  onChange={(v) => set("self_harm_thoughts", v)}
                  options={[
                    ["NO", "No"],
                    ["YES", "Yes"],
                  ]}
                />
              </Field>
            </WizardStep>
          )}

          {step === "SUMMARY" && (
            <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
              <h2 className="text-lg font-semibold">Ready to see your results?</h2>
              <p className="text-sm text-slate-600">
                We will calculate your wellness picture from your answers. You can review your
                results and any suggested next steps on the next screen.
              </p>
              <button
                type="button"
                disabled={busy}
                onClick={submit}
                className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                Calculate my results
              </button>
            </section>
          )}

          {step === "RESULT" && result && (
            <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
              <div
                className={`inline-flex items-center gap-2 rounded-full border px-4 py-1.5 text-sm font-medium ${
                  BAND_STYLES[result.risk_band]?.className ?? "bg-slate-50 text-slate-700 border-slate-200"
                }`}
              >
                {result.risk_band === "URGENT_RED_FLAG" ? (
                  <ShieldAlert className="h-4 w-4" />
                ) : (
                  <ShieldCheck className="h-4 w-4" />
                )}
                {BAND_STYLES[result.risk_band]?.label ?? result.risk_band}
              </div>
              {result.care_linkage_routed && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                  A provider follow-up has been created for you. Someone from your wellness care team
                  will reach out.
                </div>
              )}
              <div>
                <h3 className="mb-2 text-sm font-semibold text-slate-700">Suggested next steps</h3>
                <ul className="space-y-2">
                  {result.next_actions.map((a) => (
                    <li key={a} className="flex items-start gap-2 text-sm text-slate-600">
                      <ArrowRight className="mt-0.5 h-4 w-4 text-emerald-500" />
                      {a}
                    </li>
                  ))}
                </ul>
              </div>
              <a
                href="/wellness/timeline"
                className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700"
              >
                View my wellness timeline
              </a>
            </section>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function WizardStep({
  title,
  icon,
  children,
  onNext,
  busy,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  onNext: () => void;
  busy: boolean;
}) {
  return (
    <section className="space-y-4 rounded-xl border border-slate-200 bg-white p-6">
      <h2 className="flex items-center gap-2 text-lg font-semibold">
        {icon}
        {title}
      </h2>
      <div className="space-y-4">{children}</div>
      <button
        type="button"
        disabled={busy}
        onClick={onNext}
        className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
        Continue
      </button>
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block space-y-1.5">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      {children}
    </label>
  );
}

function NumberInput({
  value,
  onChange,
}: {
  value?: string;
  onChange: (v: string) => void;
}) {
  return (
    <input
      type="number"
      inputMode="decimal"
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
    />
  );
}

function Select({
  value,
  onChange,
  options,
}: {
  value: string;
  onChange: (v: string) => void;
  options: [string, string][];
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
    >
      <option value="">Select…</option>
      {options.map(([v, label]) => (
        <option key={v} value={v}>
          {label}
        </option>
      ))}
    </select>
  );
}
