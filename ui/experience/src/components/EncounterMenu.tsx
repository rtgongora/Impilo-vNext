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
  Video,
} from "lucide-react";

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
    ],
  },
  {
    title: "Consults & Referrals",
    items: [
      { label: "Consults", description: "Specialist consultations and referrals", segment: "consults", icon: ArrowRightLeft },
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

  if (!patientId) return null;

  const activeSegment = getActiveSegment(pathname, patientId);

  return (
    <aside className="w-64 bg-white border-r overflow-y-auto shrink-0">
      {/* Header */}
      <div className="px-3 py-3 border-b">
        <h2 className="text-xs font-semibold text-gray-900 uppercase tracking-wide">
          Encounter Record
        </h2>
        <p className="text-[10px] text-gray-400 mt-0.5">Clinical Documentation</p>
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
                </Link>
              );
            })}
          </div>
        ))}
      </nav>
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
