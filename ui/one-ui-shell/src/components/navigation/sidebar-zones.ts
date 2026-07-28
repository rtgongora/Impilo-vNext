import type { LucideIcon } from "lucide-react";
import {
  Ambulance,
  ArrowUpRight,
  Bell,
  BookMarked,
  Boxes,
  Brain,
  BriefcaseBusiness,
  Building2,
  Calendar,
  ClipboardList,
  Code2,
  CreditCard,
  Droplet,
  FileBarChart2,
  FileText,
  FlaskConical,
  FolderOpen,
  GraduationCap,
  Heart,
  HeartHandshake,
  IdCard,
  LayoutDashboard,
  Layers,
  LifeBuoy,
  Lightbulb,
  MessageSquare,
  Monitor,
  Package,
  Pill,
  Radio,
  Search,
  Settings2,
  Shield,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  User,
  Users,
  UsersRound,
  Wallet,
} from "lucide-react";
import type { SessionExperienceContract } from "@/lib/trust";
import { hasAdministrationGovernanceEntry } from "@/lib/administration-governance";
import { expandRoleGroup } from "@/lib/auth/privileged-roles";

/**
 * Zone navigation config — single source for the off-canvas drawer
 * (ExperienceSidebar) and the persistent NavRail. Extracted so both render
 * the same governed items with the same role gating.
 */

export interface SidebarItem {
  href: string;
  label: string;
  icon: LucideIcon;
  requiredRoles?: string[];
}

export interface SidebarZone {
  id: "work" | "professional" | "life";
  label: string;
  items: SidebarItem[];
}

export const ADMIN_ROLES = expandRoleGroup(["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"]);
export const FINANCE_ROLES = expandRoleGroup(["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"]);
export const CLINICAL_ROLES = expandRoleGroup(["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
export const QUEUE_ROLES = expandRoleGroup(["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
export const DISPENSER_ROLES = expandRoleGroup(["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
export const PUBLIC_HEALTH_ROLES = expandRoleGroup(["PUBLIC_HEALTH_OFFICER", "ENV_HEALTH", "CHW", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
export const OPERATOR_SWITCH_ROLES = expandRoleGroup(["FACILITY_ADMIN", "SYSTEM_ADMIN", "FINANCE", "SUPER_ADMIN"]);
// Vashandi workforce management — mirrors the WORKFORCE_ADMIN role group used by the app registry.
export const WORKFORCE_ROLES = expandRoleGroup(["HR_OFFICER", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);

export const ZONES: SidebarZone[] = [
  {
    id: "work",
    label: "Work",
    items: [
      { href: "/clinical", label: "Clinical Hub", icon: Stethoscope, requiredRoles: CLINICAL_ROLES },
      { href: "/clinical-tools", label: "Clinical References", icon: BookMarked, requiredRoles: CLINICAL_ROLES },
      { href: "/clinical/emergency", label: "ED / Casualty", icon: Ambulance, requiredRoles: QUEUE_ROLES },
      { href: "/queue", label: "Queue", icon: Users, requiredRoles: QUEUE_ROLES },
      { href: "/telemedicine", label: "Telemedicine", icon: Stethoscope, requiredRoles: CLINICAL_ROLES },
      { href: "/communication/secure-messaging", label: "Secure Messaging", icon: MessageSquare, requiredRoles: QUEUE_ROLES },
      { href: "/beds", label: "Bed Management", icon: Building2, requiredRoles: CLINICAL_ROLES },
      { href: "/scheduling", label: "Scheduling", icon: Calendar, requiredRoles: CLINICAL_ROLES },
      { href: "/work/vashandi", label: "Vashandi Workforce", icon: UsersRound, requiredRoles: WORKFORCE_ROLES },
      { href: "/pharmacy", label: "Pharmacy", icon: Pill, requiredRoles: DISPENSER_ROLES },
      { href: "/inventory", label: "Inventory", icon: Package },
      { href: "/enterprise", label: "Enterprise resources", icon: Boxes },
      { href: "/erp", label: "Institutional ERP", icon: Wallet, requiredRoles: FINANCE_ROLES },
      { href: "/marketplace", label: "Marketplace", icon: BriefcaseBusiness },
      { href: "/finance", label: "Finance", icon: Wallet, requiredRoles: FINANCE_ROLES },
      // Absorbed sidecars: oros-web → /lab
      { href: "/lab", label: "Laboratory", icon: FlaskConical, requiredRoles: CLINICAL_ROLES },
      { href: "/madi", label: "Madi", icon: Droplet, requiredRoles: CLINICAL_ROLES },
      { href: "/work/mental-health", label: "Mental Health", icon: Brain, requiredRoles: CLINICAL_ROLES },
      { href: "/live", label: "Impilo Live", icon: Radio, requiredRoles: CLINICAL_ROLES },
    ],
  },
  {
    id: "professional",
    label: "My Professional",
    items: [
      { href: "/professional", label: "Professional Profile", icon: Stethoscope },
      { href: "/learning", label: "Impilo Fundo", icon: GraduationCap },
      { href: "/live/cpd", label: "Impilo Live CPD", icon: Radio },
      { href: "/home/credentials", label: "Credentials", icon: ClipboardList },
      { href: "/registry-admin", label: "Registry plane", icon: ShieldCheck, requiredRoles: ["SYSTEM_ADMIN", "HIE_ADMIN"] },
      { href: "/registry", label: "Registry", icon: Building2 },
      { href: "/public-health", label: "Public Health", icon: Heart, requiredRoles: PUBLIC_HEALTH_ROLES },
      { href: "/operations/facility-operations", label: "Facility Operations", icon: Building2, requiredRoles: ADMIN_ROLES },
      { href: "/organization-admin", label: "Org administration", icon: BriefcaseBusiness, requiredRoles: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER", "FINANCE"] },
      // Phase-E2 governance spine (E2-GOV-UI) — appended entries.
      { href: "/platform-origin", label: "Platform Origin", icon: ShieldCheck, requiredRoles: ["PLATFORM_ORIGIN_ADMINISTRATOR", "NATIONAL_ADMINISTRATOR", "SYSTEM_ADMIN"] },
      { href: "/organization-admin/onboarding", label: "Org Onboarding", icon: BriefcaseBusiness, requiredRoles: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER", "FINANCE"] },
      { href: "/reports", label: "Reports", icon: FileBarChart2 },
      { href: "/admin", label: "Administration", icon: Shield, requiredRoles: ADMIN_ROLES },
      // Absorbed sidecars: ops-console → /operations, developer-console → /developer
      { href: "/operations", label: "Operations", icon: Settings2, requiredRoles: ADMIN_ROLES },
      { href: "/developer", label: "Developer Portal", icon: Code2, requiredRoles: ADMIN_ROLES },
      { href: "/admin/clinical-curation", label: "Knowledge curation", icon: Lightbulb, requiredRoles: ADMIN_ROLES },
      { href: "/admin/sidecar-retirement", label: "Sidecar ledger", icon: ArrowUpRight, requiredRoles: ADMIN_ROLES },
      { href: "/settings", label: "Settings", icon: CreditCard },
      // E2-TRUST: provider self-service claim / recovery (page already exists — nav discoverability).
      { href: "/citizen/provider-claim", label: "Provider Claim", icon: Stethoscope },
      // IATG Wave 2, WS-E: facility administrator self-service claim.
      { href: "/facility/claim", label: "Claim facility admin", icon: ShieldCheck },
    ],
  },
  {
    id: "life",
    label: "My Life",
    items: [
      { href: "/home", label: "Home", icon: LayoutDashboard },
      // Health OS §2a: Intelligent experience layer
      { href: "/social", label: "Social timeline", icon: Users },
      { href: "/communities", label: "Communities", icon: HeartHandshake },
      { href: "/pages", label: "Pages", icon: BookMarked },
      { href: "/ask", label: "Ask", icon: MessageSquare },
      { href: "/intelligence", label: "Intelligence", icon: Sparkles },
      { href: "/search", label: "Search", icon: Search },
      { href: "/guidance", label: "Guidance", icon: Lightbulb },
      { href: "/citizen", label: "Citizen services", icon: IdCard },
      // Health OS Enterprise Plane (finance domain): Mushe digital wallet
      { href: "/wallet", label: "My Wallet", icon: Wallet },
      // Health OS §2: Wellness — prevention, self-care, fitness
      { href: "/wellness", label: "Wellness", icon: Sparkles },
      { href: "/madi/donor", label: "Blood donation", icon: Droplet },
      { href: "/live/discover", label: "Live Health Talks", icon: Radio },
      // Health OS §4: Caregiving — delegated care, family, dependants
      { href: "/caregiving", label: "Caregiving", icon: HeartHandshake },
      // Health OS §2: Remote monitoring — devices, chronic care, readings
      { href: "/monitoring", label: "Monitoring", icon: Monitor },
      // Health OS §2: Service discovery — find providers, facilities, services
      { href: "/discover", label: "Discover", icon: Search },
      { href: "/share/claim", label: "Claim shared docs", icon: ClipboardList },
      { href: "/home/notifications", label: "Notifications", icon: Bell },
      { href: "/home/profile", label: "Profile", icon: User },
      { href: "/home/preferences", label: "Preferences", icon: Heart },
      { href: "/home/medications", label: "Medications", icon: Pill },
      { href: "/home/bookings", label: "My Bookings", icon: Calendar },
      { href: "/home/appointments", label: "My Appointments", icon: Calendar },
      { href: "/home/documents", label: "Documents", icon: FileText },
      { href: "/shell/file-manager", label: "File manager", icon: FolderOpen },
      { href: "/shell/task-manager", label: "Task manager", icon: Layers },
      // Absorbed sidecar: support-console → /support
      { href: "/support", label: "Support", icon: LifeBuoy },
      // E2-TRUST: four-block doctrine trust profile (identity/professional/employment/operational).
      { href: "/citizen/wallet/trust", label: "Trust Profile", icon: ShieldCheck },
      // RJ-2: self-service provider access — reachable by a Health-ID person with no provider context yet.
      { href: "/citizen/provider-claim", label: "Request Provider Access", icon: Stethoscope },
    ],
  },
];

export function matchesPath(pathname: string, href: string) {
  if (href === "/home") {
    return pathname === "/" || pathname === "/home" || pathname.startsWith("/home/");
  }

  return pathname === href || pathname.startsWith(`${href}/`);
}

/**
 * Zone filtering + ordering + role gating shared by drawer and rail.
 * Mirrors the historical ExperienceSidebar behaviour exactly.
 */
export function computeVisibleZones(args: {
  citizenOnly: boolean;
  sessionWorkZone: boolean;
  focusedWorkMode: boolean;
  activeZoneId?: SidebarZone["id"];
  contract: SessionExperienceContract | undefined;
  hasRole: (role: string) => boolean;
}): SidebarZone[] {
  const { citizenOnly, sessionWorkZone, focusedWorkMode, activeZoneId, contract, hasRole } = args;

  const orderedZones = [...ZONES]
    .filter((zone) => {
      if (focusedWorkMode) {
        return zone.id === "work";
      }
      // Citizens only see "life" unless the BFF session contract grants work/professional tabs.
      if (citizenOnly && zone.id !== "life") {
        if (zone.id === "work" && sessionWorkZone) return true;
        if (zone.id === "professional" && contract?.tabs?.professional?.visible) return true;
        return false;
      }
      return true;
    })
    .sort((left, right) => {
      if (!activeZoneId) return 0;
      if (left.id === activeZoneId) return -1;
      if (right.id === activeZoneId) return 1;
      return 0;
    });

  return orderedZones
    .map((zone) => {
      let items = zone.items.filter((item) => {
        if (!item.requiredRoles) return true;
        return item.requiredRoles.some((role) => hasRole(role));
      });
      if (zone.id === "work" && citizenOnly && contract && hasAdministrationGovernanceEntry(contract)) {
        items = [
          { href: "/work/administration-governance", label: "Administration & Governance", icon: ShieldCheck },
        ];
      } else if (zone.id === "work" && contract && hasAdministrationGovernanceEntry(contract)) {
        items = [
          { href: "/work/administration-governance", label: "Administration & Governance", icon: ShieldCheck },
          ...items,
        ];
      }
      return {
        ...zone,
        items,
      };
    })
    .filter((zone) => zone.items.length > 0);
}
