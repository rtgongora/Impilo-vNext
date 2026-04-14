"use client";

/**
 * EncounterMenu — Compact collapsible clinical navigation sidebar.
 *
 * Section headers are always visible. Sub-items expand on click/hover.
 * Active section auto-expands. Much less vertical space consumed.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { useParams, usePathname } from "next/navigation";
import {
  LayoutDashboard, Clock, HeartPulse, Stethoscope, History,
  ShieldAlert, Syringe, Pill, ClipboardList, FlaskConical,
  ArrowRightLeft, FileText, StickyNote, DoorOpen, Activity,
  User, MonitorDot, Target, Users, Scissors, TrendingUp,
  Heart, Home, Shield, Brain, ChevronDown, Globe2,
} from "lucide-react";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, displayCpid } from "@/lib/pii-mask";

interface MenuItem {
  label: string;
  segment: string;
  icon: React.ElementType;
}

interface MenuSection {
  title: string;
  icon: React.ElementType;
  items: MenuItem[];
}

const MENU_SECTIONS: MenuSection[] = [
  {
    title: "Overview",
    icon: LayoutDashboard,
    items: [
      { label: "Summary", segment: "summary", icon: LayoutDashboard },
      { label: "Timeline", segment: "timeline", icon: Clock },
      { label: "IPS", segment: "ips", icon: Globe2 },
    ],
  },
  {
    title: "Assessment",
    icon: HeartPulse,
    items: [
      { label: "Vitals", segment: "vitals", icon: HeartPulse },
      { label: "Conditions", segment: "conditions", icon: Stethoscope },
      { label: "History", segment: "history", icon: History },
    ],
  },
  {
    title: "Problems",
    icon: ShieldAlert,
    items: [
      { label: "Allergies", segment: "allergies", icon: ShieldAlert },
      { label: "Immunizations", segment: "immunizations", icon: Syringe },
    ],
  },
  {
    title: "Care",
    icon: Pill,
    items: [
      { label: "Medications", segment: "medications", icon: Pill },
      { label: "Orders", segment: "orders", icon: ClipboardList },
      { label: "Results", segment: "results", icon: FlaskConical },
      { label: "Imaging", segment: "imaging", icon: MonitorDot },
      { label: "Care Plans", segment: "care-plans", icon: Target },
      { label: "Care Team", segment: "care-team", icon: Users },
      { label: "Goals", segment: "goals", icon: Target },
    ],
  },
  {
    title: "Background",
    icon: Heart,
    items: [
      { label: "Family History", segment: "family-history", icon: Heart },
      { label: "Social History", segment: "social-history", icon: Home },
      { label: "Functional", segment: "functional-status", icon: Activity },
      { label: "Directives", segment: "advance-directives", icon: Shield },
    ],
  },
  {
    title: "Depth",
    icon: Brain,
    items: [
      { label: "Procedures", segment: "procedures", icon: Scissors },
      { label: "Growth", segment: "growth-chart", icon: TrendingUp },
      { label: "Assessments", segment: "assessments", icon: Brain },
    ],
  },
  {
    title: "Consults",
    icon: ArrowRightLeft,
    items: [
      { label: "Referrals", segment: "consults", icon: ArrowRightLeft },
      { label: "Documents", segment: "documents", icon: FileText },
    ],
  },
  {
    title: "Outcome",
    icon: DoorOpen,
    items: [
      { label: "Notes", segment: "notes", icon: StickyNote },
      { label: "Discharge", segment: "discharge", icon: DoorOpen },
    ],
  },
];

export function EncounterMenu() {
  const params = useParams();
  const pathname = usePathname();
  const patientId = params?.patientId as string | undefined;

  const { data: patientData } = usePatient(patientId ?? "");
  const { data: encountersData } = useEncounters(patientId ?? "");

  const activeSegment = getActiveSegment(pathname, patientId ?? "");
  const patient = patientData?.data;
  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS",
  );

  // Which section contains the active segment?
  const activeSectionTitle = useMemo(() => {
    for (const s of MENU_SECTIONS) {
      if (s.items.some((i) => i.segment === activeSegment)) return s.title;
    }
    return null;
  }, [activeSegment]);

  // Expanded sections — active section is always open
  const [expanded, setExpanded] = useState<Set<string>>(new Set(activeSectionTitle ? [activeSectionTitle] : ["Overview"]));

  function toggleSection(title: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(title)) next.delete(title);
      else next.add(title);
      return next;
    });
  }

  if (!patientId) return null;

  return (
    <aside className="w-52 bg-white border-r overflow-y-auto shrink-0 flex flex-col">
      {/* Compact patient header */}
      <div className="px-3 py-2.5 border-b bg-slate-50">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-impilo-100 flex items-center justify-center shrink-0">
            <User className="w-3.5 h-3.5 text-impilo-600" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-xs font-semibold text-gray-900 truncate">
              {maskName(String(patient?.attributes.displayName ?? ""), usePrivacyDisplayStore.getState().level) || "Patient"}
            </p>
            <p className="text-[10px] text-gray-400 truncate">
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

      {/* Chart link */}
      <Link
        href={`/ehr/${patientId}`}
        className="flex items-center gap-2 mx-2 mt-2 px-2 py-1.5 text-xs text-gray-500 hover:text-impilo-600 hover:bg-impilo-50 rounded-md transition-colors"
      >
        <LayoutDashboard className="w-3.5 h-3.5" />
        Patient Chart
      </Link>

      {/* Collapsible sections */}
      <nav className="flex-1 py-1 px-1">
        {MENU_SECTIONS.map((section) => {
          const SectionIcon = section.icon;
          const isExpanded = expanded.has(section.title) || section.title === activeSectionTitle;
          const hasActiveChild = section.items.some((i) => i.segment === activeSegment);

          return (
            <div key={section.title} className="mb-0.5">
              {/* Section header — always visible */}
              <button
                onClick={() => toggleSection(section.title)}
                className={`w-full flex items-center gap-2 px-2 py-1.5 rounded-md text-left transition-colors ${
                  hasActiveChild
                    ? "bg-impilo-50 text-impilo-700"
                    : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                }`}
              >
                <SectionIcon className={`w-3.5 h-3.5 shrink-0 ${hasActiveChild ? "text-impilo-500" : "text-gray-400"}`} />
                <span className="text-[11px] font-semibold flex-1">{section.title}</span>
                <ChevronDown className={`w-3 h-3 text-gray-400 transition-transform ${isExpanded ? "rotate-180" : ""}`} />
              </button>

              {/* Sub-items — collapsed by default */}
              {isExpanded && (
                <div className="ml-3 border-l border-gray-100 pl-1.5 py-0.5">
                  {section.items.map((item) => {
                    const href = `/ehr/${patientId}/${item.segment}`;
                    const isActive = activeSegment === item.segment;
                    const Icon = item.icon;

                    return (
                      <Link
                        key={item.segment}
                        href={href}
                        className={`flex items-center gap-2 px-2 py-1 rounded-md text-[11px] transition-colors ${
                          isActive
                            ? "bg-impilo-100 text-impilo-700 font-medium"
                            : "text-gray-500 hover:bg-gray-50 hover:text-gray-700"
                        }`}
                      >
                        <Icon className={`w-3 h-3 ${isActive ? "text-impilo-500" : "text-gray-400"}`} />
                        {item.label}
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      {/* Status footer */}
      <div className="border-t px-3 py-1.5">
        <div className="flex items-center gap-1.5 text-[10px] text-gray-400">
          <div className={`w-1.5 h-1.5 rounded-full ${activeEncounter ? "bg-emerald-400" : "bg-gray-300"}`} />
          {activeEncounter ? "Live" : "Chart"}
        </div>
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
