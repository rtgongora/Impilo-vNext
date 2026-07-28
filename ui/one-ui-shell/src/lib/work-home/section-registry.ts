/**
 * Work Home section presentation registry (Phase F2).
 *
 * Presentation metadata ONLY — icon, accent, a fallback label. The BFF is the sole source of
 * which sections exist and what they contain (`GET /internal/v1/work-home`); this registry
 * never grants or hides a section. An id missing here still renders, using the BFF's own
 * `title` and a generic icon — unlike `workSurfaceModules.ts` before F7, editing this file
 * cannot create or remove access to anything.
 */

import type { LucideIcon } from "lucide-react";
import {
  AlertTriangle,
  Building2,
  ClipboardList,
  Globe2,
  LayoutGrid,
  LifeBuoy,
  Map as MapIcon,
  ScrollText,
  Stethoscope,
  Users,
} from "lucide-react";

export interface WorkHomeSectionMeta {
  icon: LucideIcon;
  /** Tailwind text-color utility applied to the icon and section accent. */
  accentClassName: string;
}

const DEFAULT_META: WorkHomeSectionMeta = {
  icon: LayoutGrid,
  accentClassName: "text-muted-foreground",
};

const REGISTRY: Record<string, WorkHomeSectionMeta> = {
  "clinical-worklist": { icon: Stethoscope, accentClassName: "text-rose-600 dark:text-rose-400" },
  "virtual-worklist": { icon: Globe2, accentClassName: "text-sky-600 dark:text-sky-400" },
  "department-profile": { icon: ClipboardList, accentClassName: "text-amber-600 dark:text-amber-400" },
  "facility-status": { icon: Building2, accentClassName: "text-amber-600 dark:text-amber-400" },
  "facility-admin-claims": { icon: Users, accentClassName: "text-amber-600 dark:text-amber-400" },
  "jurisdiction-profile": { icon: MapIcon, accentClassName: "text-emerald-600 dark:text-emerald-400" },
  "programme-profile": { icon: ClipboardList, accentClassName: "text-emerald-600 dark:text-emerald-400" },
  "support-tickets": { icon: LifeBuoy, accentClassName: "text-violet-600 dark:text-violet-400" },
  "regulatory-appointments": { icon: ScrollText, accentClassName: "text-indigo-600 dark:text-indigo-400" },
  "professional-alerts": { icon: AlertTriangle, accentClassName: "text-orange-600 dark:text-orange-400" },
};

export function workHomeSectionMeta(sectionId: string): WorkHomeSectionMeta {
  return REGISTRY[sectionId] ?? DEFAULT_META;
}

export const WORK_HOME_BUCKET_LABELS: Record<string, string> = {
  ACT_NOW: "Act now",
  ACT_TODAY: "Act today",
  AWAITING_SOMEONE_ELSE: "Awaiting someone else",
  UPCOMING: "Upcoming",
  FOR_AWARENESS: "For awareness",
  COMPLETED: "Completed",
};

export const WORK_HOME_FAMILY_LABELS: Record<string, string> = {
  FACILITY_CLINICAL: "Clinical care",
  VIRTUAL_CARE: "Virtual care",
  DEPARTMENT_MANAGEMENT: "Department management",
  FACILITY_MANAGEMENT: "Facility management",
  OVERSIGHT: "Jurisdiction oversight",
  PROGRAMME_MANAGEMENT: "Programme management",
  SUPPORT: "Technical support",
  REGULATORY: "Regulatory operations",
};
