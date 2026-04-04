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

interface MenuSection {
  title: string;
  items: Array<{
    label: string;
    segment: string;
    icon: React.ElementType;
  }>;
}

const MENU_SECTIONS: MenuSection[] = [
  {
    title: "Overview",
    items: [
      { label: "Summary", segment: "summary", icon: LayoutDashboard },
      { label: "Timeline", segment: "timeline", icon: Clock },
    ],
  },
  {
    title: "Assessment",
    items: [
      { label: "Vitals", segment: "vitals", icon: HeartPulse },
      { label: "Conditions", segment: "conditions", icon: Stethoscope },
      { label: "History", segment: "history", icon: History },
    ],
  },
  {
    title: "Problems & Diagnoses",
    items: [
      { label: "Allergies", segment: "allergies", icon: ShieldAlert },
      { label: "Immunizations", segment: "immunizations", icon: Syringe },
    ],
  },
  {
    title: "Care & Management",
    items: [
      { label: "Medications", segment: "medications", icon: Pill },
      { label: "Orders", segment: "orders", icon: ClipboardList },
      { label: "Results", segment: "results", icon: FlaskConical },
    ],
  },
  {
    title: "Consults & Referrals",
    items: [
      { label: "Consults", segment: "consults", icon: ArrowRightLeft },
      { label: "Documents", segment: "documents", icon: FileText },
    ],
  },
  {
    title: "Discharge",
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

  if (!patientId) return null;

  const activeSegment = getActiveSegment(pathname, patientId);

  return (
    <aside className="w-56 bg-white border-r overflow-y-auto shrink-0">
      <div className="px-3 py-3 border-b">
        <Link
          href={`/ehr/${patientId}`}
          className="flex items-center gap-2 text-sm font-semibold text-gray-900 hover:text-blue-600 transition-colors"
        >
          <User className="w-4 h-4" />
          Patient Chart
        </Link>
        <Link
          href={`/ehr/${patientId}/encounters`}
          className="flex items-center gap-1.5 mt-1.5 text-xs text-gray-500 hover:text-blue-600"
        >
          <Activity className="w-3 h-3" />
          Encounters
        </Link>
      </div>

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
                  className={`flex items-center gap-2 px-3 py-1.5 mx-1 rounded-md text-sm transition-colors ${
                    isActive
                      ? "bg-blue-50 text-blue-700 font-medium"
                      : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? "text-blue-600" : "text-gray-400"}`} />
                  {item.label}
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
