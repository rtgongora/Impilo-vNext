"use client";

/**
 * EncounterMenu — Persistent clinical navigation sidebar for EHR views.
 * Provides grouped navigation sections:
 *   - Overview (Summary, Timeline)
 *   - Assessment (Vitals, Conditions, History)
 *   - Problems & Diagnoses (Allergies, Immunizations)
 *   - Care & Management (Medications, Orders, Results)
 *   - Consults & Referrals (Referrals, Documents)
 *   - Discharge (Notes, Discharge)
 */

import Link from "next/link";
import { useMemo } from "react";
import { useParams, usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Clock,
  HeartPulse,
  Stethoscope,
  History,
  ShieldAlert,
  Syringe,
  Pill,
  ClipboardList,
  FlaskConical,
  ArrowRightLeft,
  FileText,
  StickyNote,
  DoorOpen,
  Activity,
  User,
  MonitorDot,
  Target,
  Users,
  Scissors,
  TrendingUp,
  Heart,
  Home,
  Shield,
  Brain,
  ChevronRight,
} from "lucide-react";
import { useClinicalNotes } from "@/hooks/queries/useClinicalNotes";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import { useVitals } from "@/hooks/queries/useVitals";

interface MenuItem {
  label: string;
  description: string;
  segment: string;
  icon: React.ElementType;
}

interface MenuSection {
  title: string;
  items: MenuItem[];
}

const MENU_SECTIONS: MenuSection[] = [
  {
    title: "Overview",
    items: [
      { label: "Summary", description: "Patient summary and status", segment: "summary", icon: LayoutDashboard },
      { label: "Timeline", description: "Clinical event timeline", segment: "timeline", icon: Clock },
    ],
  },
  {
    title: "Assessment",
    items: [
      { label: "Vitals", description: "Record and review vital signs", segment: "vitals", icon: HeartPulse },
      { label: "Conditions", description: "Active problems and diagnoses", segment: "conditions", icon: Stethoscope },
      { label: "History", description: "Past medical history", segment: "history", icon: History },
    ],
  },
  {
    title: "Problems & Diagnoses",
    items: [
      { label: "Allergies", description: "Allergy and adverse reactions", segment: "allergies", icon: ShieldAlert },
      { label: "Immunizations", description: "Vaccination history", segment: "immunizations", icon: Syringe },
    ],
  },
  {
    title: "Care & Management",
    items: [
      { label: "Medications", description: "Prescriptions and formulary", segment: "medications", icon: Pill },
      { label: "Orders", description: "Lab orders and imaging", segment: "orders", icon: ClipboardList },
      { label: "Results", description: "Lab and diagnostic results", segment: "results", icon: FlaskConical },
      { label: "Imaging", description: "DICOM viewer and PACS", segment: "imaging", icon: MonitorDot },
      { label: "Care Plans", description: "Goals and interventions", segment: "care-plans", icon: Target },
      { label: "Care Team", description: "Assigned providers", segment: "care-team", icon: Users },
      { label: "Goals", description: "Patient health goals", segment: "goals", icon: Target },
    ],
  },
  {
    title: "History & Context",
    items: [
      { label: "Family History", description: "Hereditary risk factors", segment: "family-history", icon: Heart },
      { label: "Social History", description: "Social determinants of health", segment: "social-history", icon: Home },
      { label: "Functional Status", description: "ADL and IADL assessments", segment: "functional-status", icon: Activity },
      { label: "Advance Directives", description: "DNR, living will, POA", segment: "advance-directives", icon: Shield },
    ],
  },
  {
    title: "Clinical Depth",
    items: [
      { label: "Procedures", description: "Past and scheduled procedures", segment: "procedures", icon: Scissors },
      { label: "Growth Chart", description: "Paediatric growth tracking", segment: "growth-chart", icon: TrendingUp },
      { label: "Assessments", description: "Standardized scoring tools", segment: "assessments", icon: Brain },
    ],
  },
  {
    title: "Consults & Referrals",
    items: [
      { label: "Consults", description: "Consultations, referrals, and teleconsults", segment: "consults", icon: ArrowRightLeft },
      { label: "Documents", description: "Clinical documents and attachments", segment: "documents", icon: FileText },
    ],
  },
  {
    title: "Visit Outcome",
    items: [
      { label: "Notes", description: "Clinical notes and documentation", segment: "notes", icon: StickyNote },
      { label: "Discharge", description: "Encounter disposition", segment: "discharge", icon: DoorOpen },
    ],
  },
];

export function EncounterMenu() {
  const params = useParams();
  const pathname = usePathname();
  const patientId = params?.patientId as string | undefined;
  const patientIdOrEmpty = patientId ?? "";

  const { data: patientData } = usePatient(patientIdOrEmpty);
  const { data: encountersData } = useEncounters(patientIdOrEmpty);
  const { data: notesData } = useClinicalNotes(patientIdOrEmpty);
  const { data: vitalsData } = useVitals(patientIdOrEmpty);

  const activeSegment = getActiveSegment(pathname, patientIdOrEmpty);
  const patient = patientData?.data;
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "ACTIVE" || encounter.attributes.status === "IN_PROGRESS",
  );
  const latestSavedAt = useMemo(() => {
    const noteTimes = (notesData?.data ?? [])
      .map((note) => note.attributes.signedAt ?? note.attributes.createdAt)
      .filter(Boolean);
    const vitalTimes = (vitalsData?.data ?? []).map((vital) => vital.attributes.recordedAt);
    const encounterTimes = activeEncounter
      ? [String(activeEncounter.attributes.closedAt ?? activeEncounter.attributes.startedAt)]
      : [];
    const timestamps = [...noteTimes, ...vitalTimes, ...encounterTimes]
      .map((value) => new Date(String(value)).getTime())
      .filter((value) => Number.isFinite(value));

    if (timestamps.length === 0) return null;

    return new Date(Math.max(...timestamps)).toISOString();
  }, [activeEncounter, notesData?.data, vitalsData?.data]);
  const activeItem = useMemo(() => {
    for (const section of MENU_SECTIONS) {
      const item = section.items.find((entry) => entry.segment === activeSegment);
      if (item) return item;
    }
    return null;
  }, [activeSegment]);

  if (!patientId) return null;

  return (
    <aside className="w-64 bg-white border-r overflow-y-auto shrink-0">
      {/* Header */}
      <div className="px-3 py-3 border-b bg-gradient-to-br from-slate-50 via-white to-blue-50/60">
        <h2 className="text-xs font-semibold text-gray-900 uppercase tracking-wide">
          Encounter Record
        </h2>
        <p className="text-[10px] text-gray-400 mt-0.5">Clinical Documentation</p>
        <div className="mt-3 rounded-xl border border-white/80 bg-white/90 p-3 shadow-sm">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-slate-900">
                {String(patient?.attributes.displayName ?? "Patient workspace")}
              </p>
              <p className="mt-0.5 text-[11px] text-slate-500">
                {String(patient?.attributes.cpid ?? patientId)}
              </p>
            </div>
            {activeEncounter ? (
              <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-medium text-emerald-700">
                Live
              </span>
            ) : (
              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-medium text-slate-600">
                Chart
              </span>
            )}
          </div>
          <div className="mt-2 flex flex-wrap gap-2">
            <span className="rounded-full bg-slate-100 px-2.5 py-1 text-[10px] font-medium text-slate-600">
              {activeItem?.label ?? "Workspace"}
            </span>
            {activeEncounter && (
              <span className="rounded-full bg-blue-50 px-2.5 py-1 text-[10px] font-medium text-blue-700">
                {String(activeEncounter.attributes.encounterType)}
              </span>
            )}
          </div>
          {activeItem?.description && (
            <p className="mt-2 text-[11px] leading-4 text-slate-500">{activeItem.description}</p>
          )}
        </div>
      </div>

      {/* Patient File Button */}
      <div className="px-2 py-2 border-b">
        <Link
          href={`/ehr/${patientId}`}
          className="flex items-center gap-2 w-full px-3 py-2 text-sm font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
        >
          <User className="w-4 h-4 text-gray-500" />
          Patient Chart
        </Link>
        <Link
          href={`/ehr/${patientId}/encounters`}
          className="flex items-center gap-1.5 mt-1.5 px-3 text-xs text-gray-500 hover:text-blue-600 transition-colors"
        >
          <Activity className="w-3 h-3" />
          Encounters
        </Link>
      </div>

      {/* Menu Items */}
      <nav className="py-2">
        {MENU_SECTIONS.map((section) => (
          <div key={section.title} className="mb-1">
            <div className="px-3 py-1.5 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
              {section.title}
            </div>
            {section.items.map((item) => {
              const href = `/ehr/${patientId}/${item.segment}`;
              const isActive = activeSegment === item.segment;
              const Icon = item.icon;

              return (
                <Link
                  key={item.segment}
                  href={href}
                  className={`flex items-center gap-3 px-3 py-2 mx-1 rounded-lg text-left transition-all ${
                    isActive
                      ? "bg-blue-50 text-blue-700"
                      : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                  }`}
                >
                  <div className={`w-8 h-8 rounded-md flex items-center justify-center transition-colors ${
                    isActive
                      ? "bg-blue-600 text-white"
                      : "bg-gray-100 text-gray-400 group-hover:bg-blue-100 group-hover:text-blue-600"
                  }`}>
                    <Icon className="w-4 h-4" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className={`text-sm truncate ${isActive ? "font-medium" : ""}`}>{item.label}</div>
                    <div className={`text-[10px] truncate ${
                      isActive ? "text-blue-600/70" : "text-gray-400"
                    }`}>{item.description}</div>
                  </div>
                  <ChevronRight className={`w-3.5 h-3.5 shrink-0 ${
                    isActive ? "text-blue-600" : "text-gray-300"
                  }`} />
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* Last Saved Indicator */}
      <div className="mt-auto border-t px-3 py-2">
        <div className="flex items-center justify-between gap-2 text-[10px]">
          <div className="flex items-center gap-1.5 text-gray-400">
            <div className={`w-1.5 h-1.5 rounded-full ${
              activeEncounter ? "bg-emerald-400" : "bg-slate-300"
            }`} />
            <span>{activeEncounter ? "Workspace live" : "Chart view"}</span>
          </div>
          <span className="text-gray-400">
            {latestSavedAt ? `Last saved ${formatRelativeTime(latestSavedAt)}` : "No saves yet"}
          </span>
        </div>
      </div>
    </aside>
  );
}

function getActiveSegment(pathname: string, patientId: string): string | null {
  const prefix = `/ehr/${patientId}/`;
  if (!pathname.startsWith(prefix)) return null;
  const rest = pathname.slice(prefix.length);
  const segment = rest.split("/")[0];
  return segment || null;
}

function formatRelativeTime(value: string): string {
  const deltaMs = Date.now() - new Date(value).getTime();

  if (!Number.isFinite(deltaMs) || deltaMs < 60_000) return "just now";

  const minutes = Math.round(deltaMs / 60_000);
  if (minutes < 60) return `${minutes}m ago`;

  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;

  const days = Math.round(hours / 24);
  return `${days}d ago`;
}
