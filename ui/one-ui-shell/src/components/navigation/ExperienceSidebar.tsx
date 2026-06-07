"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import {
  Activity,
  ArrowLeftRight,
  ArrowUpRight,
  Ambulance,
  Bell,
  BookMarked,
  Boxes,
  BriefcaseBusiness,
  Building2,
  Calendar,
  ClipboardList,
  Code2,
  CreditCard,
  FileBarChart2,
  FileText,
  FolderOpen,
  FlaskConical,
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
  Route,
  Search,
  Settings2,
  Shield,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  User,
  Users,
  Wallet,
  X,
} from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { ProviderActivationBanner } from "@/components/ProviderActivationBanner";
import { WorkspaceSwitcher } from "@/components/WorkspaceSwitcher";
import { useAuthStore, type AuthUser } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { WORK_MODE_LABELS } from "@/hooks/useWorkModeStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { expandRoleGroup } from "@/lib/auth/privileged-roles";

interface SidebarItem {
  href: string;
  label: string;
  icon: LucideIcon;
  requiredRoles?: string[];
}

interface SidebarZone {
  id: "work" | "professional" | "life";
  label: string;
  items: SidebarItem[];
}

interface SidebarSpotlight {
  title: string;
  description: string;
  tone: string;
  actions: Array<{
    href: string;
    label: string;
    icon: LucideIcon;
  }>;
}

const ADMIN_ROLES = expandRoleGroup(["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"]);
const FINANCE_ROLES = expandRoleGroup(["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"]);
const CLINICAL_ROLES = expandRoleGroup(["CLINICIAN", "NURSE", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
const QUEUE_ROLES = expandRoleGroup(["CLINICIAN", "NURSE", "SUPPORT_AGENT", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
const DISPENSER_ROLES = expandRoleGroup(["PHARMACIST", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
const PUBLIC_HEALTH_ROLES = expandRoleGroup(["PUBLIC_HEALTH_OFFICER", "ENV_HEALTH", "CHW", "FACILITY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER"]);
const OPERATOR_SWITCH_ROLES = expandRoleGroup(["FACILITY_ADMIN", "SYSTEM_ADMIN", "FINANCE", "SUPER_ADMIN"]);

/**
 * Returns true when the user should see citizen-only experience.
 *
 * Health OS Identity Doctrine: everyone starts in citizen mode.
 * Professional zones (Work, My Professional) only appear when
 * the user has explicitly activated their Provider ID.
 */
function isCitizenOnly(user: AuthUser | null): boolean {
  if (!user) return true;
  // If provider is activated, show professional shell
  if (user.providerActivated) return false;
  // If user has clinical/admin roles, show professional shell (backward compat
  // for sessions where providerActivated hasn't been set yet)
  const professionalRoles = [...CLINICAL_ROLES, ...FINANCE_ROLES, ...ADMIN_ROLES, "PHARMACIST"];
  if (user.roles?.some((r: string) => professionalRoles.includes(r))) return false;
  // Otherwise, citizen-only experience
  return true;
}

const ZONES: SidebarZone[] = [
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
      { href: "/pharmacy", label: "Pharmacy", icon: Pill, requiredRoles: DISPENSER_ROLES },
      { href: "/inventory", label: "Inventory", icon: Package },
      { href: "/enterprise", label: "Enterprise resources", icon: Boxes },
      { href: "/erp", label: "Institutional ERP", icon: Wallet, requiredRoles: FINANCE_ROLES },
      { href: "/marketplace", label: "Marketplace", icon: BriefcaseBusiness },
      { href: "/finance", label: "Finance", icon: Wallet, requiredRoles: FINANCE_ROLES },
      // Absorbed sidecars: oros-web → /lab
      { href: "/lab", label: "Laboratory", icon: FlaskConical, requiredRoles: CLINICAL_ROLES },
    ],
  },
  {
    id: "professional",
    label: "My Professional",
    items: [
      { href: "/professional", label: "Professional Profile", icon: Stethoscope },
      { href: "/learning", label: "Impilo Fundo", icon: GraduationCap },
      { href: "/home/credentials", label: "Credentials", icon: ClipboardList },
      { href: "/registry-admin", label: "Registry plane", icon: ShieldCheck, requiredRoles: ["SYSTEM_ADMIN", "HIE_ADMIN"] },
      { href: "/registry", label: "Registry", icon: Building2 },
      { href: "/public-health", label: "Public Health", icon: Heart, requiredRoles: PUBLIC_HEALTH_ROLES },
      { href: "/operations/facility-operations", label: "Facility Operations", icon: Building2, requiredRoles: ADMIN_ROLES },
      { href: "/organization-admin", label: "Org administration", icon: BriefcaseBusiness, requiredRoles: ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER", "FINANCE"] },
      { href: "/reports", label: "Reports", icon: FileBarChart2 },
      { href: "/admin", label: "Administration", icon: Shield, requiredRoles: ADMIN_ROLES },
      // Absorbed sidecars: ops-console → /operations, developer-console → /developer
      { href: "/operations", label: "Operations", icon: Settings2, requiredRoles: ADMIN_ROLES },
      { href: "/developer", label: "Developer Portal", icon: Code2, requiredRoles: ADMIN_ROLES },
      { href: "/admin/clinical-curation", label: "Knowledge curation", icon: Lightbulb, requiredRoles: ADMIN_ROLES },
      { href: "/admin/sidecar-retirement", label: "Sidecar ledger", icon: ArrowUpRight, requiredRoles: ADMIN_ROLES },
      { href: "/settings", label: "Settings", icon: CreditCard },
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
      ],
  },
];

const ENTRY_STEPS = [
  { key: "auth", label: "Sign in" },
  { key: "facility", label: "Facility" },
  { key: "workspace", label: "Workspace" },
  { key: "shift", label: "Shift" },
] as const;

function matchesPath(pathname: string, href: string) {
  if (href === "/home") {
    return pathname === "/" || pathname === "/home" || pathname.startsWith("/home/");
  }

  return pathname === href || pathname.startsWith(`${href}/`);
}

function getSidebarSpotlight(pathname: string): SidebarSpotlight {
  if (pathname.startsWith("/registry-admin")) {
    return {
      title: "Registry governance plane",
      description: "Sovereign registry administration — elevated trust, separate from facility shift work.",
      tone: "border-[color:var(--warning)]/35 bg-[color:var(--warning-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/registry/intake", label: "Intake hub", icon: Route },
        { href: "/registry/providers", label: "Providers", icon: Users },
        { href: "/registry/facilities", label: "Facilities", icon: Building2 },
      ],
    };
  }

  if (pathname.startsWith("/wallet")) {
    return {
      title: "Mushe Wallet",
      description: "Digital wallet for health payments — send, receive, deposit, and manage smart cards.",
      tone: "border-[color:var(--primary)]/25 bg-[color:var(--primary-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/wallet", label: "Dashboard", icon: Wallet },
        { href: "/wallet/send", label: "Send Money", icon: ArrowUpRight },
        { href: "/wallet/cards", label: "Cards", icon: CreditCard },
      ],
    };
  }

  if (pathname.startsWith("/organization-admin")) {
    return {
      title: "Organization administration",
      description: "Facility and enterprise operations — policies, devices, reporting, settings.",
      tone: "border-[color:var(--nompilo)]/28 bg-[color:var(--nompilo-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/admin", label: "Admin home", icon: Shield },
        { href: "/reports", label: "Reports", icon: FileBarChart2 },
      ],
    };
  }

  if (
    pathname.startsWith("/ehr") ||
    pathname.startsWith("/queue") ||
    pathname.startsWith("/telemedicine") ||
    pathname.startsWith("/clinical")
  ) {
    return {
      title: "Clinical coordination",
      description: "Stay in the patient workflow with quick access to queue, ED activations, consults, and telemedicine.",
      tone: "border-[color:var(--primary)]/25 bg-[color:var(--primary-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/clinical/emergency", label: "ED / Casualty", icon: Ambulance },
        { href: "/queue", label: "Queue", icon: Users },
        { href: "/telemedicine", label: "Telemedicine", icon: Stethoscope },
      ],
    };
  }

  if (pathname.startsWith("/finance")) {
    return {
      title: "Finance operations",
      description: "Billing, payments, and claims are grouped together for faster revenue-cycle work.",
      tone: "border-[color:var(--info)]/25 bg-[color:var(--info-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/finance/billing", label: "Billing", icon: CreditCard },
        { href: "/finance/payments", label: "Payments", icon: Wallet },
      ],
    };
  }

  if (pathname.startsWith("/enterprise") || pathname.startsWith("/erp")) {
    return {
      title: "Enterprise resource plane",
      description:
        "Commodities, pharmacy stock, procurement, finance rails, logistics, and assets — scoped to your facility and roles, not isolated admin consoles.",
      tone: "border-[color:var(--warning)]/35 bg-[color:var(--warning-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/enterprise", label: "Enterprise dashboard", icon: LayoutDashboard },
        { href: "/inventory", label: "Inventory", icon: Package },
        { href: "/erp/procurement", label: "Procurement", icon: ClipboardList },
        { href: "/finance", label: "Finance", icon: Wallet },
      ],
    };
  }

  if (
    pathname.startsWith("/citizen") ||
    pathname.startsWith("/share/claim") ||
    pathname.startsWith("/verify/credential")
  ) {
    return {
      title: "Citizen self-service",
      description: "Health ID, recovery, public credential verification, and shared-document claim stay inside the same Experience shell.",
      tone: "border-[color:var(--primary)]/25 bg-[color:var(--surface-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/verify/credential", label: "Verify credential", icon: ShieldCheck },
        { href: "/citizen/health-id/qr", label: "Health ID QR", icon: IdCard },
        { href: "/citizen/id-recovery", label: "ID recovery", icon: IdCard },
        { href: "/share/claim", label: "Claim docs", icon: ClipboardList },
      ],
    };
  }

  if (pathname.startsWith("/operations/vito")) {
    return {
      title: "VITO — Identity Operations",
      description: "Client registry workflows: registration, issuance, smart cards, deduplication, biometrics, and recovery.",
      tone: "border-[color:var(--nompilo)]/25 bg-[color:var(--nompilo-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/operations/vito/registration", label: "Registration", icon: Users },
        { href: "/operations/vito/issuance", label: "Issuance", icon: IdCard },
        { href: "/operations/vito/cards", label: "Smart Cards", icon: CreditCard },
        { href: "/operations/vito/cards/pickup", label: "Card Pickup", icon: Package },
        { href: "/operations/vito/match", label: "Match Review", icon: ArrowLeftRight },
        { href: "/operations/vito/dedup", label: "Deduplication", icon: Layers },
        { href: "/operations/vito/patient-shares", label: "Patient Shares", icon: HeartHandshake },
        { href: "/operations/vito/print", label: "Print & Slips", icon: FileText },
        { href: "/operations/vito/internal-search", label: "Internal Search", icon: Search },
        { href: "/operations/vito/biometrics", label: "Biometrics", icon: ShieldCheck },
        { href: "/operations/vito/recovery", label: "Recovery", icon: Shield },
        { href: "/operations/vito/registry-admin", label: "Registry Admin", icon: Settings2 },
      ],
    };
  }

  if (
    pathname.startsWith("/registry") ||
    pathname.startsWith("/admin") ||
    pathname.startsWith("/reports")
  ) {
    return {
      title: "Professional oversight",
      description: "Reference, governance, and reporting — use Registry plane or Org administration for explicit operational context.",
      tone: "border-[color:var(--nompilo)]/25 bg-[color:var(--nompilo-soft)] text-[color:var(--text-primary)]",
      actions: [
        { href: "/registry/intake", label: "Registry intake", icon: Route },
        { href: "/admin/clinical-curation", label: "Knowledge curation", icon: Lightbulb },
        { href: "/registry-admin", label: "Registry plane", icon: ShieldCheck },
        { href: "/organization-admin", label: "Org admin", icon: BriefcaseBusiness },
      ],
    };
  }

  return {
    title: "One Experience Layer",
    description: "Move between work, professional, and personal contexts without losing your current facility and workspace state.",
    tone: "border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-primary)]",
    actions: [
      { href: "/home", label: "Home", icon: LayoutDashboard },
      { href: "/facility", label: "Facility", icon: Building2 },
    ],
  };
}

export function ExperienceSidebar() {
  const pathname = usePathname();
  const { user, hasRole } = useAuthStore();
  const { currentRoute, facility, workspace, stage, shiftActive, workMode } = useExperienceEntry();
  const navDrawerOpen = useShellStore((s) => s.navDrawerOpen);
  const setNavDrawerOpen = useShellStore((s) => s.setNavDrawerOpen);
  const [collapsed, setCollapsed] = useState(false);
  const [switcherOpen, setSwitcherOpen] = useState(false);

  useEffect(() => {
    if (typeof window === "undefined") return;
    setCollapsed(sessionStorage.getItem("exp:sidebar-collapsed") === "true");
  }, []);

  function toggleCollapsed() {
    const next = !collapsed;
    setCollapsed(next);
    sessionStorage.setItem("exp:sidebar-collapsed", String(next));
  }

  const citizenOnly = isCitizenOnly(user);

  const orderedZones = [...ZONES]
    .filter((zone) => {
      // Citizens only see the "life" zone — no work, no professional
      if (citizenOnly && zone.id !== "life") return false;
      return true;
    })
    .sort((left, right) => {
      const activeZone = currentRoute?.navZone;
      if (!activeZone) return 0;
      if (left.id === activeZone) return -1;
      if (right.id === activeZone) return 1;
      return 0;
    });

  const visibleZones = orderedZones.map((zone) => ({
    ...zone,
    items: zone.items.filter((item) => {
      if (!item.requiredRoles) return true;
      return item.requiredRoles.some((role) => hasRole(role));
    }),
  })).filter((zone) => zone.items.length > 0);
  const spotlight = getSidebarSpotlight(pathname);
  const activeZoneId = currentRoute?.navZone;

  const shellClasses = collapsed ? "w-[88px]" : "w-[320px]";

  function closeDrawer() {
    setNavDrawerOpen(false);
  }

  return (
    <>
      {navDrawerOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-40 bg-[color:var(--impilo-charcoal)]/35 backdrop-blur-[1px]"
          aria-label="Close navigation overlay"
          onClick={closeDrawer}
        />
      ) : null}

      <aside
        data-sidebar
        className={[
          "fixed inset-y-0 left-0 z-50 flex h-screen flex-col border-r text-[color:var(--text-primary)] transition-transform duration-200 impilo-subtle-african-accent",
          "border-[color:var(--border-soft)] bg-[linear-gradient(180deg,#f5fcf8_0%,#eef8f2_52%,#f7faf8_100%)]",
          shellClasses,
          navDrawerOpen ? "translate-x-0 shadow-2xl" : "-translate-x-full pointer-events-none",
        ].join(" ")}
      >
        <div className="flex items-center justify-between border-b border-[color:var(--border-soft)] px-4 py-4">
          {!collapsed ? (
            <div className="min-w-0">
              <Link href="/home" className="block" onClick={closeDrawer}>
                <ImpiloBrandLogo variant="full" tone="brand" size={22} />
              </Link>
              <p className="mt-1 text-xs text-[color:var(--text-muted)]">
                Health Operating System
              </p>
            </div>
          ) : (
            <Link href="/home" className="block" onClick={closeDrawer}>
              <ImpiloBrandLogo variant="mark" tone="brand" size={24} />
            </Link>
          )}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setSwitcherOpen(true)}
              className="inline-flex rounded-xl border border-[color:var(--border-soft)] bg-white p-2 text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--primary)]"
              aria-label="Switch context"
              title="Switch context"
            >
              <ArrowLeftRight className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={toggleCollapsed}
              className="hidden rounded-xl border border-[color:var(--border-soft)] bg-white px-3 py-2 text-xs text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)] md:inline-flex"
            >
              {collapsed ? "Expand" : "Collapse"}
            </button>
            <button
              type="button"
              onClick={closeDrawer}
              className="inline-flex rounded-xl border border-[color:var(--border-soft)] bg-white p-2 text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)]"
              aria-label="Close navigation"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Health OS §6: Provider activation banner — shown when linked Provider ID exists but not activated */}
        {!collapsed && <ProviderActivationBanner />}

        {!collapsed && !citizenOnly && (
          <div className="border-b border-[color:var(--border-soft)] px-4 py-4">
            <div className="rounded-3xl border border-[color:var(--border-soft)] bg-white p-4 shadow-impilo-card">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-[11px] uppercase tracking-[0.24em] text-[color:var(--text-muted)]">
                    Experience Entry
                  </p>
                  <p className="mt-2 text-sm font-semibold text-[color:var(--text-primary)]">
                    {currentRoute?.pageTitle ?? "Workspace"}
                  </p>
                </div>
                <span className="rounded-full border border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] px-2.5 py-1 text-[11px] font-semibold text-[color:var(--primary-hover)]">
                  {WORK_MODE_LABELS[workMode]}
                </span>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-2">
                {ENTRY_STEPS.map((entry) => {
                  const done =
                    entry.key === "auth"
                      ? stage !== "auth"
                      : entry.key === "facility"
                        ? Boolean(facility)
                        : entry.key === "workspace"
                          ? Boolean(workspace)
                          : shiftActive;

                  return (
                    <div
                      key={entry.key}
                      className={[
                        "rounded-2xl border px-3 py-2 text-xs",
                        done
                          ? "border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]"
                          : "border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-muted)]",
                      ].join(" ")}
                    >
                      {entry.label}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* Health OS §4: Role context switching — switch roles in-session (hidden for citizen-only) */}
        {!collapsed && user && !citizenOnly && (
          <div className="border-b border-[color:var(--border-soft)] px-4 py-3">
            <p className="text-[10px] uppercase tracking-widest text-[color:var(--text-muted)] mb-2">Active Role</p>
            <div className="flex items-center gap-2 mb-2">
              <ShieldCheck className="h-4 w-4 text-[color:var(--primary)]" />
              <span className="text-xs font-medium text-[color:var(--text-primary)]">{user.actorType}</span>
              {user.providerId && (
                <span className="text-[10px] bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)] rounded-full px-2 py-0.5">Provider</span>
              )}
            </div>
            <div className="flex gap-1 flex-wrap">
              {(["PROVIDER", "OPERATOR", "CITIZEN"] as const)
                .filter((r) => r !== user.actorType && user.roles.some((role) =>
                  r === "PROVIDER" ? ["CLINICIAN", "NURSE", "PHARMACIST"].includes(role) :
                  r === "OPERATOR" ? OPERATOR_SWITCH_ROLES.includes(role) :
                  true
                ))
                .map((role) => (
                  <Link
                    key={role}
                    href={role === "PROVIDER" ? "/provider/activate?returnTo=" + encodeURIComponent(pathname) : "/home"}
                    className="text-[10px] rounded-full border border-[color:var(--border-soft)] px-2 py-1 text-[color:var(--text-secondary)] hover:bg-white hover:text-[color:var(--text-primary)] transition"
                  >
                    Switch to {role.charAt(0) + role.slice(1).toLowerCase()}
                  </Link>
                ))
              }
            </div>
          </div>
        )}

        {!collapsed && !citizenOnly && (
          <div className="border-b border-[color:var(--border-soft)] px-4 py-4">
            <div className={`rounded-3xl border p-4 ${spotlight.tone}`}>
              <p className="text-[11px] uppercase tracking-[0.24em] text-slate-400">
                Context Spotlight
              </p>
              <p className="mt-2 text-sm font-semibold text-[color:var(--text-primary)]">{spotlight.title}</p>
              <p className="mt-1 text-xs leading-5 text-[color:var(--text-secondary)]">{spotlight.description}</p>
              <div className="mt-4 grid gap-2 sm:grid-cols-2">
                {spotlight.actions.map((action) => {
                  const Icon = action.icon;
                  return (
                    <Link
                      key={action.href}
                      href={action.href}
                      onClick={closeDrawer}
                      className="inline-flex items-center justify-between rounded-2xl border border-[color:var(--border-soft)] bg-white px-3 py-2 text-xs font-medium text-[color:var(--text-primary)] transition hover:bg-[color:var(--surface-soft)]"
                    >
                      <span className="inline-flex items-center gap-2">
                        <Icon className="h-3.5 w-3.5" />
                        {action.label}
                      </span>
                      <ArrowUpRight className="h-3.5 w-3.5 text-[color:var(--text-muted)]" />
                    </Link>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        <div className="flex-1 overflow-y-auto px-3 py-4">
          {visibleZones.map((zone) => (
            <section key={zone.id} className="mb-6">
              {!collapsed && (
                <div className="mb-2 flex items-center justify-between px-2">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-500">
                    {zone.label}
                  </p>
                  {activeZoneId === zone.id && (
                    <span className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-medium text-slate-300">
                      Active
                    </span>
                  )}
                </div>
              )}
              <div className="space-y-1">
                {zone.items.map((item) => {
                  const active = matchesPath(pathname, item.href);
                  const Icon = item.icon;

                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      onClick={closeDrawer}
                      title={collapsed ? item.label : undefined}
                      className={[
                        "group flex items-center gap-3 rounded-2xl px-3 py-3 text-sm transition",
                        active
                          ? "bg-white text-[color:var(--text-primary)] shadow-impilo-card"
                          : "text-[color:var(--text-secondary)] hover:bg-white hover:text-[color:var(--text-primary)]",
                        collapsed ? "justify-center" : "",
                      ].join(" ")}
                    >
                      <Icon className="h-4 w-4 shrink-0" />
                      {!collapsed && <span className="truncate">{item.label}</span>}
                    </Link>
                  );
                })}
              </div>
            </section>
          ))}
        </div>

        <div className="border-t border-[color:var(--border-soft)] px-4 py-4">
          {!collapsed ? (
            <div className="space-y-2 rounded-3xl border border-[color:var(--border-soft)] bg-white p-4 shadow-impilo-card">
              <div>
                <p className="text-[11px] uppercase tracking-[0.24em] text-[color:var(--text-muted)]">
                  {citizenOnly ? "My Account" : "Active Context"}
                </p>
                <p className="mt-2 truncate text-sm font-medium text-[color:var(--text-primary)]">
                  {user?.displayName ?? user?.email ?? "Impilo user"}
                </p>
              </div>
              {citizenOnly ? (
                <div className="flex gap-2 pt-1">
                  <Link
                    href="/home/profile"
                    onClick={closeDrawer}
                    className="inline-flex rounded-xl border border-[color:var(--border-soft)] px-3 py-2 text-xs text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)]"
                  >
                    Profile
                  </Link>
                  <Link
                    href="/settings"
                    onClick={closeDrawer}
                    className="inline-flex rounded-xl border border-[color:var(--border-soft)] px-3 py-2 text-xs text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)]"
                  >
                    Settings
                  </Link>
                </div>
              ) : (
              <>
              <div className="space-y-1 text-xs text-[color:var(--text-secondary)]">
                <p className="truncate">
                  Facility: {facility?.name ?? "Not selected"}
                </p>
                <p className="truncate">
                  Workspace: {workspace?.name ?? "Not selected"}
                </p>
                <p className={shiftActive ? "text-emerald-300" : "text-amber-300"}>
                  {shiftActive ? "Shift active" : "Shift not started"}
                </p>
              </div>
              <div className="flex gap-2 pt-1">
                <Link
                  href="/facility"
                  onClick={closeDrawer}
                    className="inline-flex rounded-xl border border-[color:var(--border-soft)] px-3 py-2 text-xs text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)]"
                >
                  Facility
                </Link>
                <Link
                  href="/workspace"
                  onClick={closeDrawer}
                    className="inline-flex rounded-xl border border-[color:var(--border-soft)] px-3 py-2 text-xs text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)]"
                >
                  Workspace
                </Link>
              </div>
              </>
              )}
            </div>
          ) : (
            <div className="flex flex-col items-center gap-3">
              {!citizenOnly && (
              <>
              <Link href="/facility" title={facility?.name ?? "Facility"} onClick={closeDrawer} className="rounded-2xl border border-[color:var(--border-soft)] bg-white p-3 text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)]">
                <Building2 className="h-4 w-4" />
              </Link>
              <Link href="/workspace" title={workspace?.name ?? "Workspace"} onClick={closeDrawer} className="rounded-2xl border border-[color:var(--border-soft)] bg-white p-3 text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)]">
                <BriefcaseBusiness className="h-4 w-4" />
              </Link>
              <div className={shiftActive ? "rounded-full bg-emerald-400 p-1" : "rounded-full bg-amber-400 p-1"}>
                <Activity className="h-3 w-3 text-slate-950" />
              </div>
              </>
              )}
            </div>
          )}
        </div>
      </aside>

      {/* Workspace Switcher — slide-out panel (Screen 15) */}
      <WorkspaceSwitcher open={switcherOpen} onClose={() => setSwitcherOpen(false)} />
    </>
  );
}
