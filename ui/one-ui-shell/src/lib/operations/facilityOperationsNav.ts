import type { LucideIcon } from "lucide-react";
import {
  Activity,
  Armchair,
  BarChart3,
  BedDouble,
  Building2,
  Calendar,
  ClipboardList,
  Globe2,
  Layers,
  Radio,
  Route,
  Users,
  Video,
} from "lucide-react";

export type FacilityOpsRoleMask = {
  isClinical: boolean;
  isAdmin: boolean;
  isFinance: boolean;
  isDispenser: boolean;
  isQueueManager: boolean;
  isOperationsOversight: boolean;
};

export type FacilityOpsNavCard = {
  id: string;
  label: string;
  description: string;
  href: string;
  Icon: LucideIcon;
  /** If true, card is shown to any authenticated user with facility context. */
  everyone?: boolean;
  /** Any of these flags may show the card (OR logic). */
  anyOf?: Array<keyof FacilityOpsRoleMask>;
};

const CARDS: FacilityOpsNavCard[] = [
  {
    id: "control-tower",
    label: "Facility Control Tower",
    description: "Live queue, bed, roster, and threshold signals for the selected facility.",
    href: "/clinical/control-tower",
    Icon: BarChart3,
    anyOf: ["isQueueManager", "isClinical", "isAdmin"],
  },
  {
    id: "patient-flow",
    label: "Patient Flow Board",
    description: "Waiting, called, in-service columns with drill-down to chart where permitted.",
    href: "/operations/facility-operations/patient-flow",
    Icon: Activity,
    anyOf: ["isQueueManager", "isClinical", "isAdmin"],
  },
  {
    id: "district",
    label: "District & national operations",
    description: "Compare live queue pressure across up to 40 facilities (PCT-backed where reachable).",
    href: "/operations/facility-operations/district-view",
    Icon: Globe2,
    anyOf: ["isOperationsOversight"],
  },
  {
    id: "resources-board",
    label: "Service points & Tuso resources",
    description: "Rooms, consultation points, and booking resources from Tuso via the appointments plane.",
    href: "/operations/facility-operations/resources",
    Icon: Armchair,
    anyOf: ["isClinical", "isQueueManager", "isAdmin"],
  },
  {
    id: "queues",
    label: "Queues",
    description: "Arrival, triage, waiting, scheduled, walk-in, and referral queues.",
    href: "/queue",
    Icon: Users,
    anyOf: ["isQueueManager", "isClinical", "isDispenser", "isAdmin"],
  },
  {
    id: "booking-requests",
    label: "Booking requests",
    description: "Citizen booking inbox — triage, Mvumo, approve, and convert to appointments.",
    href: "/scheduling/booking-requests",
    Icon: ClipboardList,
    anyOf: ["isClinical", "isDispenser", "isAdmin"],
  },
  {
    id: "appointments",
    label: "Appointments",
    description: "Confirmed scheduled workload — today's list and full facility calendar.",
    href: "/scheduling/today",
    Icon: Calendar,
    anyOf: ["isClinical", "isDispenser", "isAdmin"],
  },
  {
    id: "roster",
    label: "Workforce & Rosters",
    description: "Shift workspace, roster week, and on-call visibility.",
    href: "/shift",
    Icon: ClipboardList,
    anyOf: ["isQueueManager", "isClinical", "isAdmin"],
  },
  {
    id: "scheduling-roster",
    label: "Scheduling roster board",
    description: "Planning view for coverage windows.",
    href: "/scheduling/roster",
    Icon: ClipboardList,
    anyOf: ["isClinical", "isAdmin"],
  },
  {
    id: "beds",
    label: "Rooms, beds & service points",
    description: "Bed/ward reads via Experience BFF (inpatient module when deployed).",
    href: "/beds",
    Icon: BedDouble,
    anyOf: ["isClinical", "isQueueManager", "isAdmin"],
  },
  {
    id: "tasks",
    label: "Tasks & handover",
    description: "Shell task manager plus shift handover workspace.",
    href: "/shell/task-manager",
    Icon: Layers,
    anyOf: ["isClinical", "isQueueManager", "isAdmin"],
  },
  {
    id: "handover",
    label: "Shift handover",
    description: "Structured continuity between shifts.",
    href: "/shift/handover",
    Icon: Route,
    anyOf: ["isClinical", "isAdmin"],
  },
  {
    id: "inventory",
    label: "Resource availability (stock)",
    description: "Critical consumables and stock risk (Experience inventory plane).",
    href: "/inventory",
    Icon: Building2,
    anyOf: ["isClinical", "isDispenser", "isAdmin"],
  },
  {
    id: "alerts",
    label: "Operational alerts (assistant)",
    description: "BFF assistant notifications and comms hub entry.",
    href: "/home/notifications",
    Icon: Radio,
    everyone: true,
  },
  {
    id: "telemedicine",
    label: "Telemedicine & referrals",
    description: "Teleconsult sessions tied to coordination queues.",
    href: "/telemedicine",
    Icon: Video,
    anyOf: ["isClinical", "isAdmin"],
  },
  {
    id: "reports",
    label: "Reports & daily summaries",
    description: "Operational and financial reporting entry (role-gated downstream).",
    href: "/reports",
    Icon: BarChart3,
    anyOf: ["isAdmin", "isFinance", "isClinical"],
  },
];

export function filterFacilityOpsNavCards(roles: FacilityOpsRoleMask): FacilityOpsNavCard[] {
  return CARDS.filter((card) => {
    if (card.everyone) return true;
    if (!card.anyOf?.length) return false;
    return card.anyOf.some((key) => roles[key]);
  });
}
