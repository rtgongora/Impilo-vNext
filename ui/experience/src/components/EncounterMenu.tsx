"use client";

/**
 * EncounterMenu — Lovable-parity persistent encounter workspace navigation.
 *
 * Primary sections align to the canonical clinical shell. Secondary links
 * expose additional chart routes without replacing core encounter navigation.
 * Role gating uses the same role groups as other clinical surfaces (aligned
 * with AuthGuardProvider / SecurityConfig). Tshepo authorisation is enforced
 * on the BFF and services; the browser only hides sections that the user's
 * role groups are not allowed to open. Facility context is carried when links include encounter_id.
 */

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, usePathname } from "next/navigation";
import {
  LayoutDashboard,
  Stethoscope,
  ClipboardList,
  FileStack,
  HeartHandshake,
  ArrowRightLeft,
  StickyNote,
  DoorOpen,
  User,
  Clock,
  Globe2,
  Heart,
  Home,
  Activity,
  Shield,
  TrendingUp,
  Brain,
  Users,
  Target,
  ChevronRight,
  Circle,
} from "lucide-react";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { usePatient } from "@/hooks/queries/usePatients";
import { useAuthStore } from "@/hooks/useAuthStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { maskName, displayCpid } from "@/lib/pii-mask";
import {
  ENCOUNTER_MENU_LABELS,
  orderedWorkspaceSections,
  sectionForPathname,
  buildSectionHref,
  isSectionVisible,
  type EncounterWorkspaceSectionId,
} from "@/lib/clinical/encounter-workspace-nav";

const SECTION_ICONS: Record<EncounterWorkspaceSectionId, React.ElementType> = {
  overview: LayoutDashboard,
  assessment: Stethoscope,
  problems_diagnoses: ClipboardList,
  orders_results: FileStack,
  care_management: HeartHandshake,
  consults_referrals: ArrowRightLeft,
  notes_attachments: StickyNote,
  visit_outcome: DoorOpen,
};

const MORE_CHART_LINKS: { label: string; segment: string; icon: React.ElementType }[] = [
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
];

function getActiveSegment(pathname: string, patientId: string): string | null {
  const prefix = `/ehr/${patientId}/`;
  if (!pathname.startsWith(prefix)) return null;
  const rest = pathname.slice(prefix.length);
  return rest.split("/")[0] || null;
}

export function EncounterMenu() {
  const params = useParams();
  const pathname = usePathname() ?? "";
  const patientId = params?.patientId as string | undefined;
  const encounterFromRoute = params?.encounterId as string | undefined;
  const user = useAuthStore((s) => s.user);
  const role = useRoleGroup();

  const { data: patientData } = usePatient(patientId ?? "");
  const { data: encountersData } = useEncounters(patientId ?? "");

  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS",
  );
  const encounterId = encounterFromRoute ?? activeEncounter?.id ?? null;

  const roleFlags = useMemo(
    () => ({
      isClinical: role.isClinical,
      isPrescriber: role.isPrescriber,
      isDispenser: role.isDispenser,
      isAdmin: role.isAdmin,
    }),
    [role.isAdmin, role.isClinical, role.isDispenser, role.isPrescriber],
  );

  const primarySections = useMemo(
    () => orderedWorkspaceSections().filter((id) => isSectionVisible(id, roleFlags)),
    [roleFlags],
  );

  const activeSection = patientId ? sectionForPathname(pathname, patientId) : null;
  const activeSegment = patientId ? getActiveSegment(pathname, patientId) : null;

  const visitKey = patientId ? `exp:ehr-section-visit:${patientId}` : null;
  const [visitedCount, setVisitedCount] = useState(0);

  useEffect(() => {
    if (!visitKey || !patientId || typeof window === "undefined") return;
    const section = sectionForPathname(pathname, patientId);
    if (!section) return;
    const raw = sessionStorage.getItem(visitKey);
    const set = new Set<string>(raw ? (JSON.parse(raw) as string[]) : []);
    set.add(section);
    const next = [...set];
    sessionStorage.setItem(visitKey, JSON.stringify(next));
    setVisitedCount(next.length);
  }, [pathname, patientId, visitKey]);

  const patient = patientData?.data;

  const nextSection = useMemo(() => {
    if (!activeSection) return primarySections[0] ?? null;
    const idx = primarySections.indexOf(activeSection);
    if (idx < 0) return primarySections[0] ?? null;
    return primarySections[idx + 1] ?? null;
  }, [activeSection, primarySections]);

  if (!patientId) return null;

  const ctx = { patientId, encounterId };

  return (
    <aside className="w-52 bg-white border-r overflow-y-auto shrink-0 flex flex-col">
      <div className="px-3 py-2 border-b bg-slate-50">
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

      <div className="px-3 py-2 border-b">
        <div className="flex items-center justify-between text-[10px] text-gray-400 mb-1">
          <span>Workspace</span>
          <span>
            {visitedCount}/{primarySections.length} sections
          </span>
        </div>
        <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-impilo-500 rounded-full transition-all"
            style={{
              width: `${Math.min(100, (visitedCount / Math.max(primarySections.length, 1)) * 100)}%`,
            }}
          />
        </div>
      </div>

      {nextSection && nextSection !== activeSection && (
        <Link
          href={buildSectionHref(nextSection, ctx)}
          className="mx-2 mt-2 flex items-center gap-2 px-2.5 py-2 bg-impilo-50 border border-impilo-200 rounded-lg text-xs hover:bg-impilo-100 transition-colors"
        >
          <ChevronRight className="w-3.5 h-3.5 text-impilo-500 shrink-0" />
          <div className="min-w-0">
            <p className="font-semibold text-impilo-700 truncate">Next: {ENCOUNTER_MENU_LABELS[nextSection]}</p>
          </div>
        </Link>
      )}

      <Link
        href={`/ehr/${patientId}`}
        className="flex items-center gap-2 mx-2 mt-2 px-2 py-1.5 text-xs text-gray-500 hover:text-impilo-600 hover:bg-impilo-50 rounded-md transition-colors"
      >
        <LayoutDashboard className="w-3.5 h-3.5" /> Patient overview
      </Link>

      <nav className="flex-1 py-1 px-1" aria-label="Encounter workspace">
        <div className="px-2 py-1 text-[10px] font-bold text-gray-400 uppercase tracking-wider">Encounter menu</div>
        <div className="space-y-0.5">
          {primarySections.map((section) => {
            const href = buildSectionHref(section, ctx);
            const isActive = activeSection === section;
            const Icon = SECTION_ICONS[section];
            return (
              <Link
                key={section}
                href={href}
                className={`flex items-center gap-2 px-2 py-1.5 rounded-md transition-colors ${
                  isActive ? "bg-impilo-100 text-impilo-700" : "text-gray-600 hover:bg-gray-50"
                }`}
              >
                <div className="w-4 h-4 flex items-center justify-center shrink-0">
                  {isActive ? (
                    <div className="w-2.5 h-2.5 rounded-full bg-impilo-500" />
                  ) : (
                    <Circle className="w-3 h-3 text-gray-300" />
                  )}
                </div>
                <Icon className="w-3.5 h-3.5 shrink-0 opacity-80" />
                <span className={`text-[11px] leading-tight ${isActive ? "font-semibold" : ""}`}>{ENCOUNTER_MENU_LABELS[section]}</span>
              </Link>
            );
          })}
        </div>

        <details className="mt-2">
          <summary className="px-2 py-1 text-[10px] font-bold text-gray-300 uppercase tracking-wider cursor-pointer hover:text-gray-500">
            More chart areas
          </summary>
          <div className="space-y-0.5 mt-0.5">
            {MORE_CHART_LINKS.map((item) => {
              const isActive = activeSegment === item.segment;
              const Icon = item.icon;
              return (
                <Link
                  key={item.segment}
                  href={`/ehr/${patientId}/${item.segment}`}
                  className={`flex items-center gap-2 px-2 py-1 rounded-md text-[11px] transition-colors ${
                    isActive ? "bg-impilo-100 text-impilo-700 font-medium" : "text-gray-500 hover:bg-gray-50 hover:text-gray-700"
                  }`}
                >
                  <Icon className="w-3 h-3" /> {item.label}
                </Link>
              );
            })}
          </div>
        </details>
      </nav>

      <div className="border-t px-2 py-2 text-[10px] text-gray-400 truncate" title={user?.email ?? ""}>
        {role.isClinical ? "Clinical workspace" : role.isDispenser ? "Medication workspace" : "Chart"}
      </div>
    </aside>
  );
}
