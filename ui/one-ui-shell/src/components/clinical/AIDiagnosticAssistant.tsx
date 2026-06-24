"use client";

/**
 * AIDiagnosticAssistant — governed clinical decision support.
 *
 * This component is deliberately NOT a free-text "AI that invents diagnoses". It is a
 * thin, honest surface over the real clinical-knowledge-platform-service assistant
 * (`POST /internal/v1/clinical/assistant/ask`), which answers ONLY from indexed national
 * guidance (EDLIZ source sections + structured medicine guidance) and a deterministic
 * clinical rules engine, returning page-referenced citations and an auditable trace id.
 *
 * Hard guarantees (Product Truth):
 *  - No fabricated differentials. When the backend has no indexed evidence it returns
 *    support_mode = INSUFFICIENT_EVIDENCE and we render that honestly — we never
 *    synthesise a confident-looking answer client-side.
 *  - Advisory only. Every answer carries the backend disclaimer and a "does not replace
 *    clinical judgement" banner; the clinician remains the decision-maker.
 *  - Auditable. The returned trace id is surfaced so the recommendation can be traced /
 *    overridden via the clinical override audit trail.
 *
 * Replaces the previously-deleted orphan component of the same name, which silently
 * fabricated differential diagnoses with no backend at all.
 */

import { useState } from "react";
import { Sparkles, AlertTriangle, FileText, Loader2, ShieldCheck, Info, Stethoscope } from "lucide-react";
import { cn } from "@/lib/accessibility";
import { useAskEdlizClinical, type ClinicalAskResponse } from "@/hooks/queries/useGuidance";

interface Citation {
  section_id?: string;
  page?: number | string;
  section_title?: string;
  excerpt?: string;
  document_version?: string;
}

interface MedicineRecommendation {
  name?: string;
  generic?: string;
  dose?: string;
  unit?: string;
  route?: string;
  frequency?: string;
  duration?: string;
  level_of_care?: string;
  specialist_only?: boolean;
}

interface AIDiagnosticAssistantProps {
  /** Optional encounter the question is asked in the context of (forwarded for the audit trace). */
  encounterId?: string;
  /** Whether the asking actor is a licensed clinician (PROVIDER) or a citizen (education only). */
  clinicianMode?: boolean;
}

/** Honest visual treatment per backend support_mode — INSUFFICIENT_EVIDENCE must never look confident. */
const SUPPORT_MODE_META: Record<string, { label: string; className: string }> = {
  SOURCE_GROUNDED: { label: "Grounded in indexed guidance", className: "bg-primary-soft text-primary border-primary/25" },
  MIXED_RULE_AND_SOURCE: { label: "Guidance + clinical rules", className: "bg-primary-soft text-primary border-primary/25" },
  RULE_EVALUATED: { label: "Clinical rules evaluated", className: "bg-warning-soft text-warning-foreground border-warning/35" },
  INSUFFICIENT_EVIDENCE: {
    label: "No indexed evidence — not a clinical answer",
    className: "bg-orange-50 text-orange-700 border-orange-200",
  },
};

export function AIDiagnosticAssistant({ encounterId, clinicianMode = true }: AIDiagnosticAssistantProps) {
  const [complaint, setComplaint] = useState("");
  const [activeMeds, setActiveMeds] = useState("");
  const [diagnoses, setDiagnoses] = useState("");
  const [result, setResult] = useState<ClinicalAskResponse | null>(null);
  const [errored, setErrored] = useState(false);

  const ask = useAskEdlizClinical();

  const csv = (s: string) =>
    s
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean);

  async function submit() {
    const question = complaint.trim();
    if (!question || ask.isPending) return;
    setErrored(false);
    setResult(null);

    const patientContext: Record<string, unknown> = {};
    const meds = csv(activeMeds);
    const dx = csv(diagnoses);
    if (meds.length) patientContext.active_medications = meds;
    if (dx.length) patientContext.diagnoses = dx;

    try {
      const res = await ask.mutateAsync({
        question,
        citizen_mode: !clinicianMode,
        role: clinicianMode ? "PROVIDER" : "CITIZEN",
        patient_context: patientContext,
        encounter_id: encounterId,
      });
      setResult((res?.data as ClinicalAskResponse) ?? null);
    } catch {
      // Fail honest: surface unavailability, never fabricate a fallback answer.
      setErrored(true);
    }
  }

  const supportMode = result?.support_mode ? String(result.support_mode) : undefined;
  const supportMeta = supportMode ? SUPPORT_MODE_META[supportMode] : undefined;
  const citations = (Array.isArray(result?.source_citations) ? result?.source_citations : []) as Citation[];
  const warnings = (Array.isArray(result?.warnings) ? result?.warnings : []) as string[];
  const recommendation = (result?.recommendation as { medicine?: MedicineRecommendation } | null)?.medicine;
  const nextActions = (Array.isArray(result?.next_actions) ? result?.next_actions : []) as string[];

  return (
    <div className="rounded-xl border border-border bg-card p-4" data-testid="ai-diagnostic-assistant">
      {/* Header — advisory framing is non-negotiable */}
      <div className="flex items-start gap-2">
        <Stethoscope className="h-5 w-5 text-primary mt-0.5 shrink-0" />
        <div>
          <h3 className="text-sm font-semibold text-foreground">Clinical Decision Support</h3>
          <p className="text-[11px] text-muted-foreground leading-relaxed">
            Answers come only from indexed national guidance (EDLIZ) and clinical rules. Advisory only — it does not
            replace clinical assessment or local policy.
          </p>
        </div>
      </div>

      {/* Inputs */}
      <div className="mt-3 space-y-2">
        <label className="block text-[11px] font-medium text-muted-foreground" htmlFor="cds-complaint">
          Presenting complaint / clinical question
        </label>
        <textarea
          id="cds-complaint"
          value={complaint}
          onChange={(e) => setComplaint(e.target.value)}
          rows={2}
          placeholder="e.g. Adult with productive cough, fever and pleuritic chest pain — guideline management?"
          className="w-full rounded-lg border border-border bg-background px-3 py-2 text-xs"
          data-testid="cds-complaint-input"
        />
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
          <input
            value={activeMeds}
            onChange={(e) => setActiveMeds(e.target.value)}
            placeholder="Active medications (comma-separated)"
            className="rounded-lg border border-border bg-background px-3 py-2 text-xs"
            data-testid="cds-meds-input"
          />
          <input
            value={diagnoses}
            onChange={(e) => setDiagnoses(e.target.value)}
            placeholder="Known diagnoses (comma-separated)"
            className="rounded-lg border border-border bg-background px-3 py-2 text-xs"
            data-testid="cds-dx-input"
          />
        </div>
        <button
          onClick={submit}
          disabled={!complaint.trim() || ask.isPending}
          className={cn(
            "inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium",
            "bg-primary text-primary-foreground disabled:opacity-50",
          )}
          data-testid="cds-submit"
        >
          {ask.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
          {ask.isPending ? "Consulting guidance…" : "Get guidance"}
        </button>
      </div>

      {/* Error — honest, no fabricated fallback */}
      {errored && (
        <div
          className="mt-3 flex items-start gap-2 rounded-lg border border-orange-200 bg-orange-50 px-3 py-2"
          data-testid="cds-error"
        >
          <AlertTriangle className="h-4 w-4 text-orange-700 mt-0.5 shrink-0" />
          <p className="text-[11px] text-orange-800">
            The clinical knowledge service is unavailable. No guidance was retrieved — rely on clinical assessment and
            EDLIZ directly. (Nothing has been generated locally.)
          </p>
        </div>
      )}

      {/* Result */}
      {result && (
        <div className="mt-3 space-y-3" data-testid="cds-result">
          {/* Support-mode badge — honest about evidence strength */}
          {supportMeta && (
            <span
              className={cn("inline-flex items-center gap-1 rounded border px-2 py-0.5 text-[10px] font-medium", supportMeta.className)}
              data-testid="cds-support-mode"
            >
              <ShieldCheck className="h-3 w-3" />
              {supportMeta.label}
            </span>
          )}

          {/* Answer summary */}
          {result.answer_summary && (
            <p className="text-xs text-foreground leading-relaxed whitespace-pre-line" data-testid="cds-answer">
              {String(result.answer_summary)}
            </p>
          )}

          {/* Structured medicine recommendation (from real medicine_guidance rows) */}
          {recommendation?.name && (
            <div className="rounded-lg border border-border bg-muted/30 px-3 py-2 text-[11px]" data-testid="cds-recommendation">
              <div className="font-medium text-foreground">
                {recommendation.name}
                {recommendation.specialist_only && (
                  <span className="ml-2 rounded bg-danger-soft px-1.5 py-0.5 text-[9px] text-danger">specialist only</span>
                )}
              </div>
              <div className="text-muted-foreground">
                {[recommendation.dose, recommendation.unit, recommendation.route, recommendation.frequency, recommendation.duration]
                  .filter(Boolean)
                  .join(" · ")}
                {recommendation.level_of_care ? ` — ${recommendation.level_of_care}` : ""}
              </div>
            </div>
          )}

          {/* Rules-engine warnings/alerts */}
          {warnings.length > 0 && (
            <div className="space-y-1" data-testid="cds-warnings">
              {warnings.map((w, i) => (
                <div key={i} className="flex items-start gap-1.5 rounded bg-warning-soft px-2 py-1">
                  <AlertTriangle className="h-3.5 w-3.5 text-warning-foreground mt-0.5 shrink-0" />
                  <span className="text-[11px] text-warning-foreground">{w}</span>
                </div>
              ))}
            </div>
          )}

          {/* Suggested next actions */}
          {nextActions.length > 0 && (
            <ul className="list-disc pl-5 text-[11px] text-muted-foreground space-y-0.5">
              {nextActions.map((a, i) => (
                <li key={i}>{a}</li>
              ))}
            </ul>
          )}

          {/* Page-referenced citations */}
          {citations.length > 0 && (
            <div className="space-y-1.5" data-testid="cds-citations">
              <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">Sources</p>
              {citations.map((c, i) => (
                <div key={c.section_id ?? i} className="flex items-start gap-1.5">
                  <FileText className="h-3.5 w-3.5 text-muted-foreground mt-0.5 shrink-0" />
                  <p className="text-[11px] text-muted-foreground leading-relaxed">
                    <span className="font-medium text-foreground">{c.section_title || "Guidance section"}</span>
                    {c.page != null && <span> · p.{String(c.page)}</span>}
                    {c.document_version && <span> · {c.document_version}</span>}
                    {c.excerpt && <span className="block italic">{c.excerpt}</span>}
                  </p>
                </div>
              ))}
            </div>
          )}

          {/* Disclaimer + auditable trace */}
          <div className="flex items-start gap-1.5 border-t border-border/40 pt-2">
            <Info className="h-3.5 w-3.5 text-muted-foreground mt-0.5 shrink-0" />
            <p className="text-[10px] text-muted-foreground leading-relaxed">
              {result.disclaimer
                ? String(result.disclaimer)
                : "Guideline support assists clinical judgment; it is not a substitute for assessment and local policy."}
              {result.trace_id && (
                <span className="block mt-0.5 font-mono" data-testid="cds-trace">
                  Trace: {String(result.trace_id)}
                </span>
              )}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
