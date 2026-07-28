"use client";

/**
 * EncounterMenu — Intelligent wizard sidebar for clinical encounters.
 *
 * Role-aware: shows a recommended step sequence based on the logged-in
 * clinician type. Steps are ordered by clinical workflow, with progress
 * indicators and "Next step" guidance. Irrelevant sections are hidden
 * or deprioritised.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { useParams, usePathname } from "next/navigation";
import {
  LayoutDashboard, Clock, HeartPulse, Stethoscope, History,
  ShieldAlert, Syringe, Pill, ClipboardList, FlaskConical,
  ArrowRightLeft, FileText, StickyNote, DoorOpen, Activity,
  User, MonitorDot, Target, Users, Scissors, TrendingUp,
  Heart, Home, Shield, Brain, Globe2,
  ArrowRight, CheckCircle2, Circle, Baby,
} from "lucide-react";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import { useAuthStore } from "@/hooks/useAuthStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, displayCpid } from "@/lib/pii-mask";

interface WizardStep {
  label: string;
  segment: string;
  icon: React.ElementType;
  /** hint shown below the step label */
  hint?: string;
}

interface WizardPhase {
  title: string;
  steps: WizardStep[];
}

// ── Role-specific wizard flows ───────────────────────────────────

function getDoctorFlow(): WizardPhase[] {
  return [
    {
      title: "1. Assess",
      steps: [
        { label: "Vitals", segment: "vitals", icon: HeartPulse, hint: "Record vital signs" },
        { label: "History", segment: "history", icon: History, hint: "Review/update history" },
        { label: "Conditions", segment: "conditions", icon: Stethoscope, hint: "Active problems" },
        // Programmes + problems + allergies in one view; an overview belongs where the clinician
        // orients themselves, not among the treatment steps.
        { label: "Medicine", segment: "medicine", icon: LayoutDashboard, hint: "Chronic & programme overview" },
        { label: "Allergies", segment: "allergies", icon: ShieldAlert, hint: "Verify allergies" },
      ],
    },
    {
      title: "2. Diagnose",
      steps: [
        { label: "Encounter", segment: "encounter", icon: ClipboardList, hint: "Structured assessment" },
        { label: "Results", segment: "results", icon: FlaskConical, hint: "Labs & diagnostics" },
        { label: "Imaging", segment: "imaging", icon: MonitorDot, hint: "PACS / X-ray" },
      ],
    },
    {
      title: "3. Treat",
      steps: [
        { label: "Medications", segment: "medications", icon: Pill, hint: "Prescribe" },
        { label: "Orders", segment: "orders", icon: ClipboardList, hint: "Lab & procedure orders" },
        { label: "Procedures", segment: "procedures", icon: Scissors, hint: "Procedures done" },
        { label: "Care Plans", segment: "care-plans", icon: Target, hint: "Goals & interventions" },
        // HIV/TB enrolment, regimens and governed guidance — longitudinal treatment, so it sits
        // with the other treatment steps rather than in Assess.
        { label: "Programmes", segment: "programmes", icon: Activity, hint: "HIV/TB care & regimens" },
      ],
    },
    {
      title: "4. Coordinate",
      steps: [
        { label: "Referrals", segment: "consults", icon: ArrowRightLeft, hint: "Consults & teleconsults" },
        { label: "Documents", segment: "documents", icon: FileText, hint: "Attach documents" },
      ],
    },
    {
      title: "5. Close",
      steps: [
        { label: "Notes", segment: "notes", icon: StickyNote, hint: "Clinical notes" },
        { label: "Discharge", segment: "discharge", icon: DoorOpen, hint: "Disposition & discharge" },
      ],
    },
  ];
}

function getNurseFlow(): WizardPhase[] {
  return [
    {
      title: "1. Triage & Assess",
      steps: [
        { label: "Vitals", segment: "vitals", icon: HeartPulse, hint: "Obs & vital signs" },
        { label: "Allergies", segment: "allergies", icon: ShieldAlert, hint: "Allergy check" },
        { label: "Encounter", segment: "encounter", icon: ClipboardList, hint: "Nursing assessment" },
      ],
    },
    {
      title: "2. Care Delivery",
      steps: [
        { label: "Medications", segment: "medications", icon: Pill, hint: "Medication round" },
        { label: "Orders", segment: "orders", icon: ClipboardList, hint: "Pending orders" },
        { label: "Care Plans", segment: "care-plans", icon: Target, hint: "Nursing care plan" },
      ],
    },
    {
      title: "3. Document",
      steps: [
        { label: "Notes", segment: "notes", icon: StickyNote, hint: "Nursing notes" },
        { label: "Results", segment: "results", icon: FlaskConical, hint: "Specimen results" },
        { label: "Documents", segment: "documents", icon: FileText, hint: "Uploaded docs" },
      ],
    },
    {
      title: "4. Handover",
      steps: [
        { label: "Summary", segment: "summary", icon: LayoutDashboard, hint: "Patient summary" },
        { label: "Discharge", segment: "discharge", icon: DoorOpen, hint: "Discharge checklist" },
      ],
    },
  ];
}

function getPharmacistFlow(): WizardPhase[] {
  return [
    {
      title: "1. Verify",
      steps: [
        { label: "Medications", segment: "medications", icon: Pill, hint: "Active prescriptions" },
        { label: "Allergies", segment: "allergies", icon: ShieldAlert, hint: "Allergy check" },
        { label: "Conditions", segment: "conditions", icon: Stethoscope, hint: "Active conditions" },
        { label: "Encounter", segment: "encounter", icon: ClipboardList, hint: "Dispensing checks" },
      ],
    },
    {
      title: "2. Review",
      steps: [
        { label: "Results", segment: "results", icon: FlaskConical, hint: "Renal/hepatic function" },
        { label: "Orders", segment: "orders", icon: ClipboardList, hint: "Pending orders" },
      ],
    },
    {
      title: "3. Document",
      steps: [
        { label: "Notes", segment: "notes", icon: StickyNote, hint: "Intervention notes" },
        { label: "Documents", segment: "documents", icon: FileText, hint: "Documentation" },
      ],
    },
  ];
}

function getMidwifeFlow(): WizardPhase[] {
  return [
    {
      title: "1. ANC Assessment",
      steps: [
        { label: "Vitals", segment: "vitals", icon: HeartPulse, hint: "BP, weight, urine" },
        { label: "Encounter", segment: "encounter", icon: Baby, hint: "ANC checklist" },
        { label: "Conditions", segment: "conditions", icon: Stethoscope, hint: "Risk factors" },
      ],
    },
    {
      title: "2. Investigations",
      steps: [
        { label: "Results", segment: "results", icon: FlaskConical, hint: "Hb, HIV, syphilis" },
        { label: "Immunizations", segment: "immunizations", icon: Syringe, hint: "Tetanus, others" },
      ],
    },
    {
      title: "3. Care & Counsel",
      steps: [
        { label: "Medications", segment: "medications", icon: Pill, hint: "Iron/folate, ARVs" },
        { label: "Care Plans", segment: "care-plans", icon: Target, hint: "Birth plan" },
        { label: "Referrals", segment: "consults", icon: ArrowRightLeft, hint: "If high risk" },
      ],
    },
    {
      title: "4. Document",
      steps: [
        { label: "Notes", segment: "notes", icon: StickyNote, hint: "Visit notes" },
        { label: "Growth", segment: "growth-chart", icon: TrendingUp, hint: "Fundal height" },
      ],
    },
  ];
}

function getTherapistFlow(title: string): WizardPhase[] {
  return [
    { title: "1. Assess", steps: [
      { label: "Summary", segment: "summary", icon: LayoutDashboard, hint: "Patient overview" },
      { label: "Conditions", segment: "conditions", icon: Stethoscope, hint: "Active problems" },
      { label: "Encounter", segment: "encounter", icon: ClipboardList, hint: `${title} assessment` },
    ]},
    { title: "2. Treat", steps: [
      { label: "Care Plans", segment: "care-plans", icon: Target, hint: "Goals & interventions" },
      { label: "Orders", segment: "orders", icon: ClipboardList, hint: "Requests" },
      { label: "Medications", segment: "medications", icon: Pill, hint: "Current meds" },
    ]},
    { title: "3. Close", steps: [
      { label: "Notes", segment: "notes", icon: StickyNote, hint: "Session notes" },
      { label: "Referrals", segment: "consults", icon: ArrowRightLeft, hint: "Refer if needed" },
      { label: "Discharge", segment: "discharge", icon: DoorOpen, hint: "Discharge / follow-up" },
    ]},
  ];
}

function getDiagnosticFlow(title: string): WizardPhase[] {
  return [
    { title: "1. Verify", steps: [
      { label: "Orders", segment: "orders", icon: ClipboardList, hint: "Incoming requests" },
      { label: "Allergies", segment: "allergies", icon: ShieldAlert, hint: "Safety check" },
      { label: "Encounter", segment: "encounter", icon: ClipboardList, hint: `${title} checklist` },
    ]},
    { title: "2. Process", steps: [
      { label: "Results", segment: "results", icon: FlaskConical, hint: "Enter results" },
      { label: "Documents", segment: "documents", icon: FileText, hint: "Attach reports" },
    ]},
    { title: "3. Close", steps: [
      { label: "Notes", segment: "notes", icon: StickyNote, hint: "Tech notes" },
    ]},
  ];
}

function getCommunityFlow(): WizardPhase[] {
  return [
    { title: "1. Visit", steps: [
      { label: "Encounter", segment: "encounter", icon: ClipboardList, hint: "Visit checklist" },
      { label: "Vitals", segment: "vitals", icon: HeartPulse, hint: "Basic obs" },
      { label: "Immunizations", segment: "immunizations", icon: Syringe, hint: "Vaccine status" },
    ]},
    { title: "2. Act", steps: [
      { label: "Medications", segment: "medications", icon: Pill, hint: "Drugs given" },
      { label: "Referrals", segment: "consults", icon: ArrowRightLeft, hint: "Facility referral" },
    ]},
    { title: "3. Record", steps: [
      { label: "Notes", segment: "notes", icon: StickyNote, hint: "Visit notes" },
    ]},
  ];
}

function getFlowForRole(roles: string[]): WizardPhase[] {
  if (roles.includes("PHARMACIST")) return getPharmacistFlow();
  if (roles.includes("NURSE")) return getNurseFlow();
  if (roles.includes("MIDWIFE")) return getMidwifeFlow();
  if (roles.includes("PHYSIOTHERAPIST")) return getTherapistFlow("Physio");
  if (roles.includes("OCCUPATIONAL_THERAPIST")) return getTherapistFlow("OT");
  if (roles.includes("PSYCHOLOGIST") || roles.includes("COUNSELLOR")) return getTherapistFlow("Psychology");
  if (roles.includes("NUTRITIONIST") || roles.includes("DIETITIAN")) return getTherapistFlow("Nutrition");
  if (roles.includes("SOCIAL_WORKER")) return getTherapistFlow("Social work");
  if (roles.includes("SPEECH_THERAPIST") || roles.includes("SLT")) return getTherapistFlow("SLT");
  if (roles.includes("RESPIRATORY_THERAPIST")) return getTherapistFlow("Respiratory");
  if (roles.includes("AUDIOLOGIST")) return getTherapistFlow("Audiology");
  if (roles.includes("PODIATRIST")) return getTherapistFlow("Podiatry");
  if (roles.includes("RADIOGRAPHER") || roles.includes("IMAGING_TECH")) return getDiagnosticFlow("Imaging");
  if (roles.includes("LAB_TECHNOLOGIST") || roles.includes("LAB_TECH")) return getDiagnosticFlow("Lab");
  if (roles.includes("OPTOMETRIST")) return getTherapistFlow("Optometry");
  if (roles.includes("DENTIST") || roles.includes("ORAL_HYGIENIST")) return getTherapistFlow("Dental");
  if (roles.includes("EMT") || roles.includes("PARAMEDIC")) return getDoctorFlow();
  if (roles.includes("RADIOTHERAPIST") || roles.includes("RADIATION_THERAPIST")) return getDiagnosticFlow("Radiotherapy");
  if (roles.includes("CHW") || roles.includes("COMMUNITY_HEALTH_WORKER")) return getCommunityFlow();
  if (roles.includes("EHO") || roles.includes("ENVIRONMENTAL_HEALTH")) return getCommunityFlow();
  return getDoctorFlow();
}

// ── Component ─────────────────────────────────────────────────────

export function EncounterMenu() {
  const params = useParams();
  const pathname = usePathname();
  const patientId = params?.patientId as string | undefined;
  const user = useAuthStore((s) => s.user);

  const { data: patientData } = usePatient(patientId ?? "");
  const { data: encountersData } = useEncounters(patientId ?? "");

  const activeSegment = getActiveSegment(pathname, patientId ?? "");
  const patient = patientData?.data;
  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.isOpen,
  );

  const phases = useMemo(() => getFlowForRole(user?.roles ?? []), [user?.roles]);

  // Track visited steps
  const [visited, setVisited] = useState<Set<string>>(new Set(activeSegment ? [activeSegment] : []));

  // Mark current segment as visited
  if (activeSegment && !visited.has(activeSegment)) {
    setVisited((prev) => new Set(prev).add(activeSegment));
  }

  // Find next recommended step
  const nextStep = useMemo(() => {
    for (const phase of phases) {
      for (const step of phase.steps) {
        if (!visited.has(step.segment)) return step;
      }
    }
    return null;
  }, [phases, visited]);

  // All steps flat for encounter path building
  const allSteps = useMemo(() => phases.flatMap((p) => p.steps), [phases]);
  const currentIdx = allSteps.findIndex((s) => s.segment === activeSegment);
  const prevStep = currentIdx > 0 ? allSteps[currentIdx - 1] : null;
  const nextSeqStep = currentIdx >= 0 && currentIdx < allSteps.length - 1 ? allSteps[currentIdx + 1] : null;

  if (!patientId) return null;

  return (
    <aside className="w-52 bg-card border-r overflow-y-auto shrink-0 flex flex-col">
      {/* Patient header */}
      <div className="px-3 py-2 border-b bg-background">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-primary-soft flex items-center justify-center shrink-0">
            <User className="w-3.5 h-3.5 text-primary" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold text-foreground truncate">
              {maskName(String(patient?.attributes.displayName ?? ""), usePrivacyDisplayStore.getState().level) || "Patient"}
            </p>
            <p className="text-[10px] text-muted-foreground truncate">
              {displayCpid(String(patient?.attributes.cpid ?? patientId))}
            </p>
          </div>
          {activeEncounter ? (
            <span className="w-2 h-2 rounded-full bg-emerald-400 shrink-0" title="Live encounter" />
          ) : (
            <span className="w-2 h-2 rounded-full bg-gray-300 shrink-0" title="Chart view" />
          )}
        </div>
      </div>

      {/* Progress bar */}
      <div className="px-3 py-2 border-b">
        <div className="flex items-center justify-between text-[10px] text-muted-foreground mb-1">
          <span>Progress</span>
          <span>{visited.size}/{allSteps.length} steps</span>
        </div>
        <div className="h-1.5 bg-neutral-100 rounded-full overflow-hidden">
          <div className="h-full bg-primary rounded-full transition-all"
            style={{ width: `${(visited.size / allSteps.length) * 100}%` }} />
        </div>
      </div>

      {/* Next step suggestion */}
      {nextStep && (
        <Link href={`/ehr/${patientId}/${nextStep.segment}`}
          className="mx-2 mt-2 flex items-center gap-2 px-2.5 py-2 bg-primary-soft border border-primary/25 rounded-lg text-xs hover:bg-primary-soft transition-colors">
          <ArrowRight className="w-3.5 h-3.5 text-primary shrink-0" />
          <div className="min-w-0">
            <p className="font-semibold text-primary-hover truncate">Next: {nextStep.label}</p>
            {nextStep.hint && <p className="text-primary truncate">{nextStep.hint}</p>}
          </div>
        </Link>
      )}

      {/* Chart link */}
      <Link href={`/ehr/${patientId}`}
        className="flex items-center gap-2 mx-2 mt-2 px-2 py-1.5 text-xs text-muted-foreground hover:text-primary hover:bg-primary-soft rounded-md transition-colors">
        <LayoutDashboard className="w-3.5 h-3.5" /> Patient Chart
      </Link>

      {/* Wizard phases */}
      <nav className="flex-1 py-1 px-1">
        {phases.map((phase) => (
          <div key={phase.title} className="mb-1">
            <div className="px-2 py-1 text-[10px] font-bold text-muted-foreground uppercase tracking-wider">
              {phase.title}
            </div>
            <div className="space-y-0.5">
              {phase.steps.map((step) => {
                const href = `/ehr/${patientId}/${step.segment}`;
                const isActive = activeSegment === step.segment;
                const isVisited = visited.has(step.segment);

                return (
                  <Link key={step.segment} href={href}
                    className={`flex items-center gap-2 px-2 py-1.5 rounded-md transition-colors ${
                      isActive
                        ? "bg-primary-soft text-primary-hover"
                        : isVisited
                          ? "text-foreground hover:bg-background"
                          : "text-muted-foreground hover:bg-background hover:text-muted-foreground"
                    }`}>
                    {/* Step indicator */}
                    <div className="w-4 h-4 flex items-center justify-center shrink-0">
                      {isVisited && !isActive ? (
                        <CheckCircle2 className="w-3.5 h-3.5 text-primary" />
                      ) : isActive ? (
                        <div className="w-2.5 h-2.5 rounded-full bg-primary" />
                      ) : (
                        <Circle className="w-3 h-3 text-muted-foreground" />
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className={`text-[11px] truncate ${isActive ? "font-semibold" : isVisited ? "font-medium" : ""}`}>
                        {step.label}
                      </p>
                      {isActive && step.hint && (
                        <p className="text-[9px] text-primary truncate">{step.hint}</p>
                      )}
                    </div>
                  </Link>
                );
              })}
            </div>
          </div>
        ))}

        {/* More sections (collapsed) */}
        <details className="mt-1">
          <summary className="px-2 py-1 text-[10px] font-bold text-muted-foreground uppercase tracking-wider cursor-pointer hover:text-muted-foreground">
            More...
          </summary>
          <div className="space-y-0.5 mt-0.5">
            {[
              { label: "Timeline", segment: "timeline", icon: Clock },
              { label: "IPS", segment: "ips", icon: Globe2 },
              { label: "Family Hx", segment: "family-history", icon: Heart },
              { label: "Social Hx", segment: "social-history", icon: Home },
              { label: "Functional", segment: "functional-status", icon: Activity },
              { label: "Directives", segment: "advance-directives", icon: Shield },
              { label: "Growth", segment: "growth-chart", icon: TrendingUp },
              { label: "Assessments", segment: "assessments", icon: Brain },
              { label: "Care Team", segment: "care-team", icon: Users },
              { label: "Goals", segment: "goals", icon: Target },
            ].map((item) => {
              const isActive = activeSegment === item.segment;
              const Icon = item.icon;
              return (
                <Link key={item.segment} href={`/ehr/${patientId}/${item.segment}`}
                  className={`flex items-center gap-2 px-2 py-1 rounded-md text-[11px] transition-colors ${
                    isActive ? "bg-primary-soft text-primary-hover font-medium" : "text-muted-foreground hover:bg-background hover:text-muted-foreground"
                  }`}>
                  <Icon className="w-3 h-3" /> {item.label}
                </Link>
              );
            })}
          </div>
        </details>
      </nav>

      {/* Prev/Next navigation */}
      <div className="border-t px-2 py-2 flex items-center gap-1">
        {prevStep ? (
          <Link href={`/ehr/${patientId}/${prevStep.segment}`}
            className="flex-1 text-center text-[10px] text-muted-foreground hover:text-muted-foreground py-1 rounded hover:bg-background">
            ← {prevStep.label}
          </Link>
        ) : <div className="flex-1" />}
        {nextSeqStep ? (
          <Link href={`/ehr/${patientId}/${nextSeqStep.segment}`}
            className="flex-1 text-center text-[10px] font-medium text-primary hover:text-primary-hover py-1 rounded hover:bg-primary-soft">
            {nextSeqStep.label} →
          </Link>
        ) : <div className="flex-1" />}
      </div>
    </aside>
  );
}

function getActiveSegment(pathname: string, patientId: string): string | null {
  const prefix = `/ehr/${patientId}/`;
  if (!pathname.startsWith(prefix)) return null;
  const rest = pathname.slice(prefix.length);
  return rest.split("/")[0] || null;
}
