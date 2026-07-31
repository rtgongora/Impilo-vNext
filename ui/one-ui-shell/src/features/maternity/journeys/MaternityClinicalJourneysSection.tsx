"use client";

import { useMemo, useState } from "react";
import { AlertTriangle, HelpCircle } from "lucide-react";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { clinicalFormPatientContextFromPatient } from "@/lib/clinical-forms/patient-context";
import { usePatient } from "@/hooks/queries/usePatients";
import { GovernedEncounterFormPanel, YoungInfantPathLink } from "./GovernedEncounterFormPanel";
import {
  ANC_FIRST_CONTACT_FORM_KEY,
  ANC_FOLLOWUP_FORM_KEY,
  FP_ELIGIBILITY_FORM_KEY,
  PNC_MATERNAL_FORM_KEY,
  PNC_NEWBORN_FORM_KEY,
} from "./form-keys";
import { useMecAssess, type MecAssessResponse } from "@/hooks/queries/useMecAssess";
import {
  screeningIncomplete,
  usePncMaternalAssess,
  usePncNewbornAssess,
  type PncMaternalAssessResponse,
  type PncNewbornAssessResponse,
} from "@/hooks/queries/usePostnatalAssess";

export interface MaternityClinicalJourneysSectionProps {
  patientId: string;
  encounterId: string;
  hasActiveEncounter: boolean;
}

function PncMaternalOutcome({ result }: { result: PncMaternalAssessResponse }) {
  if (!result.screening_complete) {
    return (
      <div className="rounded-lg border border-amber-300/70 bg-amber-50 px-3 py-2 text-xs" data-testid="pnc-maternal-incomplete-screen">
        <p className="flex items-center gap-1 font-semibold"><HelpCircle className="h-3.5 w-3.5" /> Screening not completed</p>
        <p className="mt-1">Not the same as &ldquo;no danger signs&rdquo; — complete every question before an all-clear.</p>
      </div>
    );
  }
  if (result.no_danger_signs_found) {
    return <p className="text-xs text-emerald-800">No danger signs on what was recorded (screening completed).</p>;
  }
  return (
    <div className="space-y-1 text-xs">
      {result.top_line_action && <p className="font-semibold text-red-900">{result.top_line_action}</p>}
      {result.alerts.map((a) => (
        <p key={a.code}>{a.name}: {a.required_action}</p>
      ))}
    </div>
  );
}

function MecOutcomePanel({ result }: { result: MecAssessResponse }) {
  return (
    <ul className="space-y-1 text-xs" data-testid="mec-outcome">
      {result.methods.map((m) => (
        <li key={m.method_code} data-testid={`mec-method-${m.method_code}`}>
          <span className="font-medium">{m.method_name}</span>: {m.recommendation.replace(/_/g, " ")}
        </li>
      ))}
    </ul>
  );
}

export function MaternityClinicalJourneysSection({
  patientId,
  encounterId,
  hasActiveEncounter,
}: MaternityClinicalJourneysSectionProps) {
  const { isClinical } = useRoleGroup();
  const { data: patientData } = usePatient(patientId);
  const [activeJourney, setActiveJourney] = useState(ANC_FIRST_CONTACT_FORM_KEY);
  const [pncMaternalResult, setPncMaternalResult] = useState<PncMaternalAssessResponse | null>(null);
  const [pncNewbornResult, setPncNewbornResult] = useState<PncNewbornAssessResponse | null>(null);
  const [mecResult, setMecResult] = useState<MecAssessResponse | null>(null);
  const [newbornAgeDays, setNewbornAgeDays] = useState("7");

  const pncMaternalAssess = usePncMaternalAssess();
  const pncNewbornAssess = usePncNewbornAssess();
  const mecAssess = useMecAssess();

  const patient = useMemo(() => {
    if (!patientData?.data) return null;
    const extras =
      activeJourney === ANC_FOLLOWUP_FORM_KEY || activeJourney === ANC_FIRST_CONTACT_FORM_KEY
        ? { pregnant: true as const, programmes: ["ANC"] }
        : activeJourney === FP_ELIGIBILITY_FORM_KEY
          ? { programmes: ["FAMILY_PLANNING"] }
          : { programmes: ["PNC"] };
    return clinicalFormPatientContextFromPatient(patientData.data, extras);
  }, [patientData, activeJourney]);

  if (!isClinical) return null;

  const eid = hasActiveEncounter ? encounterId : "";
  const tabs = [
    { key: ANC_FIRST_CONTACT_FORM_KEY, label: "ANC first contact" },
    { key: ANC_FOLLOWUP_FORM_KEY, label: "ANC follow-up" },
    { key: PNC_MATERNAL_FORM_KEY, label: "Facility PNC — mother" },
    { key: PNC_NEWBORN_FORM_KEY, label: "Facility PNC — newborn" },
    { key: FP_ELIGIBILITY_FORM_KEY, label: "FP eligibility" },
  ];

  return (
    <section className="mt-4 rounded-lg border border-pink-200/90 bg-card p-5" data-testid="maternity-clinical-journeys">
      <h2 className="text-lg font-semibold">Clinical journeys (W13-C)</h2>
      <p className="mt-1 text-xs text-muted-foreground">
        Dedicated ANC, facility PNC and FP forms — not encounter-resolver dependent. CHW postnatal stays on outreach.
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            data-testid={`journey-tab-${t.key}`}
            onClick={() => { setActiveJourney(t.key); setPncMaternalResult(null); setMecResult(null); }}
            className={`rounded-lg border px-3 py-1.5 text-xs font-medium ${activeJourney === t.key ? "border-pink-600 bg-pink-600 text-white" : "border-border"}`}
          >
            {t.label}
          </button>
        ))}
      </div>
      {patient && activeJourney === ANC_FIRST_CONTACT_FORM_KEY && (
        <GovernedEncounterFormPanel testId="anc-first-contact-panel" formKey={ANC_FIRST_CONTACT_FORM_KEY} title="ANC first contact" encounterId={eid} patientId={patientId} patient={patient} providerRoles={["MIDWIFE"]} encounterType="ANTENATAL" />
      )}
      {patient && activeJourney === ANC_FOLLOWUP_FORM_KEY && (
        <GovernedEncounterFormPanel testId="anc-followup-panel" formKey={ANC_FOLLOWUP_FORM_KEY} title="ANC follow-up" encounterId={eid} patientId={patientId} patient={patient} providerRoles={["MIDWIFE"]} encounterType="ANTENATAL" />
      )}
      {patient && activeJourney === PNC_MATERNAL_FORM_KEY && (
        <GovernedEncounterFormPanel
          testId="pnc-maternal-panel"
          formKey={PNC_MATERNAL_FORM_KEY}
          title="Facility PNC — mother"
          encounterId={eid}
          patientId={patientId}
          patient={patient}
          providerRoles={["MIDWIFE"]}
          encounterType="POSTNATAL"
          onSubmitted={(answers) => {
            if (screeningIncomplete(answers)) {
              setPncMaternalResult({ alerts: [], not_assessed: [], incomplete: true, screening_complete: false, no_danger_signs_found: false });
              return;
            }
            pncMaternalAssess.mutate({ facts: answers, screeningComplete: true }, { onSuccess: (r) => setPncMaternalResult(r.data) });
          }}
          outcomeSlot={pncMaternalResult ? <PncMaternalOutcome result={pncMaternalResult} /> : null}
        />
      )}
      {patient && activeJourney === PNC_NEWBORN_FORM_KEY && (
        <GovernedEncounterFormPanel
          testId="pnc-newborn-panel"
          formKey={PNC_NEWBORN_FORM_KEY}
          title="Facility PNC — newborn"
          encounterId={eid}
          patientId={patientId}
          patient={patient}
          providerRoles={["MIDWIFE", "NURSE"]}
          encounterType="POSTNATAL"
          headerSlot={
            <div className="space-y-2">
              <YoungInfantPathLink patientId={patientId} />
              <label className="block text-xs">
                Newborn age (days)
                <input type="number" min={0} max={59} value={newbornAgeDays} onChange={(e) => setNewbornAgeDays(e.target.value)} className="mt-1 block w-24 rounded border px-2 py-1 text-sm" data-testid="newborn-age-days" />
              </label>
            </div>
          }
          onSubmitted={(answers) => {
            const age = parseInt(newbornAgeDays, 10);
            pncNewbornAssess.mutate({ ageDays: Number.isFinite(age) ? age : 0, facts: answers, screeningComplete: answers.screeningComplete === "YES" }, { onSuccess: (r) => setPncNewbornResult(r.data) });
          }}
          outcomeSlot={pncNewbornResult ? (
            <div data-testid="pnc-newborn-outcome">
              {pncNewbornResult.incomplete && (
                <p className="flex items-center gap-1 text-xs text-amber-900"><AlertTriangle className="h-3.5 w-3.5" /> Incomplete — not an all-clear.</p>
              )}
            </div>
          ) : null}
        />
      )}
      {patient && activeJourney === FP_ELIGIBILITY_FORM_KEY && (
        <GovernedEncounterFormPanel
          testId="fp-eligibility-panel"
          formKey={FP_ELIGIBILITY_FORM_KEY}
          title="FP eligibility (form 18)"
          encounterId={eid}
          patientId={patientId}
          patient={patient}
          providerRoles={["MIDWIFE", "NURSE"]}
          encounterType="FAMILY_PLANNING"
          onSubmitted={(answers) => mecAssess.mutate({ facts: answers }, { onSuccess: (r) => setMecResult(r.data) })}
          outcomeSlot={mecAssess.isPending ? <p className="text-xs">Assessing MEC…</p> : mecResult ? <MecOutcomePanel result={mecResult} /> : null}
        />
      )}
    </section>
  );
}
