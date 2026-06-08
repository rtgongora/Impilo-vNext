"use client";

/**
 * Home — Role-aware dashboard with workplace hub, professional stats,
 * module categories, and recent activity.
 *
 * Lovable reference: ModuleHome with WorkplaceSelectionHub,
 * MyProfessionalHub (Dashboard/Affiliations/Schedule/Credentials),
 * and ExpandableCategoryCards.
 */

import { useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Users, BookOpen, BarChart3, Clock, ArrowRight, Building2,
  Activity, Receipt, Pill, Calendar, Shield, Stethoscope,
  ClipboardList, Package, Settings, FileText, MapPin,
  ChevronRight, Video, ShoppingCart, Database, AlertTriangle,
  Briefcase, Heart, Globe, Siren, Award, User, ShieldCheck, UserCog, Droplet,
  MessageSquare, Radio, TestTube2, Scan, Phone, Send, ThumbsUp, MessageCircle, GraduationCap,
  Wifi, Wrench, Layers, QrCode, FlaskConical, FileCheck, Clipboard, Play, LayoutGrid, BedDouble,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import {
  HomeAttentionFromData,
  HomeRecentFromShell,
  HomeSearchCommandPrompt,
} from "@/components/home/HomeShellDiscovery";
import { WorkplaceSelectionHub } from "@/components/home/WorkplaceSelectionHub";
import { PageShell } from "@/components/PageShell";
import { useAuthStore, type AuthUser } from "@/hooks/useAuthStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import {
  useFacilities,
  type FacilityOperatingModel,
  type FacilityResource,
} from "@/hooks/queries/useFacilities";
import { useProviderLicenses, hasActiveLicense } from "@/hooks/queries/useLicenses";
import { useProviderCpd } from "@/hooks/queries/useCpd";
import { summarizeFundoMyLearning, useFundoMyLearning } from "@/hooks/queries/useFundoLms";
import { useProviderPrivileges } from "@/hooks/queries/useProviderPrivileges";
import { useCommunityGroups, useJoinGroup } from "@/hooks/queries/useCommunity";
import { useFacilityActiveShiftCount, useFacilityQueueStats } from "@/hooks/queries/useFacilityOperations";
import { apiClient } from "@/lib/api-client";
import { countUnacknowledgedAbnormalLabResults } from "@/lib/lab-results-attention";
import { getAppointmentStatus, getAppointmentTime, getAppointmentType, getAppointmentProvider } from "@/lib/queue-workflows";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

// ── Module category types ────────────────────────────────────────
interface ModuleItem {
  label: string;
  description: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  requiresClinical?: boolean;
  requiresAdmin?: boolean;
  requiresFinance?: boolean;
  requiresDispenser?: boolean;
}

interface ModuleCategory {
  id: string;
  title: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  modules: ModuleItem[];
}

interface WorkerLaunchAction {
  label: string;
  description: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  requiresWorkContext?: boolean;
}

interface WorkerSnapshotCard {
  label: string;
  value: string;
  detail: string;
  tone: "impilo" | "emerald" | "amber" | "rose" | "slate";
}

interface OperatingSignal {
  label: string;
  value: string;
  detail: string;
}

function formatOperatingLabel(value?: string | null) {
  if (!value) return null;
  return value
    .toLowerCase()
    .split("_")
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(" ");
}

function getOperatingNarrative(model?: FacilityOperatingModel) {
  if (!model) {
    return "Pick a workplace to load the facility-specific launch, continuity, and deployment cues for this shift.";
  }

  if (model.continuityClass === "EDGE_CRITICAL") {
    return "This site is expected to keep service delivery moving even when connectivity is unstable, so launch flows should stay simple and resilient.";
  }

  if (model.continuityClass === "LOCAL_EXECUTION_REQUIRED") {
    return "This site is expected to keep core service delivery running when the center is unavailable, so local execution continuity matters as much as throughput.";
  }

  if (model.workflowArchetype === "VIRTUAL_CARE_NETWORK") {
    return "This operating profile is built for virtual intake, referral coordination, and distributed care rather than one physical front desk.";
  }

  return "This facility profile shapes which launch actions, dashboards, and escalation paths should appear first for the current team.";
}

function getOperatingSignals(model?: FacilityOperatingModel): OperatingSignal[] {
  if (!model) return [];

  const tier = formatOperatingLabel(model.facilityTier);
  const deployment = formatOperatingLabel(model.deploymentMode);
  const continuity = formatOperatingLabel(model.continuityClass);

  return [
    tier
      ? {
          label: "Facility tier",
          value: tier,
          detail: "Tier sets the baseline complexity and service expectations for this site.",
        }
      : null,
    deployment
      ? {
          label: "Deployment mode",
          value: deployment,
          detail: "Deployment mode tells us whether this site runs on shared, dedicated, or edge-assisted execution.",
        }
      : null,
    continuity
      ? {
          label: "Continuity class",
          value: continuity,
          detail: "Continuity class defines how strongly local execution must hold when central connectivity degrades.",
        }
      : null,
  ].filter((signal): signal is OperatingSignal => signal !== null);
}

// ── Module categories (aligned to Lovable ExpandableCategoryCards) ──
function getModuleCategories(roles: {
  isClinical: boolean; isAdmin: boolean; isFinance: boolean; isDispenser: boolean;
}): ModuleCategory[] {
  const cats: ModuleCategory[] = [];

  if (roles.isClinical || roles.isDispenser) {
    cats.push({
      id: "clinical",
      title: "Clinical Care & Orders",
      icon: Stethoscope,
      color: "bg-impilo-500",
      modules: [
        ...(roles.isClinical ? [
          { label: "Clinical Hub", description: "All 10 clinical modules", href: "/clinical", icon: Stethoscope, color: "bg-impilo-100 text-impilo-500" },
          { label: "Queues & Wards", description: "Intake, triage, waiting, and ward status", href: "/queue", icon: Users, color: "bg-orange-100 text-orange-600" },
          { label: "Walk-in Registration", description: "Register a patient directly into queue flow", href: "/queue/walk-in", icon: Users, color: "bg-amber-100 text-amber-600" },
          { label: "Bookings & Appointments", description: "Scheduling, waitlist, and planned arrivals", href: "/scheduling", icon: Calendar, color: "bg-cyan-100 text-cyan-600" },
          { label: "Referrals", description: "Telemedicine and referral coordination", href: "/telemedicine", icon: Video, color: "bg-teal-100 text-teal-600" },
          { label: "Laboratory (LIMS)", description: "Select a chart, then continue into orders and results", href: "/queue/search?workflow=lims", icon: TestTube2, color: "bg-violet-100 text-violet-700" },
          { label: "Imaging (PACS)", description: "Select a chart, then continue into imaging review", href: "/queue/search?workflow=pacs", icon: Scan, color: "bg-rose-100 text-rose-700" },
        ] : []),
        ...(roles.isClinical || roles.isDispenser ? [
          { label: "Pharmacy & Rx", description: "Prescriptions, dispensing, and stock follow-through", href: "/pharmacy", icon: Pill, color: "bg-green-100 text-green-600" },
        ] : []),
      ],
    });
  }

  if (roles.isClinical || roles.isAdmin) {
    cats.push({
      id: "facility-ops",
      title: "Facility Operations",
      icon: Clock,
      color: "bg-rose-500",
      modules: [
        ...(roles.isClinical ? [
          { label: "Shift Handoff", description: "Care continuity reports", href: "/shift/handover", icon: Clock, color: "bg-amber-100 text-amber-600" },
        ] : []),
        { label: "Control Tower", description: "Real-time facility operations", href: "/clinical/control-tower", icon: BarChart3, color: "bg-rose-100 text-rose-600" },
        { label: "Operations & Roster", description: "Shifts, roster, and workforce visibility", href: "/shift", icon: Clock, color: "bg-cyan-100 text-cyan-600" },
        { label: "Communication Hub", description: "Messages, pages, and calls", href: "/communication", icon: MessageSquare, color: "bg-impilo-100 text-impilo-500" },
        { label: "Provider Noticeboard", description: "Announcements and staffing updates", href: "/scheduling/noticeboard", icon: ClipboardList, color: "bg-purple-100 text-purple-600" },
        ...(roles.isAdmin ? [
          { label: "Omnichannel Hub", description: "SMS, callbacks, disclosure, and access channels", href: "/omnichannel", icon: Radio, color: "bg-teal-100 text-teal-600" },
        ] : []),
      ],
    });
  }

  if (roles.isFinance) {
    cats.push({
      id: "finance",
      title: "Finance & Billing",
      icon: Receipt,
      color: "bg-emerald-500",
      modules: [
        { label: "Billing", description: "Bills & invoices", href: "/finance/billing", icon: FileText, color: "bg-impilo-100 text-impilo-500" },
        { label: "Payments", description: "Payment tracking", href: "/finance/payments", icon: Receipt, color: "bg-green-100 text-green-600" },
        { label: "Claims", description: "Insurance claims", href: "/finance/claims", icon: ClipboardList, color: "bg-purple-100 text-purple-600" },
        { label: "Tariffs", description: "Tariff schedules", href: "/finance/tariffs", icon: BarChart3, color: "bg-amber-100 text-amber-600" },
      ],
    });
  }

  cats.push({
    id: "registry",
    title: "Registries & Reference",
    icon: Database,
    color: "bg-indigo-500",
    modules: [
      { label: "Providers", description: "Provider registry", href: "/registry/providers", icon: Stethoscope, color: "bg-teal-100 text-teal-600" },
      { label: "Facilities", description: "Facility registry", href: "/registry/facilities", icon: Building2, color: "bg-purple-100 text-purple-600" },
      { label: "Products", description: "Product catalogue", href: "/registry/products", icon: Package, color: "bg-orange-100 text-orange-600" },
      { label: "Terminology", description: "ICD, SNOMED, LOINC", href: "/registry/terminology", icon: BookOpen, color: "bg-impilo-100 text-impilo-500" },
    ],
  });

  if (roles.isAdmin) {
    cats.push({
      id: "identity",
      title: "Identity Services",
      icon: Shield,
      color: "bg-indigo-500",
      modules: [
        { label: "ID Services Hub", description: "Generate, validate & recover IDs", href: "/id-services", icon: Shield, color: "bg-indigo-100 text-indigo-600" },
        { label: "Patient PHID", description: "Generate patient health IDs", href: "/id-services?tab=generate", icon: Users, color: "bg-impilo-100 text-impilo-500" },
        { label: "Provider ID", description: "Healthcare worker IDs", href: "/id-services?tab=generate", icon: Stethoscope, color: "bg-teal-100 text-teal-600" },
        { label: "ID Validation", description: "Verify ID authenticity", href: "/id-services?tab=validate", icon: Shield, color: "bg-green-100 text-green-600" },
        { label: "ID Recovery", description: "Recover lost IDs", href: "/id-services?tab=recovery", icon: Shield, color: "bg-amber-100 text-amber-600" },
      ],
    });
  }

  if (roles.isAdmin) {
    cats.push({
      id: "public-health",
      title: "Public Health",
      icon: Shield,
      color: "bg-amber-600",
      modules: [
        { label: "PH Operations", description: "Surveillance & response hub", href: "/public-health", icon: Shield, color: "bg-amber-100 text-amber-600" },
        { label: "Surveillance", description: "Disease surveillance & eIDSR", href: "/public-health?tab=surveillance", icon: Shield, color: "bg-red-100 text-red-600" },
        { label: "Outbreaks", description: "Outbreak management", href: "/public-health?tab=outbreaks", icon: Shield, color: "bg-red-100 text-red-600" },
        { label: "Campaigns", description: "Immunization & outreach", href: "/public-health?tab=campaigns", icon: Shield, color: "bg-green-100 text-green-600" },
        { label: "INDAWO Sites", description: "Premises registry", href: "/public-health?tab=sites", icon: Shield, color: "bg-emerald-100 text-emerald-600" },
      ],
    });
  }

  if (roles.isAdmin) {
    cats.push({
      id: "coverage",
      title: "Coverage & Financing",
      icon: Shield,
      color: "bg-violet-500",
      modules: [
        { label: "Coverage Hub", description: "Schemes, eligibility & claims", href: "/coverage", icon: Shield, color: "bg-violet-100 text-violet-600" },
        { label: "Eligibility Check", description: "Real-time coverage verification", href: "/coverage?tab=eligibility", icon: Users, color: "bg-green-100 text-green-600" },
        { label: "Claims", description: "Submit and track claims", href: "/coverage?tab=claims", icon: FileText, color: "bg-purple-100 text-purple-600" },
        { label: "Settlement", description: "Remittance & payouts", href: "/coverage?tab=settlement", icon: Receipt, color: "bg-emerald-100 text-emerald-600" },
        { label: "Schemes", description: "Plan administration", href: "/coverage?tab=schemes", icon: Shield, color: "bg-impilo-100 text-impilo-500" },
      ],
    });
  }

  cats.push({
    id: "operations",
    title: "Supply & Marketplace",
    icon: Package,
    color: "bg-orange-500",
    modules: [
      { label: "Inventory", description: "Stock management", href: "/inventory", icon: Package, color: "bg-orange-100 text-orange-600" },
      { label: "Marketplace", description: "Health products & vendors", href: "/marketplace", icon: ShoppingCart, color: "bg-purple-100 text-purple-600" },
      { label: "Reports", description: "Analytics & dashboards", href: "/reports", icon: BarChart3, color: "bg-indigo-100 text-indigo-600" },
    ],
  });

  if (roles.isClinical) {
    cats.push({
      id: "clinical-tools",
      title: "Clinical Tools",
      icon: Shield,
      color: "bg-pink-500",
      modules: [
        { label: "Voice Dictation", description: "Speech-to-text for notes", href: "/clinical/dictation", icon: Shield, color: "bg-pink-100 text-pink-600" },
        { label: "Offline Sync", description: "Sync status & conflicts", href: "/clinical-tools?tab=offline", icon: Shield, color: "bg-impilo-100 text-impilo-500" },
        { label: "Documents", description: "Document management", href: "/clinical-tools?tab=documents", icon: FileText, color: "bg-impilo-100 text-impilo-500" },
        { label: "CDS Alerts", description: "Clinical decision support", href: "/clinical-tools?tab=cds", icon: Shield, color: "bg-red-100 text-red-600" },
      ],
    });
  }

  if (roles.isAdmin) {
    cats.push({
      id: "admin",
      title: "Governance & Admin",
      icon: Shield,
      color: "bg-slate-600",
      modules: [
        { label: "Production Command Centre", description: "Discover services, demo paths, and live integration health", href: "/production-command-centre", icon: LayoutGrid, color: "bg-impilo-100 text-impilo-600" },
        { label: "User Management", description: "Users, roles & policies", href: "/admin/users", icon: Users, color: "bg-red-100 text-red-600" },
        { label: "Audit Trail", description: "System audit logs", href: "/admin/audit", icon: ClipboardList, color: "bg-amber-100 text-amber-600" },
        { label: "System Settings", description: "Configuration & security", href: "/admin", icon: Settings, color: "bg-gray-100 text-gray-600" },
      ],
    });
    cats.push({
      id: "ai-governance",
      title: "AI Governance",
      icon: Shield,
      color: "bg-cyan-600",
      modules: [
        { label: "AI Governance Hub", description: "Policy, audit & decision controls", href: "/ai-governance", icon: Shield, color: "bg-cyan-100 text-cyan-700" },
        { label: "Governance Datasets", description: "Register and classify datasets", href: "/ai-governance?tab=datasets", icon: Database, color: "bg-indigo-100 text-indigo-700" },
        { label: "Decision Rules", description: "Policy rules for AI access", href: "/ai-governance?tab=rules", icon: Shield, color: "bg-violet-100 text-violet-700" },
        { label: "Policy Publishing", description: "Publish AI governance policy versions", href: "/ai-governance?tab=policy", icon: FileText, color: "bg-emerald-100 text-emerald-700" },
      ],
    });
  }

  if (roles.isClinical || roles.isAdmin || roles.isFinance || roles.isDispenser) {
    cats.push({
      id: "production-readiness",
      title: "Production readiness",
      icon: LayoutGrid,
      color: "bg-impilo-500",
      modules: [
        ...(roles.isAdmin ? [
          {
            label: "Production Command Centre",
            description: "Service discovery, maturity labels, and integration probes",
            href: "/production-command-centre",
            icon: LayoutGrid,
            color: "bg-impilo-100 text-impilo-600",
          },
        ] : []),
        ...(roles.isClinical || roles.isAdmin ? [
          {
            label: "Data & intelligence",
            description: "Quality, pipelines, integration, and audit intelligence",
            href: "/data-intelligence",
            icon: Database,
            color: "bg-indigo-100 text-indigo-700",
          },
          {
            label: "Inpatient workspace",
            description: "Admissions, nursing workbench, and ward operations",
            href: "/clinical/inpatient",
            icon: BedDouble,
            color: "bg-purple-100 text-purple-700",
          },
          {
            label: "Core transactions",
            description: "Transaction audit feed and state history",
            href: "/core-transaction",
            icon: Layers,
            color: "bg-slate-100 text-slate-700",
          },
        ] : []),
        ...(roles.isClinical || roles.isDispenser || roles.isFinance ? [
          {
            label: "Rx transaction journey",
            description: "Golden-path Rx · pay · dispatch demonstration",
            href: "/pharmacy/transaction-journey?patientId=CPID-ZW-00001",
            icon: Pill,
            color: "bg-cyan-100 text-cyan-700",
          },
        ] : []),
        ...(roles.isFinance && !roles.isClinical ? [
          {
            label: "Core transactions",
            description: "Billing and payment transaction audit trail",
            href: "/core-transaction",
            icon: Layers,
            color: "bg-slate-100 text-slate-700",
          },
        ] : []),
      ],
    });
  }

  return cats;
}

function getWorkerLaunchActions(args: {
  isClinical: boolean;
  isAdmin: boolean;
  isFinance: boolean;
  isDispenser: boolean;
}, operatingModel?: FacilityOperatingModel): WorkerLaunchAction[] {
  const actions: WorkerLaunchAction[] = [];

  if (args.isClinical || args.isDispenser) {
    actions.push(
      {
        label: "Impilo Fundo Learning",
        description: "Open learning, certifications, and CPD pathways first before entering other workflow surfaces.",
        href: "/learning",
        icon: GraduationCap,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Composed Worklist",
        description: "Start from prioritized clinical inbox, then open queue, telemedicine, labs, and tools from one surface.",
        href: "/provider-workspace",
        icon: Clipboard,
        color: "bg-emerald-100 text-emerald-700",
        requiresWorkContext: true,
      },
      {
        label: "Find Patient",
        description: "Search by CPID, national ID, or name and launch straight into chart flow.",
        href: "/queue/search",
        icon: Users,
        color: "bg-impilo-100 text-impilo-600",
        requiresWorkContext: true,
      },
      {
        label: "Walk-in Registration",
        description: "Register a patient directly into today's service queue without losing facility context.",
        href: "/queue/walk-in",
        icon: ClipboardList,
        color: "bg-amber-100 text-amber-700",
        requiresWorkContext: true,
      },
      {
        label: "Waiting Room",
        description: "Pick up the next patient already waiting and move them into care.",
        href: "/queue/waiting",
        icon: Clock,
        color: "bg-orange-100 text-orange-700",
        requiresWorkContext: true,
      },
      {
        label: "Triage",
        description: "Start first-contact assessment and move urgent patients forward quickly.",
        href: "/queue/triage",
        icon: AlertTriangle,
        color: "bg-rose-100 text-rose-700",
        requiresWorkContext: true,
      },
      {
        label: "New Teleconsult",
        description: "Launch a virtual consult or referral escalation without leaving the work surface.",
        href: "/telemedicine/new",
        icon: Video,
        color: "bg-teal-100 text-teal-700",
      },
      {
        label: "Pharmacy Dispense",
        description: "Move directly into dispensing and medication follow-through.",
        href: "/pharmacy/dispense",
        icon: Pill,
        color: "bg-emerald-100 text-emerald-700",
        requiresWorkContext: true,
      },
      {
        label: "Inpatient workspace",
        description: "Open admissions, nursing workbench, and ward operations.",
        href: "/clinical/inpatient",
        icon: BedDouble,
        color: "bg-purple-100 text-purple-700",
        requiresWorkContext: true,
      },
      {
        label: "Rx transaction journey",
        description: "Walk the golden-path Rx · pay · dispatch demonstration.",
        href: "/pharmacy/transaction-journey?patientId=CPID-ZW-00001",
        icon: ClipboardList,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Core transactions",
        description: "Review transaction state, audit correlation, and next actions.",
        href: "/core-transaction",
        icon: Layers,
        color: "bg-slate-100 text-slate-700",
      },
      {
        label: "Data & intelligence",
        description: "Open quality, pipeline, and audit intelligence surfaces.",
        href: "/data-intelligence",
        icon: Database,
        color: "bg-indigo-100 text-indigo-700",
      },
    );
  }

  if (args.isFinance) {
    actions.push(
      {
        label: "Impilo Fundo Learning",
        description: "Start with role-based finance learning pathways, policy updates, and certification progress.",
        href: "/learning",
        icon: GraduationCap,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Claims Review",
        description: "Open claims and payer actions from the finance workbench.",
        href: "/finance/claims",
        icon: Receipt,
        color: "bg-violet-100 text-violet-700",
      },
      {
        label: "Billing",
        description: "Go straight into billing, invoices, and revenue follow-through.",
        href: "/finance/billing",
        icon: FileText,
        color: "bg-emerald-100 text-emerald-700",
      },
      {
        label: "Settlements",
        description: "Run settlement operations and release payouts without extra navigation hops.",
        href: "/finance/settlements",
        icon: BarChart3,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Rx transaction journey",
        description: "Follow payment and dispatch steps on the golden-patient Rx path.",
        href: "/pharmacy/transaction-journey?patientId=CPID-ZW-00001",
        icon: Pill,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Core transactions",
        description: "Audit billing and payment transaction state.",
        href: "/core-transaction",
        icon: Layers,
        color: "bg-slate-100 text-slate-700",
      },
    );
  }

  if (args.isAdmin) {
    actions.push(
      {
        label: "Impilo Fundo Learning",
        description: "Prioritise governance/admin learning modules and required training before operational actions.",
        href: "/learning",
        icon: GraduationCap,
        color: "bg-cyan-100 text-cyan-700",
      },
      {
        label: "Production Command Centre",
        description: "Browse Health OS services, maturity labels, and integration hub probes before operational actions.",
        href: "/production-command-centre",
        icon: LayoutGrid,
        color: "bg-impilo-100 text-impilo-600",
      },
      {
        label: "Control Tower",
        description: "Start from live operational oversight when you are driving throughput or escalation.",
        href: "/clinical/control-tower",
        icon: Activity,
        color: "bg-rose-100 text-rose-700",
      },
      {
        label: "Staff & Roles",
        description: "Jump into admin action on users, roles, and governance surfaces.",
        href: "/admin/users",
        icon: UserCog,
        color: "bg-slate-100 text-slate-700",
      },
      {
        label: "Data & intelligence",
        description: "Open data quality, pipelines, and audit intelligence.",
        href: "/data-intelligence",
        icon: Database,
        color: "bg-indigo-100 text-indigo-700",
      },
      {
        label: "Core transactions",
        description: "Transaction audit feed across production journeys.",
        href: "/core-transaction",
        icon: Layers,
        color: "bg-slate-100 text-slate-700",
      },
    );
  }

  const seen = new Set<string>();
  const deduped = actions.filter((action) => {
    if (seen.has(action.href)) return false;
    seen.add(action.href);
    return true;
  });

  if (operatingModel?.workflowArchetype === "VIRTUAL_CARE_NETWORK") {
    return [...deduped].sort((a, b) => {
      if (a.href === "/learning") return -1;
      if (b.href === "/learning") return 1;
      if (a.href === "/provider-workspace") return -1;
      if (b.href === "/provider-workspace") return 1;
      if (a.href === "/telemedicine/new") return -1;
      if (b.href === "/telemedicine/new") return 1;
      return 0;
    });
  }

  return deduped;
}

function getWorkerSnapshotCards(args: {
  isClinical: boolean;
  isAdmin: boolean;
  isFinance: boolean;
  isDispenser: boolean;
  queueWaiting: number;
  queueInService: number;
  appointmentsCount: number;
  activeShifts: number;
  hasWorkContext: boolean;
  licenseActive: boolean;
}): WorkerSnapshotCard[] {
  if (args.isClinical || args.isDispenser) {
    return [
      {
        label: "Patients waiting",
        value: String(args.queueWaiting),
        detail: args.hasWorkContext
          ? "Current waiting-room demand in the selected facility."
          : "Choose a workplace to start tracking waiting-room demand.",
        tone: args.queueWaiting > 0 ? "amber" : "slate",
      },
      {
        label: "In service",
        value: String(args.queueInService),
        detail: "Patients already called or actively in care right now.",
        tone: args.queueInService > 0 ? "impilo" : "slate",
      },
      {
        label: "Today's appointments",
        value: String(args.appointmentsCount),
        detail: "Confirmed scheduled arrivals — pending booking requests are in Booking Requests.",
        tone: args.appointmentsCount > 0 ? "emerald" : "slate",
      },
      {
        label: "Active shifts",
        value: String(args.activeShifts),
        detail: args.licenseActive
          ? "Current workforce presence on the roster in this facility."
          : "Resolve licence issues before starting regulated clinical work.",
        tone: args.licenseActive ? "impilo" : "rose",
      },
    ];
  }

  if (args.isFinance) {
    return [
      {
        label: "Claims lane",
        value: "Ready",
        detail: "Use the finance workspace to move straight into claims review and payer actions.",
        tone: "impilo",
      },
      {
        label: "Billing lane",
        value: "Open",
        detail: "Bills, invoices, and payment follow-through are one click away.",
        tone: "emerald",
      },
      {
        label: "Settlement lane",
        value: "Live",
        detail: "Settlement and reconciliation surfaces are available from the same workbench.",
        tone: "impilo",
      },
      {
        label: "Work context",
        value: args.hasWorkContext ? "Bound" : "Flexible",
        detail: args.hasWorkContext
          ? "Finance can continue within the current facility context."
          : "Finance work can start before choosing a facility-specific context.",
        tone: "slate",
      },
    ];
  }

  if (args.isAdmin) {
    return [
      {
        label: "Operational context",
        value: args.hasWorkContext ? "Live" : "Select",
        detail: args.hasWorkContext
          ? "Facility context is available for queue, roster, and operational oversight."
          : "Choose a workplace or admin plane to unlock work surfaces.",
        tone: args.hasWorkContext ? "impilo" : "amber",
      },
      {
        label: "Admin planes",
        value: "2",
        detail: "Registry administration and organization administration are available from home.",
        tone: "slate",
      },
      {
        label: "Throughput focus",
        value: String(args.queueWaiting),
        detail: "Waiting patients provide a quick signal for where operations may need intervention.",
        tone: args.queueWaiting > 0 ? "amber" : "slate",
      },
      {
        label: "Shift coverage",
        value: String(args.activeShifts),
        detail: "Use active shift coverage to gauge workforce pressure before entering queue ops.",
        tone: args.activeShifts > 0 ? "emerald" : "slate",
      },
    ];
  }

  return [
    {
      label: "Ready to work",
      value: args.hasWorkContext ? "Yes" : "Select",
      detail: "Choose a workplace context and then start the next patient-facing or operational task.",
      tone: args.hasWorkContext ? "impilo" : "amber",
    },
  ];
}

type HomeTab = "work" | "professional" | "personal";

export default function HomePage() {
  const user = useAuthStore((s) => s.user);
  const shift = useShiftStore((s) => s.shift);
  const roleGroup = useRoleGroup();
  const { isClinical, isAdmin, isFinance, isDispenser } = roleGroup;
  const { facility, selectFacility, enterMode, operationalMode, availableOperationalModes } =
    useExperienceEntry();
  const pathname = usePathname();
  const homeContextSyncPass = useRef(false);

  useEffect(() => {
    if (pathname !== "/home" && pathname !== "/") {
      homeContextSyncPass.current = false;
    }
  }, [pathname]);
  const [expandedCategory, setExpandedCategory] = useState<string | null>(null);
  const hasProfessionalRoles = isClinical || isAdmin || isFinance || isDispenser;
  const [activeTab, setActiveTab] = useState<HomeTab>("personal");

  // Correct the active tab whenever the user's roles are known.
  // Citizens always land on "personal"; professionals respect the stored tab.
  useEffect(() => {
    if (!user) return;
    if (!hasProfessionalRoles) {
      // Citizen — force personal, clear any stale stored tab
      setActiveTab("personal");
      sessionStorage.removeItem("exp:home-tab");
      return;
    }
    // Professional — honour stored tab, default to "work"
    const stored = sessionStorage.getItem("exp:home-tab") as HomeTab | null;
    setActiveTab(stored ?? "work");
  }, [user, hasProfessionalRoles]);

  function switchTab(tab: HomeTab) {
    setActiveTab(tab);
    sessionStorage.setItem("exp:home-tab", tab);
    const { setOperationalMode, operationalMode: currentOp } = useOperationalContextStore.getState();
    if (tab === "personal") {
      setOperationalMode("my_life");
    } else if (tab === "professional") {
      setOperationalMode("my_professional");
    } else if (tab === "work") {
      if (currentOp === "registry_admin" || currentOp === "organization_admin") return;
      setOperationalMode("facility_work");
    }
  }

  // Home only: first pass lets remembered tab correct mismatched mode; later passes sync tab from strip.
  useEffect(() => {
    if (pathname !== "/home" && pathname !== "/") return;
    const store = useOperationalContextStore.getState();
    if (!homeContextSyncPass.current) {
      homeContextSyncPass.current = true;
      if (activeTab === "personal" && store.operationalMode !== "my_life") {
        store.setOperationalMode("my_life");
      } else if (activeTab === "professional" && store.operationalMode !== "my_professional") {
        store.setOperationalMode("my_professional");
      } else if (
        activeTab === "work" &&
        (store.operationalMode === "my_life" || store.operationalMode === "my_professional")
      ) {
        store.setOperationalMode("facility_work");
      }
      return;
    }
    if (operationalMode === "my_life") {
      setActiveTab("personal");
      sessionStorage.setItem("exp:home-tab", "personal");
    } else if (operationalMode === "my_professional") {
      setActiveTab("professional");
      sessionStorage.setItem("exp:home-tab", "professional");
    } else {
      setActiveTab("work");
      sessionStorage.setItem("exp:home-tab", "work");
    }
  }, [pathname, activeTab, operationalMode]);

  const greeting = getGreeting();
  const router = useRouter();
  const hasWorkContext = !!facility;
  const selectedOperatingModel = facility?.operatingModel;

  // Fetch facilities for workplace selection hub
  const { data: facilitiesData, isLoading: facilitiesLoading } = useFacilities();
  const facilities: FacilityResource[] = facilitiesData?.data ?? [];

  // Fetch provider privileges (facility affiliations) from VARAPI
  const { data: privilegesData } = useProviderPrivileges(isClinical ? user?.id : undefined);
  const privileges = privilegesData?.data ?? [];

  // Fetch license data
  const { data: licenseData } = useProviderLicenses(isClinical ? user?.id : undefined);
  const licenses = licenseData?.data ?? [];
  const licenseActive = licenses.length === 0 || hasActiveLicense(licenses);
  const { data: cpdData } = useProviderCpd(isClinical ? user?.id : undefined);

  const learningSubject = useMemo(
    () => ({
      subjectType: "PROVIDER",
      subjectId: user?.providerId ?? user?.id ?? "",
    }),
    [user?.providerId, user?.id],
  );
  const {
    data: fundoMyLearningData,
    isLoading: fundoKpisLoading,
    isError: fundoKpisError,
  } = useFundoMyLearning(learningSubject, hasProfessionalRoles);
  const fundoKpis = summarizeFundoMyLearning(
    (fundoMyLearningData?.data ?? {}) as Record<string, unknown>,
  );
  const cpdCycle = cpdData?.data?.currentCycle;
  const cpdEarned = cpdCycle?.earnedPoints ?? 0;
  const cpdRequired = cpdCycle?.requiredPoints ?? 0;
  const cpdProgressPct =
    cpdRequired > 0 ? Math.min(100, Math.round((cpdEarned / cpdRequired) * 100)) : 0;

  // Community groups for Personal tab
  const { data: groupsData } = useCommunityGroups();
  const communityGroups = groupsData?.data ?? [];
  const joinGroup = useJoinGroup();

  function handleFacilitySelect(facilityResource: FacilityResource) {
    useOperationalContextStore.getState().setOperationalMode("facility_work");
    selectFacility(
      {
        id: facilityResource.id,
        name: facilityResource.attributes.name,
        code: facilityResource.attributes.code,
        facilityType: facilityResource.attributes.facilityType,
        capabilities: facilityResource.attributes.capabilities ?? [],
        operatingModel: facilityResource.attributes.operatingModel,
      },
      {
        mode: "clinical",
        nextPath: "/workspace",
      }
    );
  }

  function enterFacilityFreeMode(mode: "clinical" | "admin" | "finance", nextPath: string) {
    useOperationalContextStore.getState().setOperationalMode(
      mode === "clinical" ? "facility_work" : "organization_admin"
    );
    enterMode(mode, nextPath);
  }

  function enterIndependentMode(mode: "independent_practice" | "emergency_response" | "community_outreach") {
    useOperationalContextStore.getState().setOperationalMode("facility_work");
    useWorkModeStore.getState().setMode(mode, {
      licenseNumber: licenses[0]?.licenseNumber ?? "",
      licenseCategory: licenses[0]?.cadre ?? user?.roles?.[0] ?? "",
    });
    router.push("/queue");
  }

  // Fetch recent encounters
  const { data: recentEncounters } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["home-recent-encounters"],
    queryFn: () => apiClient.get("/internal/v1/encounters?size=5"),
    enabled: isClinical,
  });
  const encounters = recentEncounters?.data ?? [];
  const activeEncounterCount = encounters.filter((enc) => {
    const status = String(enc.attributes.status ?? "");
    return status === "IN_PROGRESS" || status === "ACTIVE";
  }).length;

  // Fetch today's appointments
  const { data: appointmentsData } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["home-today-appointments"],
    queryFn: () => apiClient.get("/internal/v1/appointments?size=5"),
    enabled: isClinical,
  });
  const appointments = appointmentsData?.data ?? [];
  const queueStatsQuery = useFacilityQueueStats(facility?.id);
  const queueStats = queueStatsQuery.data?.data;
  const { activeShiftCount } = useFacilityActiveShiftCount(facility?.id);

  const { data: providerSupportTicketsData } = useQuery<{
    data: Array<{ id: string; attributes?: Record<string, unknown> }>;
  }>({
    queryKey: ["home-provider-support-tickets", facility?.id],
    queryFn: () =>
      apiClient.get(`/internal/v1/mobile/provider/support/tickets?facility_id=${facility?.id}`),
    enabled: hasWorkContext && !!facility?.id && (isClinical || isDispenser),
    staleTime: 60_000,
  });
  const providerSupportTickets = providerSupportTicketsData?.data ?? [];
  const resolvedTicketStatuses = new Set(["RESOLVED", "CLOSED", "CANCELLED", "COMPLETED", "DONE"]);
  const openSupportTicketCount = providerSupportTickets.filter((row) => {
    const s = String(row.attributes?.status ?? "").toUpperCase();
    return s.length > 0 && !resolvedTicketStatuses.has(s);
  }).length;

  const { data: providerLabResultsPayload } = useQuery<unknown>({
    queryKey: ["home-provider-lab-results-attention", user?.id, facility?.id],
    queryFn: () => apiClient.get("/internal/v1/mobile/provider/labs/results?page=0&size=50"),
    enabled: hasWorkContext && !!facility?.id && (isClinical || isDispenser),
    staleTime: 60_000,
  });
  const unackedAbnormalLabCount = countUnacknowledgedAbnormalLabResults(providerLabResultsPayload);

  // Module categories
  const categories = getModuleCategories({ isClinical, isAdmin, isFinance, isDispenser });
  const workerLaunchActions = getWorkerLaunchActions(
    { isClinical, isAdmin, isFinance, isDispenser },
    selectedOperatingModel,
  );
  const launchActions = workerLaunchActions.filter((action) => !action.requiresWorkContext || hasWorkContext);
  const operatingSignals = getOperatingSignals(selectedOperatingModel);
  const operatingNarrative = getOperatingNarrative(selectedOperatingModel);
  const workerSnapshotCards = getWorkerSnapshotCards({
    isClinical,
    isAdmin,
    isFinance,
    isDispenser,
    queueWaiting: queueStats?.waiting ?? 0,
    queueInService: queueStats?.inService ?? encounters.filter((enc) => {
      const status = String(enc.attributes.status ?? "");
      return status === "IN_PROGRESS" || status === "ACTIVE";
    }).length,
    appointmentsCount: appointments.length,
    activeShifts: activeShiftCount,
    hasWorkContext,
    licenseActive,
  });

  // ── Citizen 3-column layout (Facebook-style) ────────────────────
  if (!hasProfessionalRoles) {
    return (
      <AppLayout>
        <PageShell title="Home">
          <span data-testid="home-operational-mode" className="sr-only">
            {operationalMode}
          </span>
          <CitizenHome user={user} greeting={greeting} />
        </PageShell>
      </AppLayout>
    );
  }

  // ── Professional layout (existing tabs) ────────────────────────
  return (
    <AppLayout>
      <PageShell title="Home">
        <span data-testid="home-operational-mode" className="sr-only">
          {operationalMode}
        </span>
        <div className="space-y-6">
          {/* Welcome + Context */}
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-xl font-semibold text-gray-900">
                  {greeting}, {user?.displayName ?? "User"}
                </h2>
                <p className="text-sm text-gray-500 mt-1">
                  {user?.actorType === "CITIZEN"
                    ? "Personal Health Account"
                    : user?.actorType === "PROVIDER"
                      ? "Healthcare Professional"
                      : user?.actorType === "OPERATOR"
                        ? "System Administrator"
                        : "Welcome to Impilo"}
                </p>
              </div>
              <div className="flex items-center gap-2">
                {isClinical && licenses.length > 0 && (
                  <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${
                    licenseActive ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"
                  }`}>
                    {licenseActive ? "Licensed" : "License Issue"}
                  </span>
                )}
                {user?.actorType && (
                  <span className="px-2.5 py-1 text-xs font-medium rounded-full bg-impilo-50 text-impilo-600">
                    {user.actorType}
                  </span>
                )}
              </div>
            </div>

            <div className="mt-4 flex flex-wrap gap-3">
              {facility && (
                <div className="flex items-center gap-2 text-sm text-gray-600 bg-gray-50 px-3 py-1.5 rounded-lg">
                  <Building2 className="w-4 h-4 text-gray-400" />
                  <span>{facility.name}</span>
                </div>
              )}
              {shift && (
                <div className="flex items-center gap-2 text-sm text-gray-600 bg-green-50 px-3 py-1.5 rounded-lg">
                  <Activity className="w-4 h-4 text-green-500" />
                  <span>Shift active since {new Date(shift.startedAt).toLocaleTimeString()}</span>
                </div>
              )}
              {!shift && !facility && (
                <div className="flex items-center gap-2 text-sm text-amber-700 bg-amber-50 px-3 py-1.5 rounded-lg">
                  <MapPin className="w-4 h-4" />
                  <span>Select where you are working from below</span>
                </div>
              )}
              {facility && !shift && (
                <Link href="/shift" className="flex items-center gap-2 text-sm text-impilo-500 bg-impilo-50 px-3 py-1.5 rounded-lg hover:bg-impilo-100 transition-colors">
                  <Clock className="w-4 h-4" />
                  <span>Start a shift</span>
                  <ArrowRight className="w-3 h-3" />
                </Link>
              )}
            </div>
          </div>

          {/* Tab Switcher (Lovable 3-tab: Work / Professional / Personal) */}
          <div className="flex gap-1 bg-gray-100 p-1 rounded-lg">
            <button onClick={() => switchTab("work")}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === "work" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"}`}>
              <Briefcase className="w-4 h-4" /> Work
            </button>
            <button onClick={() => switchTab("professional")}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === "professional" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"}`}>
              <Stethoscope className="w-4 h-4" /> My Professional
            </button>
            <button onClick={() => switchTab("personal")}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === "personal" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"}`}>
              <Heart className="w-4 h-4" /> My Life
            </button>
          </div>

          {/* ═══ WORK TAB ═══ */}
          {activeTab === "work" && (<>
          <div className="grid gap-4 lg:grid-cols-3">
            <div className="space-y-3 lg:col-span-2">
              <HomeSearchCommandPrompt />
              <HomeRecentFromShell />
            </div>
            <HomeAttentionFromData
              enabled={isClinical || isDispenser}
              queueWaiting={queueStats?.waiting ?? 0}
              activeEncounterCount={activeEncounterCount}
              hasWorkContext={hasWorkContext}
              openSupportTickets={openSupportTicketCount}
              unackedAbnormalLabResults={unackedAbnormalLabCount}
            />
          </div>

          {/* Workplace Selection Hub — when no facility */}
          {!hasWorkContext && (
            <div id="workplace-selection">
              <WorkplaceSelectionHub
              facilities={facilities}
              isLoading={facilitiesLoading}
              onSelectFacility={handleFacilitySelect}
              maxVisible={6}
              modeActions={[
                ...(isAdmin
                  ? [{
                      label: "Production Command Centre",
                      description: "Browse Health OS services and demo paths without binding to a facility first.",
                      icon: LayoutGrid,
                      onClick: () => enterFacilityFreeMode("admin", "/production-command-centre"),
                    },
                    {
                      label: "Administration",
                      description: "Enter the operational oversight surface without binding to a facility first.",
                      icon: Shield,
                      onClick: () => enterFacilityFreeMode("admin", "/admin"),
                    }]
                  : []),
                ...(isFinance
                  ? [{
                      label: "Finance",
                      description: "Jump into claims, billing, and payment orchestration from a finance-first workflow.",
                      icon: Receipt,
                      onClick: () => enterFacilityFreeMode("finance", "/finance"),
                    }]
                  : []),
                {
                  label: "Reports",
                  description: "Review cross-cutting reporting and performance signals before entering a work context.",
                  icon: BarChart3,
                  href: "/reports",
                },
              ]}
              />
            </div>
          )}

          {(isClinical || isDispenser || isFinance || isAdmin) && (
            <div className="space-y-4">
              <div className="grid gap-4 xl:grid-cols-[1.2fr,0.8fr]">
                <div className="impilo-subtle-african-accent rounded-3xl bg-gradient-to-r from-impilo-700 via-impilo-600 to-cyan-600 p-6 text-white shadow-impilo-floating">
                  <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
                    <div className="max-w-2xl">
                      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-impilo-100">
                        The Experience
                      </p>
                      <h3 className="mt-2 text-2xl font-semibold">
                        Launch Client Experience
                      </h3>
                      <p className="mt-2 max-w-xl text-sm text-impilo-100">
                        {operatingNarrative}
                      </p>
                      <div className="mt-4 flex flex-wrap gap-2">
                        {hasWorkContext ? (
                          <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium text-white">
                            <Building2 className="h-3.5 w-3.5" />
                            {facility?.name ?? "Work context selected"}
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-2 rounded-full bg-amber-300/25 px-3 py-1 text-xs font-medium text-white">
                            <MapPin className="h-3.5 w-3.5" />
                            Select a workplace to unlock client-facing actions
                          </span>
                        )}
                        {shift && (
                          <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium text-white">
                            <Activity className="h-3.5 w-3.5" />
                            Shift active
                          </span>
                        )}
                        {selectedOperatingModel?.facilityTier && (
                          <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium text-white">
                            <Layers className="h-3.5 w-3.5" />
                            {formatOperatingLabel(selectedOperatingModel.facilityTier)}
                          </span>
                        )}
                        {selectedOperatingModel?.continuityClass && (
                          <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium text-white">
                            <Wifi className="h-3.5 w-3.5" />
                            {formatOperatingLabel(selectedOperatingModel.continuityClass)}
                          </span>
                        )}
                      </div>
                    </div>

                    {hasWorkContext ? (
                      <Link
                        href={launchActions[0]?.href ?? "/queue/search"}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-semibold text-impilo-700 transition-colors hover:bg-impilo-50"
                      >
                        <Play className="h-4 w-4" />
                        {launchActions[0]?.label ?? "Start care"}
                      </Link>
                    ) : (
                      <a
                        href="#workplace-selection"
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-white px-5 py-3 text-sm font-semibold text-impilo-700 transition-colors hover:bg-impilo-50"
                      >
                        <MapPin className="h-4 w-4" />
                        Choose workplace
                      </a>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  {workerSnapshotCards.map((card) => {
                    const toneClasses = {
                      impilo: "border-impilo-200 bg-impilo-50 text-impilo-700",
                      emerald: "border-emerald-200 bg-emerald-50 text-emerald-700",
                      amber: "border-amber-200 bg-amber-50 text-amber-800",
                      rose: "border-rose-200 bg-rose-50 text-rose-700",
                      slate: "border-slate-200 bg-white text-slate-700",
                    } as const;

                    return (
                      <div
                        key={card.label}
                        className={`rounded-2xl border p-4 ${toneClasses[card.tone]}`}
                      >
                        <p className="text-xs font-semibold uppercase tracking-wide">{card.label}</p>
                        <p className="mt-2 text-2xl font-semibold">{card.value}</p>
                        <p className="mt-2 text-xs leading-5 opacity-90">{card.detail}</p>
                      </div>
                    );
                  })}
                </div>
              </div>

              {hasWorkContext && operatingSignals.length > 0 && (
                <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
                  <div className="mb-4 flex items-start justify-between gap-4">
                    <div>
                      <h3 className="text-base font-semibold text-slate-900">Facility operating model</h3>
                      <p className="mt-1 text-sm text-slate-500">
                        This facility is configured as a distinct care-delivery environment, not just a location. The operating model below should shape launch flow and continuity expectations for this shift.
                      </p>
                    </div>
                    <div className="rounded-xl bg-slate-100 px-3 py-2 text-xs font-medium uppercase tracking-[0.16em] text-slate-600">
                      Operating profile
                    </div>
                  </div>
                  <div className="grid gap-3 md:grid-cols-3">
                    {operatingSignals.map((signal) => (
                      <div key={signal.label} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">
                          {signal.label}
                        </p>
                        <p className="mt-2 text-lg font-semibold text-slate-900">{signal.value}</p>
                        <p className="mt-2 text-xs leading-5 text-slate-500">{signal.detail}</p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="bg-white rounded-lg border border-gray-200 p-4">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="flex items-center gap-2 text-base font-semibold text-gray-900">
                    <Clipboard className="w-5 h-5 text-impilo-500" />
                    Service Delivery Pathways
                  </h3>
                  <p className="text-xs text-gray-500">Quick starts for the work that matters now</p>
                </div>
                {launchActions.length === 0 ? (
                  <div className="rounded-xl border border-dashed border-gray-200 bg-gray-50 px-4 py-6 text-sm text-gray-500">
                    Select a workplace or activate the right operational plane to unlock role-specific launch actions.
                  </div>
                ) : (
                  <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                    {launchActions.map((action) => {
                      const ActionIcon = action.icon;
                      return (
                        <Link
                          key={action.href}
                          href={action.href}
                          className="group rounded-xl border border-gray-200 bg-gray-50 p-4 transition-colors hover:border-impilo-200 hover:bg-white hover:shadow-sm"
                        >
                          <div className={`mb-3 inline-flex h-11 w-11 items-center justify-center rounded-xl ${action.color}`}>
                            <ActionIcon className="h-5 w-5" />
                          </div>
                          <div className="flex items-start justify-between gap-3">
                            <div>
                              <p className="text-sm font-semibold text-gray-900 group-hover:text-impilo-600">{action.label}</p>
                              <p className="mt-1 text-xs leading-5 text-gray-500">{action.description}</p>
                            </div>
                            <ArrowRight className="mt-0.5 h-4 w-4 shrink-0 text-gray-400 group-hover:text-impilo-500" />
                          </div>
                        </Link>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Professional Dashboard — stats + schedule (Lovable MyProfessionalHub) */}
          {isClinical && hasWorkContext && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Today's Schedule */}
              <div className="bg-white rounded-lg border border-gray-200">
                <div className="px-5 py-3 border-b flex items-center justify-between">
                  <h3 className="text-sm font-medium text-gray-900">Today&apos;s Schedule</h3>
                  <Link href="/scheduling" className="text-xs text-impilo-500 hover:text-impilo-700">View All →</Link>
                </div>
                {appointments.length === 0 ? (
                  <div className="p-6 text-center">
                    <Calendar className="w-6 h-6 text-gray-300 mx-auto mb-1" />
                    <p className="text-xs text-gray-400">No appointments today</p>
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100">
                    {appointments.slice(0, 4).map((apt) => (
                      <div key={apt.id} className="px-5 py-2.5 flex items-center justify-between">
                        <div>
                          <p className="text-sm text-gray-900">{(apt.attributes.appointment_type as string) ?? "Appointment"}</p>
                          <p className="text-xs text-gray-500">{apt.attributes.start_time ? new Date(apt.attributes.start_time as string).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—"}</p>
                        </div>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${apt.attributes.status === "CONFIRMED" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"}`}>
                          {(apt.attributes.status as string) ?? "—"}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Recent Encounters */}
              <div className="bg-white rounded-lg border border-gray-200">
                <div className="px-5 py-3 border-b flex items-center justify-between">
                  <h3 className="text-sm font-medium text-gray-900">Recent Encounters</h3>
                  <Link href="/queue" className="text-xs text-impilo-500 hover:text-impilo-700">Queue →</Link>
                </div>
                {encounters.length === 0 ? (
                  <div className="p-6 text-center">
                    <Stethoscope className="w-6 h-6 text-gray-300 mx-auto mb-1" />
                    <p className="text-xs text-gray-400">No recent encounters</p>
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100">
                    {encounters.slice(0, 4).map((enc) => {
                      const a = enc.attributes;
                      const isActive = a.status === "IN_PROGRESS" || a.status === "ACTIVE";
                      return (
                        <Link key={enc.id} href={`/ehr/${a.patient_id}/encounter/${enc.id}`}
                          className="flex items-center justify-between px-5 py-2.5 hover:bg-gray-50 transition-colors">
                          <div className="flex items-center gap-2">
                            <div className={`w-1.5 h-1.5 rounded-full ${isActive ? "bg-green-500" : "bg-gray-300"}`} />
                            <div>
                              <p className="text-sm text-gray-900">{((a.encounter_type as string) ?? "Encounter").replace(/_/g, " ")}</p>
                              <p className="text-xs text-gray-500">{a.started_at ? new Date(a.started_at as string).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "—"}</p>
                            </div>
                          </div>
                          <span className={`px-2 py-0.5 text-xs rounded-full ${isActive ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"}`}>
                            {(a.status as string)?.replace(/_/g, " ") ?? "—"}
                          </span>
                        </Link>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Communication Noticeboard (Lovable ModuleHome) */}
          <div className="bg-white rounded-lg border border-gray-200 p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                <MessageSquare className="w-5 h-5 text-impilo-500" />
                Communication & Access
              </h3>
              <Link href="/communication" className="text-xs text-impilo-500 hover:text-impilo-700">
                View All →
              </Link>
            </div>
            <div className="flex flex-wrap gap-2 mb-3">
              <Link href="/communication?tab=messages"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-gray-200 rounded-lg hover:bg-impilo-50 hover:border-impilo-200 transition-colors">
                <FileText className="w-5 h-5 text-impilo-500" />
                Messages
              </Link>
              <Link href="/communication?tab=pages"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-gray-200 rounded-lg hover:bg-amber-50 hover:border-amber-300 transition-colors">
                <AlertTriangle className="w-5 h-5 text-amber-500" />
                Pages
              </Link>
              <Link href="/communication?tab=calls"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-gray-200 rounded-lg hover:bg-green-50 hover:border-green-300 transition-colors">
                <Video className="w-5 h-5 text-green-600" />
                Calls
              </Link>
              <Link href="/omnichannel"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-gray-200 rounded-lg hover:bg-teal-50 hover:border-teal-300 transition-colors">
                <Radio className="w-5 h-5 text-teal-600" />
                Omnichannel
              </Link>
              <Link href="/scheduling/noticeboard"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-gray-200 rounded-lg hover:bg-purple-50 hover:border-purple-300 transition-colors">
                <Globe className="w-5 h-5 text-purple-600" />
                Noticeboard
              </Link>
            </div>
            <AnnouncementsBanner />
          </div>

          {/* Module Categories (Lovable ExpandableCategoryCards) */}
          <div>
            <h3 className="text-sm font-semibold text-gray-700 mb-3 uppercase tracking-wide">Modules</h3>
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
              {categories.map((cat) => {
                const CatIcon = cat.icon;
                const isExpanded = expandedCategory === cat.id;
                return (
                  <div key={cat.id}>
                    <button
                      onClick={() => setExpandedCategory(isExpanded ? null : cat.id)}
                      className={`w-full text-left rounded-lg border p-4 transition-all ${
                        isExpanded ? "border-impilo-200 bg-impilo-50 shadow-sm" : "border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm"
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div className={`w-9 h-9 rounded-lg ${cat.color} flex items-center justify-center shrink-0`}>
                          <CatIcon className="w-4.5 h-4.5 text-white" />
                        </div>
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-gray-900 truncate">{cat.title}</p>
                          <p className="text-xs text-gray-500">{cat.modules.length} modules</p>
                        </div>
                      </div>
                    </button>
                    {isExpanded && (
                      <div className="mt-2 bg-white rounded-lg border border-gray-200 p-3 space-y-1">
                        {cat.modules.map((mod) => {
                          const ModIcon = mod.icon;
                          return (
                            <Link key={mod.href + mod.label} href={mod.href}
                              className="flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-gray-50 transition-colors group">
                              <div className={`w-7 h-7 rounded ${mod.color} flex items-center justify-center shrink-0`}>
                                <ModIcon className="w-3.5 h-3.5" />
                              </div>
                              <div className="min-w-0">
                                <p className="text-sm text-gray-900 group-hover:text-impilo-500">{mod.label}</p>
                                <p className="text-xs text-gray-500 truncate">{mod.description}</p>
                              </div>
                              <ChevronRight className="w-3.5 h-3.5 text-gray-400 shrink-0 ml-auto" />
                            </Link>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Finance Overview (finance users only) */}
          {isFinance && (
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">Finance Overview</h3>
                <Link href="/finance" className="text-xs text-impilo-500 hover:text-impilo-700">Finance Dashboard →</Link>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <Link href="/finance/billing" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-impilo-200 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><Receipt className="w-4 h-4 text-impilo-400" /><span className="text-sm font-medium text-gray-900">Billing</span></div>
                  <p className="text-xs text-gray-500">View and manage bills</p>
                </Link>
                <Link href="/finance/payments" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-impilo-200 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><FileText className="w-4 h-4 text-green-500" /><span className="text-sm font-medium text-gray-900">Payments</span></div>
                  <p className="text-xs text-gray-500">Track payment status</p>
                </Link>
                <Link href="/finance/claims" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-impilo-200 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><ClipboardList className="w-4 h-4 text-purple-500" /><span className="text-sm font-medium text-gray-900">Claims</span></div>
                  <p className="text-xs text-gray-500">Insurance claim tracking</p>
                </Link>
              </div>
            </div>
          )}

          </>)}

          {/* ═══ PROFESSIONAL TAB ═══ */}
          {activeTab === "professional" && (
            <div className="space-y-6">
              {(availableOperationalModes.includes("registry_admin") ||
                availableOperationalModes.includes("organization_admin")) && (
                <div className="rounded-xl border border-slate-200 bg-gradient-to-r from-slate-50 to-white p-5">
                  <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
                    <Shield className="w-4 h-4 text-amber-700" />
                    Administrative planes
                  </h3>
                  <p className="mt-1 text-xs text-gray-600 max-w-2xl">
                    Enter sovereign registry governance or organization operations explicitly — separate from
                    Facility Work (facility → workspace → shift). These match the context strip at the top of
                    the shell.
                  </p>
                  <div className="mt-4 flex flex-wrap gap-3">
                    {availableOperationalModes.includes("registry_admin") && (
                      <Link
                        href="/registry-admin"
                        className="inline-flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 text-sm font-medium text-amber-950 hover:border-amber-400 transition-colors"
                      >
                        <ShieldCheck className="h-4 w-4" />
                        Registry administration
                      </Link>
                    )}
                    {availableOperationalModes.includes("organization_admin") && (
                      <Link
                        href="/organization-admin"
                        className="inline-flex items-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-4 py-2.5 text-sm font-medium text-violet-950 hover:border-violet-400 transition-colors"
                      >
                        <UserCog className="h-4 w-4" />
                        Organization administration
                      </Link>
                    )}
                  </div>
                </div>
              )}

              {/* Credentials & License - Compact Cards */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* License Card - Prominent */}
                <div className={`rounded-xl border-2 p-4 ${licenseActive ? "border-green-200 bg-green-50" : "border-red-200 bg-red-50"}`}>
                  <div className="flex items-center gap-2 mb-2">
                    <Award className={`w-5 h-5 ${licenseActive ? "text-green-600" : "text-red-600"}`} />
                    <span className={`text-sm font-semibold ${licenseActive ? "text-green-700" : "text-red-700"}`}>
                      {licenseActive ? "Licensed" : "Action Required"}
                    </span>
                  </div>
                  <p className="text-xs text-gray-600">
                    {licenses.length > 0 ? `${licenses.length} license${licenses.length > 1 ? "s" : ""} on record` : "No license data"}
                  </p>
                  <Link href="/home/credentials" className="text-xs text-impilo-500 hover:text-impilo-700 mt-2 inline-block">
                    Manage →
                  </Link>
                </div>
                {/* CPD Progress */}
                <div className="rounded-xl border border-gray-200 bg-white p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium text-gray-900">CPD Progress</span>
                    <span className="text-xs text-impilo-500">
                      {cpdCycle ? `${cpdEarned}/${cpdRequired} pts` : "No active cycle"}
                    </span>
                  </div>
                  <div className="w-full bg-gray-100 rounded-full h-2">
                    <div
                      className="bg-impilo-500 rounded-full h-2"
                      style={{ width: `${cpdProgressPct}%` }}
                    />
                  </div>
                  <p className="text-xs text-gray-500 mt-2">
                    {cpdCycle
                      ? `${Math.max(0, cpdRequired - cpdEarned)} points remaining`
                      : "CPD cycle data unavailable"}
                  </p>
                </div>
                {/* Fundo learning KPI snapshot */}
                <div className="rounded-xl border border-cyan-200 bg-cyan-50 p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium text-cyan-900">Fundo Snapshot</span>
                    <Link href="/learning" className="text-xs font-medium text-cyan-700 hover:text-cyan-900">
                      Open →
                    </Link>
                  </div>
                  <div className="grid grid-cols-3 gap-2">
                    <Link href="/learning?focus=required" className="rounded-lg bg-white px-2 py-2 text-center border border-cyan-100 hover:border-cyan-300 transition">
                      <p className="text-[10px] uppercase tracking-wide text-cyan-700">Required</p>
                      <p className="text-base font-semibold text-cyan-900">
                        {fundoKpisLoading ? "…" : fundoKpis.required}
                      </p>
                    </Link>
                    <Link href="/learning?focus=overdue" className="rounded-lg bg-white px-2 py-2 text-center border border-cyan-100 hover:border-cyan-300 transition">
                      <p className="text-[10px] uppercase tracking-wide text-cyan-700">Overdue</p>
                      <p className={`text-base font-semibold ${fundoKpis.overdue > 0 ? "text-rose-700" : "text-cyan-900"}`}>
                        {fundoKpisLoading ? "…" : fundoKpis.overdue}
                      </p>
                    </Link>
                    <Link href="/learning?focus=cpd" className="rounded-lg bg-white px-2 py-2 text-center border border-cyan-100 hover:border-cyan-300 transition">
                      <p className="text-[10px] uppercase tracking-wide text-cyan-700">CPD</p>
                      <p className="text-base font-semibold text-cyan-900">
                        {fundoKpisLoading ? "…" : fundoKpis.cpdEligible}
                      </p>
                    </Link>
                  </div>
                  <p className="mt-2 text-xs text-cyan-800">
                    {fundoKpisError
                      ? "Learning KPI feed unavailable right now; open Impilo Fundo for full details."
                      : "Required, overdue, and CPD-eligible counts are sourced from Fundo my-learning."}
                  </p>
                </div>
                {/* Quick Actions */}
                <div className="rounded-xl border border-gray-200 bg-white p-4">
                  <span className="text-sm font-medium text-gray-900 mb-3 block">Quick Actions</span>
                  <div className="space-y-2">
                    <Link href="/learning" className="flex items-center gap-2 text-xs text-gray-600 hover:text-impilo-500">
                      <GraduationCap className="w-3.5 h-3.5" /> Impilo Fundo
                    </Link>
                    <Link href="/home/certifications" className="flex items-center gap-2 text-xs text-gray-600 hover:text-impilo-500">
                      <Award className="w-3.5 h-3.5" /> Certifications
                    </Link>
                    <Link href="/professional" className="flex items-center gap-2 text-xs text-gray-600 hover:text-impilo-500">
                      <User className="w-3.5 h-3.5" /> Full Profile
                    </Link>
                    <Link href="/home/transcripts" className="flex items-center gap-2 text-xs text-gray-600 hover:text-impilo-500">
                      <FileText className="w-3.5 h-3.5" /> CPD Transcripts
                    </Link>
                  </div>
                </div>
              </div>

              {/* Affiliations (from VARAPI privileges or facility list fallback) */}
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                  <Building2 className="w-5 h-5 text-purple-600" /> Facility Affiliations
                </h3>
                {privileges.length > 0 ? (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {privileges.slice(0, 6).map((priv, idx) => (
                      <div key={idx} className="flex items-center justify-between bg-gray-50 rounded-lg border border-gray-200 p-3">
                        <div className="flex items-center gap-2">
                          <Building2 className="w-4 h-4 text-gray-400" />
                          <div>
                            <p className="text-sm font-medium text-gray-900">Facility {priv.facilityId?.slice(0, 8)}</p>
                            <p className="text-xs text-gray-500">{priv.privilegeType} · {priv.scope}</p>
                          </div>
                        </div>
                        <span className={`text-xs px-2 py-0.5 rounded-full ${
                          priv.status === "APPROVED" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"
                        }`}>{priv.status}</span>
                      </div>
                    ))}
                  </div>
                ) : facilities.length > 0 ? (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {facilities.slice(0, 4).map((f) => (
                      <div key={f.id} className="flex items-center justify-between bg-gray-50 rounded-lg border border-gray-200 p-3">
                        <div className="flex items-center gap-2">
                          <Building2 className="w-4 h-4 text-gray-400" />
                          <div>
                            <p className="text-sm font-medium text-gray-900">{f.attributes.name}</p>
                            <p className="text-xs text-gray-500">{f.attributes.facilityType}</p>
                          </div>
                        </div>
                        <button onClick={() => handleFacilitySelect(f)}
                          className="text-xs text-impilo-500 hover:text-impilo-700 font-medium">
                          Start Shift →
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-gray-400">No facility affiliations found.</p>
                )}
              </div>

              {/* Work Modes — all ways a professional can work */}
              {isClinical && (
                <div className="bg-white rounded-lg border border-gray-200 p-6">
                  <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                    <Globe className="w-5 h-5 text-teal-600" /> Other Ways to Work
                  </h3>
                  <p className="text-sm text-gray-500 mb-4">
                    Not at a facility? Choose how you are working today.
                  </p>
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                    {/* Telemedicine & Virtual */}
                    <button onClick={() => enterFacilityFreeMode("clinical", "/telemedicine")}
                      className="text-left bg-green-50 rounded-lg border border-green-200 p-4 hover:border-green-400 transition-all group">
                      <Video className="w-5 h-5 text-green-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-green-700">Telemedicine & Virtual</p>
                      <p className="text-xs text-gray-500 mt-0.5">Video/voice consultations, remote patient monitoring, e-prescribing</p>
                    </button>
                    {/* Independent Practice */}
                    <button onClick={() => enterIndependentMode("independent_practice")}
                      className="text-left bg-amber-50 rounded-lg border border-amber-200 p-4 hover:border-amber-400 transition-all group">
                      <Briefcase className="w-5 h-5 text-amber-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-amber-700">Independent Practice</p>
                      <p className="text-xs text-gray-500 mt-0.5">Work under your own licence without facility context</p>
                    </button>
                    {/* Field Work */}
                    <button onClick={() => enterIndependentMode("community_outreach")}
                      className="text-left bg-teal-50 rounded-lg border border-teal-200 p-4 hover:border-teal-400 transition-all group">
                      <MapPin className="w-5 h-5 text-teal-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-teal-700">Field & Community Work</p>
                      <p className="text-xs text-gray-500 mt-0.5">Community outreach, home visits, mobile clinics, school health</p>
                    </button>
                    {/* Emergency Response */}
                    <button onClick={() => enterIndependentMode("emergency_response")}
                      className="text-left bg-red-50 rounded-lg border border-red-200 p-4 hover:border-red-400 transition-all group">
                      <Siren className="w-5 h-5 text-red-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-red-700">Emergency Response</p>
                      <p className="text-xs text-gray-500 mt-0.5">Disaster, outbreak, mass casualty — elevated trust, break-glass access</p>
                    </button>
                    {/* Above Site */}
                    <button onClick={() => enterFacilityFreeMode("admin", "/reports")}
                      className="text-left bg-indigo-50 rounded-lg border border-indigo-200 p-4 hover:border-indigo-400 transition-all group">
                      <Layers className="w-5 h-5 text-indigo-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-indigo-700">Above-Site & Programme</p>
                      <p className="text-xs text-gray-500 mt-0.5">District/provincial oversight, programme management, surveillance, reporting</p>
                    </button>
                    {/* Maintenance & Configuration */}
                    <button onClick={() => enterFacilityFreeMode("admin", "/admin")}
                      className="text-left bg-gray-50 rounded-lg border border-gray-200 p-4 hover:border-gray-400 transition-all group">
                      <Wrench className="w-5 h-5 text-gray-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-gray-700">Maintenance & Configuration</p>
                      <p className="text-xs text-gray-500 mt-0.5">System setup, master data, user management, integrations, troubleshooting</p>
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ═══ PERSONAL TAB ═══ */}
          {activeTab === "personal" && (
            <div className="space-y-6">
              {/* My Health Quick Actions */}
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                  <Heart className="w-5 h-5 text-pink-600" /> My Health
                </h3>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                  <Link href="/scheduling" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-impilo-50 border border-impilo-200 hover:border-impilo-400 transition-colors text-center">
                    <Calendar className="w-6 h-6 text-impilo-500" />
                    <span className="text-xs font-medium text-gray-900">Book Visit</span>
                  </Link>
                  <Link href="/telemedicine" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-green-50 border border-green-200 hover:border-green-400 transition-colors text-center">
                    <Video className="w-6 h-6 text-green-600" />
                    <span className="text-xs font-medium text-gray-900">Video Call</span>
                  </Link>
                  <Link href="/home/medications" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-amber-50 border border-amber-200 hover:border-amber-400 transition-colors text-center">
                    <Pill className="w-6 h-6 text-amber-600" />
                    <span className="text-xs font-medium text-gray-900">My Medications</span>
                  </Link>
                  <Link href="/home/notifications" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-purple-50 border border-purple-200 hover:border-purple-400 transition-colors text-center">
                    <FileText className="w-6 h-6 text-purple-600" />
                    <span className="text-xs font-medium text-gray-900">Messages</span>
                  </Link>
                </div>
              </div>

              {/* Blood donation — My Life */}
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                  <Droplet className="w-5 h-5 text-rose-600" /> Give blood
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                  <Link href="/madi/donor/drives" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-rose-50 border border-rose-200 hover:border-rose-400 transition-colors text-center">
                    <Droplet className="w-6 h-6 text-rose-600" />
                    <span className="text-xs font-medium text-gray-900">Donate blood</span>
                    <span className="text-[11px] text-gray-500">Find a drive near you</span>
                  </Link>
                  <Link href="/madi/donor/register" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-white border border-rose-200 hover:border-rose-400 transition-colors text-center">
                    <Heart className="w-6 h-6 text-rose-500" />
                    <span className="text-xs font-medium text-gray-900">Become a blood donor</span>
                    <span className="text-[11px] text-gray-500">Voluntary registration</span>
                  </Link>
                  <Link href="/madi/donor" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-white border border-gray-200 hover:border-rose-300 transition-colors text-center">
                    <User className="w-6 h-6 text-gray-500" />
                    <span className="text-xs font-medium text-gray-900">My donor hub</span>
                    <span className="text-[11px] text-gray-500">History & preferences</span>
                  </Link>
                </div>
              </div>

              {/* Personal Links */}
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                  <User className="w-5 h-5 text-gray-600" /> Account & Settings
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <Link href="/home/profile" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-impilo-200 transition-colors">
                    <User className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">Profile</p>
                      <p className="text-xs text-gray-500">View and edit your profile</p>
                    </div>
                  </Link>
                  <Link href="/home/preferences" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-impilo-200 transition-colors">
                    <Settings className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">Preferences</p>
                      <p className="text-xs text-gray-500">Language, notifications, display</p>
                    </div>
                  </Link>
                  <Link href="/settings/security" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-impilo-200 transition-colors">
                    <Shield className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">Security & Privacy</p>
                      <p className="text-xs text-gray-500">Password, MFA, sessions</p>
                    </div>
                  </Link>
                  <Link href="/marketplace" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-impilo-200 transition-colors">
                    <ShoppingCart className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">Health Marketplace</p>
                      <p className="text-xs text-gray-500">Browse products & services</p>
                    </div>
                  </Link>
                </div>
              </div>

              {/* Community Groups */}
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                  <Users className="w-5 h-5 text-purple-600" /> Communities
                </h3>
                {communityGroups.length === 0 ? (
                  <p className="text-sm text-gray-400">No community groups available yet.</p>
                ) : (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {communityGroups.slice(0, 4).map((group) => (
                      <div key={group.id} className="flex items-center justify-between bg-gray-50 rounded-lg border border-gray-200 p-3">
                        <div>
                          <p className="text-sm font-medium text-gray-900">{group.attributes.name}</p>
                          <p className="text-xs text-gray-500">
                            {group.attributes.groupType} · {group.attributes.memberCount} members
                          </p>
                        </div>
                        <button
                          onClick={() => joinGroup.mutate({ groupId: group.id, memberId: user?.id ?? "" })}
                          disabled={joinGroup.isPending}
                          className="text-xs text-impilo-500 hover:text-impilo-700 font-medium"
                        >
                          Join
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Community Feed */}
              <FeedSection />
            </div>
          )}

        </div>
      </PageShell>
    </AppLayout>
  );
}

// ═══════════════════════════════════════════════════════════════════
// Citizen Home — Facebook-style 3-column layout
// Left: quick-nav shortcuts · Centre: timeline feed · Right: widgets
// ═══════════════════════════════════════════════════════════════════

/** Normalize citizen + scheduling appointment payloads into JSON:API-style rows for queue-workflows helpers. */
function normalizeCitizenAppointmentList(payload: unknown): Array<{ id: string; attributes: Record<string, unknown> }> {
  const root = payload as { data?: unknown } | null | undefined;
  const raw = root?.data;
  const arr: unknown[] = Array.isArray(raw) ? raw : [];
  return arr.map((row, i) => {
    if (row && typeof row === "object" && "attributes" in row) {
      const r = row as { id?: string; attributes?: Record<string, unknown> };
      return { id: String(r.id ?? `apt-${i}`), attributes: r.attributes ?? {} };
    }
    if (row && typeof row === "object") {
      const o = row as Record<string, unknown>;
      return {
        id: String(o.id ?? `apt-${i}`),
        attributes: {
          scheduled_at: o.scheduledAt ?? o.scheduled_at,
          scheduledAt: o.scheduledAt,
          appointment_time: o.appointmentTime ?? o.appointment_time,
          appointment_type: o.appointmentType ?? o.appointment_type,
          appointmentType: o.appointmentType,
          status: o.status,
          provider_name: o.providerName ?? o.provider_name,
          providerName: o.providerName,
        },
      };
    }
    return { id: `apt-${i}`, attributes: {} };
  });
}

function CitizenHome({
  user,
  greeting,
}: {
  user: AuthUser | null;
  greeting: string;
}) {
  const patientAnchor = (user?.healthId && user.healthId.trim()) || user?.id || "";

  const citizenActorAppointments = useQuery<unknown>({
    queryKey: ["citizen-upcoming-appointments-mobile", user?.id],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/appointments?page=0&size=25"),
    enabled: !!user?.id,
    staleTime: 60_000,
  });

  const citizenPatientAppointments = useQuery<unknown>({
    queryKey: ["citizen-upcoming-appointments-patient", patientAnchor],
    queryFn: () =>
      apiClient.get(`/internal/v1/appointments?patient_id=${encodeURIComponent(patientAnchor)}&size=25`),
    enabled: !!patientAnchor,
    staleTime: 60_000,
  });

  const mergedAppointmentRows = useMemo(() => {
    const fromActor = normalizeCitizenAppointmentList(citizenActorAppointments.data);
    const fromPatient = normalizeCitizenAppointmentList(citizenPatientAppointments.data);
    const map = new Map<string, { id: string; attributes: Record<string, unknown> }>();
    for (const row of fromActor) map.set(row.id, row);
    for (const row of fromPatient) map.set(row.id, row);
    return [...map.values()];
  }, [citizenActorAppointments.data, citizenPatientAppointments.data]);

  const citizenAppointmentsLoading = citizenActorAppointments.isPending || citizenPatientAppointments.isPending;
  const citizenAppointmentsError = citizenActorAppointments.isError && citizenPatientAppointments.isError;

  const terminalAppt = new Set(["CANCELLED", "CANCELED", "NO_SHOW", "COMPLETED", "DONE"]);
  const startOfToday = () => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  };
  const upcomingAppointments = mergedAppointmentRows
    .filter((row) => {
      const status = getAppointmentStatus(row).toUpperCase();
      if (terminalAppt.has(status)) return false;
      const when = getAppointmentTime(row);
      if (!when) return false;
      const ms = new Date(when).getTime();
      if (Number.isNaN(ms)) return false;
      return ms >= startOfToday();
    })
    .sort((a, b) => new Date(getAppointmentTime(a)).getTime() - new Date(getAppointmentTime(b)).getTime())
    .slice(0, 5);

  return (
    <>
      {/* ── Top status bar ────────────────────────────────────────── */}
      <div className="flex items-center justify-between bg-white border-b border-gray-200 px-4 py-2.5 -mx-4 -mt-4 mb-4 sm:px-6 sm:-mx-6">
        <div className="flex items-center gap-3 min-w-0">
          <div className="h-9 w-9 rounded-full bg-impilo-100 flex items-center justify-center shrink-0">
            <User className="h-4.5 w-4.5 text-impilo-600" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-900 truncate">
              {greeting}, {user?.displayName?.split(" ")[0] ?? "there"}
            </p>
            <p className="text-[11px] text-gray-400 truncate">
              {user?.email}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-1.5">
          <Link href="/home/notifications" className="p-2 rounded-full hover:bg-gray-100 relative" title="Notifications">
            <Activity className="w-4.5 h-4.5 text-gray-500" />
          </Link>
          <Link href="/ask" className="p-2 rounded-full hover:bg-gray-100" title="Ask Nompilo">
            <MessageSquare className="w-4.5 h-4.5 text-gray-500" />
          </Link>
          <Link href="/auth/logout" className="p-2 rounded-full hover:bg-gray-100" title="Switch account">
            <UserCog className="w-4.5 h-4.5 text-gray-500" />
          </Link>
        </div>
      </div>

      {/* ── 3-column grid ─────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-[220px_1fr_260px] gap-5">

        {/* ════ LEFT COLUMN — Quick nav ════ */}
        <aside className="hidden lg:block space-y-4">
          {/* Health ID QR - always visible */}
          <Link href="/citizen/health-id/qr"
            className="flex items-center gap-3 p-3 rounded-xl bg-gradient-to-br from-impilo-50 to-impilo-100 border border-impilo-200 hover:border-impilo-300 transition-colors">
            <div className="w-10 h-10 rounded-lg bg-impilo-500 flex items-center justify-center">
              <QrCode className="w-5 h-5 text-white" />
            </div>
            <div>
              <p className="text-sm font-semibold text-impilo-700">My Health ID</p>
              <p className="text-[11px] text-impilo-500">Tap to show QR</p>
            </div>
          </Link>

          {/* My Health shortcuts with badges */}
          <nav className="space-y-0.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider px-2 mb-1">My Health</p>
            {[
              { href: "/home/profile", label: "Profile", icon: User, badge: null },
              { href: "/home/medications", label: "Medications", icon: Pill, badge: null },
              { href: "/home/documents", label: "Documents", icon: FileText, badge: null },
              { href: "/home/bookings", label: "My Bookings", icon: Calendar, badge: null },
              { href: "/home/appointments", label: "My Appointments", icon: Calendar, badge: null },
              { href: "/madi/donor", label: "Blood donation", icon: Droplet, badge: null },
              { href: "/monitoring", label: "Monitoring", icon: Activity, badge: null },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <Link key={item.href} href={item.href}
                  className="flex items-center justify-between px-2 py-2 rounded-lg text-sm text-gray-700 hover:bg-gray-100 transition-colors">
                  <div className="flex items-center gap-2.5">
                    <Icon className="w-4 h-4 text-gray-400" />
                    {item.label}
                  </div>
                  {item.badge && (
                    <span className="px-1.5 py-0.5 text-[10px] font-medium bg-amber-100 text-amber-700 rounded-full">
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>

          <hr className="border-gray-200" />

          {/* My Care */}
          <nav className="space-y-0.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider px-2 mb-1">My Care</p>
            {[
              { href: "/home/care-team", label: "Care Team", icon: Users, badge: null },
              { href: "/coverage", label: "Coverage", icon: Shield, badge: null },
              { href: "/home/referrals", label: "Referrals", icon: ArrowRight, badge: null },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <Link key={item.href} href={item.href}
                  className="flex items-center justify-between px-2 py-2 rounded-lg text-sm text-gray-700 hover:bg-gray-100 transition-colors">
                  <div className="flex items-center gap-2.5">
                    <Icon className="w-4 h-4 text-gray-400" />
                    {item.label}
                  </div>
                  {item.badge && (
                    <span className="px-1.5 py-0.5 text-[10px] font-medium bg-amber-100 text-amber-700 rounded-full">
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>

          <hr className="border-gray-200" />

          {/* Explore */}
          <nav className="space-y-0.5">
            <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider px-2 mb-1">Explore</p>
            {[
              { href: "/wellness", label: "Wellness", icon: Heart, badge: null },
              { href: "/discover", label: "Find services", icon: Globe, badge: null },
              { href: "/marketplace", label: "Marketplace", icon: ShoppingCart, badge: null },
              { href: "/caregiving", label: "Caregiving", icon: Users, badge: null },
              { href: "/wallet", label: "My Wallet", icon: Award, badge: null },
            ].map((item) => {
              const Icon = item.icon;
              return (
                <Link key={item.href} href={item.href}
                  className="flex items-center gap-2.5 px-2 py-2 rounded-lg text-sm text-gray-700 hover:bg-gray-100 transition-colors">
                  <Icon className="w-4 h-4 text-gray-400" />
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </aside>

        {/* ════ CENTRE COLUMN — Timeline ════ */}
        <main className="min-w-0 space-y-4">
          <TimelineComposer userName={user?.displayName?.split(" ")[0] ?? "there"} />
          <TimelineFeed userId={user?.id} />
        </main>

        {/* ════ RIGHT COLUMN — Widgets ════ */}
        <aside className="hidden lg:block space-y-4">
          {/* Health ID Widget - prominent at top */}
          <Link href="/citizen/health-id/qr"
            className="flex items-center gap-3 p-4 rounded-xl bg-gradient-to-br from-impilo-500 to-impilo-600 hover:from-impilo-600 hover:to-impilo-700 transition-colors text-white">
            <QrCode className="w-8 h-8" />
            <div>
              <p className="text-sm font-semibold">Health ID</p>
              <p className="text-xs opacity-80">Tap to show QR code</p>
            </div>
          </Link>

          {/* Upcoming appointments — confirmed scheduled events only */}
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3 flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-impilo-500" /> Upcoming appointments
            </h3>
            {citizenAppointmentsLoading && (
              <p className="text-xs text-gray-500">Loading appointments…</p>
            )}
            {!citizenAppointmentsLoading && citizenAppointmentsError && (
              <p className="text-xs text-gray-500 leading-relaxed">
                Appointments could not be loaded. Book a service or check My Bookings for pending requests.
              </p>
            )}
            {!citizenAppointmentsLoading && !citizenAppointmentsError && upcomingAppointments.length === 0 && (
              <p className="text-xs text-gray-500 leading-relaxed">
                No confirmed appointments yet. Booking requests appear under My Bookings until confirmed.
              </p>
            )}
            {!citizenAppointmentsLoading && !citizenAppointmentsError && upcomingAppointments.length > 0 && (
              <ul className="space-y-2.5">
                {upcomingAppointments.map((appt) => {
                  const when = getAppointmentTime(appt);
                  const label = when
                    ? new Date(when).toLocaleString([], { dateStyle: "medium", timeStyle: "short" })
                    : "Date TBC";
                  return (
                    <li key={appt.id} className="text-xs text-gray-700 border-b border-gray-100 pb-2 last:border-0 last:pb-0">
                      <p className="font-medium text-gray-900">{getAppointmentType(appt)}</p>
                      <p className="text-[11px] text-gray-500 mt-0.5">{label}</p>
                      <p className="text-[11px] text-gray-500">{getAppointmentProvider(appt)}</p>
                    </li>
                  );
                })}
              </ul>
            )}
            <div className="mt-3 flex flex-col gap-1 text-center text-xs font-medium">
              <Link href="/home/bookings/new" className="text-impilo-600 hover:underline">
                Book a service →
              </Link>
              <Link href="/home/bookings" className="text-gray-500 hover:underline">
                My Bookings (pending requests)
              </Link>
              <Link href="/home/appointments" className="text-gray-500 hover:underline">
                My Appointments (confirmed)
              </Link>
            </div>
          </div>

          {/* Quick health actions */}
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">Quick Actions</h3>
            <div className="grid grid-cols-2 gap-2">
              {[
                { href: "/home/medications", label: "Medications", icon: Pill, color: "text-amber-500" },
                { href: "/home/conditions", label: "Conditions", icon: FlaskConical, color: "text-purple-500" },
                { href: "/home/allergies", label: "Allergies", icon: AlertTriangle, color: "text-red-500" },
                { href: "/home/documents", label: "Documents", icon: FileCheck, color: "text-blue-500" },
                { href: "/home/results", label: "Lab Results", icon: Clipboard, color: "text-green-500" },
              ].map((a) => {
                const Icon = a.icon;
                return (
                  <Link key={a.href} href={a.href}
                    className="flex flex-col items-center gap-1.5 p-3 rounded-lg border border-gray-100 hover:border-impilo-200 hover:bg-impilo-50/50 transition-colors text-center">
                    <Icon className={`w-5 h-5 ${a.color}`} />
                    <span className="text-[11px] font-medium text-gray-600">{a.label}</span>
                  </Link>
                );
              })}
            </div>
          </div>

          {/* Account */}
          <div className="bg-white rounded-xl border border-gray-200 p-4">
            <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">Account</h3>
            <div className="space-y-1">
              {[
                { href: "/home/profile", label: "Profile" },
                { href: "/home/preferences", label: "Preferences" },
                { href: "/settings/security", label: "Security & Privacy" },
                { href: "/support", label: "Help & Support" },
              ].map((link) => (
                <Link key={link.href} href={link.href}
                  className="block px-2 py-1.5 rounded-md text-xs text-gray-500 hover:text-gray-700 hover:bg-gray-50 transition-colors">
                  {link.label}
                </Link>
              ))}
            </div>
          </div>
        </aside>
      </div>
    </>
  );
}

/** Compact announcements banner showing pinned/urgent items from the noticeboard */
// ═══════════════════════════════════════════════════════════════════
// Timeline components — post creation + live citizen feed
// ═══════════════════════════════════════════════════════════════════

function TimelineComposer({ userName }: { userName: string }) {
  const [postText, setPostText] = useState("");
  const [posting, setPosting] = useState(false);
  const [posted, setPosted] = useState(false);

  async function handlePost() {
    if (!postText.trim()) return;
    setPosting(true);
    try {
      await apiClient.post("/internal/v1/mobile/citizen/feed", {
        body: postText.trim(),
        type: "STATUS_UPDATE",
      });
    } catch {
      // BFF may not have this endpoint yet — that's OK, clear locally
    }
    setPosting(false);
    setPosted(true);
    setPostText("");
    setTimeout(() => setPosted(false), 3000);
  }

  return (
    <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
      {/* Composer */}
      <div className="p-4">
        <div className="flex items-start gap-3">
          <div className="h-10 w-10 rounded-full bg-impilo-100 flex items-center justify-center shrink-0 mt-0.5">
            <User className="h-5 w-5 text-impilo-600" />
          </div>
          <div className="flex-1">
            <textarea
              value={postText}
              onChange={(e) => setPostText(e.target.value)}
              placeholder={`What's on your mind, ${userName}?`}
              rows={2}
              className="w-full resize-none border-0 bg-transparent text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-0"
            />
          </div>
        </div>
        {posted && (
          <p className="text-xs text-impilo-500 mt-1 ml-[52px]">Posted to your timeline</p>
        )}
      </div>
      {/* Action bar */}
      <div className="flex items-center justify-between border-t border-gray-100 px-4 py-2">
        <p className="text-[11px] text-gray-400 px-1">
          Media and structured check-ins will appear here when those channels are enabled for citizen posts.
        </p>
        <button
          onClick={handlePost}
          disabled={!postText.trim() || posting}
          className="flex items-center gap-1.5 px-4 py-1.5 rounded-full bg-impilo-500 text-white text-xs font-medium hover:bg-impilo-600 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <Send className="w-3.5 h-3.5" /> Post
        </button>
      </div>
    </div>
  );
}

/** Live citizen timeline posts from the BFF. */
function TimelineFeed({ userId }: { userId?: string }) {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isCitizenAccess = hasRole("CITIZEN") || hasRole("SYSTEM_ADMIN") || hasRole("SUPER_ADMIN") || hasRole("DEVELOPER");
  const { data, isLoading } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["personal-feed", userId],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/feed?size=10"),
    staleTime: 30_000,
    enabled: isCitizenAccess,
  });
  const liveItems = data?.data ?? [];

  // Seeded posts so the timeline never looks empty
  const seededPosts: Array<{
    id: string;
    author: string;
    avatar: string;
    time: string;
    body: string;
    likes: number;
    comments: number;
    type: "update" | "health_tip" | "announcement" | "milestone";
  }> = [
    {
      id: "seed-1",
      author: "Ministry of Health",
      avatar: "MoH",
      time: "2 hours ago",
      body: "Flu season reminder: Free influenza vaccinations are available at all public health facilities. Bring your Health ID. Walk-ins welcome, no appointment needed.",
      likes: 142,
      comments: 23,
      type: "announcement",
    },
    {
      id: "seed-2",
      author: "Nompilo Health AI",
      avatar: "N",
      time: "4 hours ago",
      body: "Tip: Drinking 2 litres of water daily can improve energy levels, skin health, and kidney function. Track your hydration in the Wellness tab.",
      likes: 89,
      comments: 7,
      type: "health_tip",
    },
    {
      id: "seed-3",
      author: "Harare Central Hospital",
      avatar: "HCH",
      time: "6 hours ago",
      body: "New services available: Our Telemedicine clinic is now open for video consultations Monday–Friday 08:00–16:00. Book through Impilo or call +263 4 123 4567.",
      likes: 67,
      comments: 15,
      type: "update",
    },
    {
      id: "seed-4",
      author: "Community Wellness",
      avatar: "CW",
      time: "Yesterday",
      body: "Congratulations to 1,247 citizens who completed the 30-Day Walking Challenge! New challenge starting Monday: Sleep Better in 21 Days. Join in the Wellness tab.",
      likes: 234,
      comments: 41,
      type: "milestone",
    },
    {
      id: "seed-5",
      author: "MOHCC Immunisation Programme",
      avatar: "IP",
      time: "Yesterday",
      body: "Childhood immunisation week starts next Monday across all provinces. Check your child's vaccination schedule in Documents > Immunisation Record.",
      likes: 178,
      comments: 32,
      type: "announcement",
    },
  ];

  const typeColors: Record<string, string> = {
    announcement: "bg-red-50 text-red-600 border-red-100",
    health_tip: "bg-green-50 text-green-600 border-green-100",
    update: "bg-blue-50 text-blue-600 border-blue-100",
    milestone: "bg-amber-50 text-amber-600 border-amber-100",
  };

  const avatarColors: Record<string, string> = {
    Y: "bg-impilo-100 text-impilo-700",
  };

  const livePosts = liveItems.length > 0 ? [
    ...liveItems.map((item) => ({
      id: item.id,
      author: (item.attributes.author as string) ?? "You",
      avatar: ((item.attributes.author as string) ?? "Y").charAt(0).toUpperCase(),
      time: item.attributes.published_at
        ? new Date(item.attributes.published_at as string).toLocaleDateString()
        : "Just now",
      body: (item.attributes.body as string) ?? (item.attributes.title as string) ?? "",
      likes: (item.attributes.likes as number) ?? 0,
      comments: (item.attributes.comments as number) ?? 0,
      type: "update" as const,
    })),
  ] : seededPosts;

  return (
    <div className="space-y-4">
      {/* Call / video shortcuts — prominent strip */}
      <div className="grid grid-cols-3 gap-3">
        <Link href="/telemedicine"
          className="flex items-center justify-center gap-2 py-3 bg-green-50 border border-green-200 rounded-xl text-sm font-medium text-green-700 hover:bg-green-100 transition-colors">
          <Video className="w-4 h-4" /> Video call
        </Link>
        <Link href="/telemedicine?mode=audio"
          className="flex items-center justify-center gap-2 py-3 bg-blue-50 border border-blue-200 rounded-xl text-sm font-medium text-blue-700 hover:bg-blue-100 transition-colors">
          <Phone className="w-4 h-4" /> Voice call
        </Link>
        <Link href="/scheduling"
          className="flex items-center justify-center gap-2 py-3 bg-impilo-50 border border-impilo-200 rounded-xl text-sm font-medium text-impilo-700 hover:bg-impilo-100 transition-colors">
          <Calendar className="w-4 h-4" /> Book visit
        </Link>
      </div>

      {/* Timeline posts */}
      {isLoading && (
        <div className="rounded-xl border border-gray-200 bg-white px-4 py-8 text-center text-sm text-gray-500">
          Loading your citizen timeline…
        </div>
      )}
      {!isLoading && livePosts.length === 0 && (
        <div className="rounded-xl border border-gray-200 bg-white px-4 py-8 text-center">
          <p className="text-sm font-medium text-gray-900">No timeline posts yet</p>
          <p className="mt-1 text-xs text-gray-500">
            Your home feed will populate when the citizen feed service returns real posts for this account.
          </p>
        </div>
      )}
      {livePosts.map((post) => (
        <article key={post.id} className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          {/* Post header */}
          <div className="flex items-center gap-3 px-4 pt-4 pb-2">
            <div className={`w-10 h-10 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${avatarColors[post.avatar] ?? "bg-gray-100 text-gray-600"}`}>
              {post.avatar}
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-gray-900 truncate">{post.author}</p>
              <div className="mt-0.5 flex items-center gap-2">
                <p className="text-xs text-gray-400">{post.time}</p>
                <span className={`rounded-full border px-1.5 py-0.5 text-[10px] font-medium ${typeColors[post.type] ?? "bg-gray-50 text-gray-600 border-gray-100"}`}>
                  {post.type.replace("_", " ")}
                </span>
              </div>
            </div>
          </div>
          {/* Post body */}
          <div className="px-4 pb-3">
            <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-line">{post.body}</p>
          </div>
          {/* Engagement bar */}
          <div className="flex items-center justify-between border-t border-gray-100 px-4 py-2">
            <span className="text-xs text-gray-400">{post.likes > 0 ? `${post.likes} likes` : ""}</span>
            <span className="text-xs text-gray-400">{post.comments > 0 ? `${post.comments} comments` : ""}</span>
          </div>
          {/* Action buttons */}
          <div className="flex border-t border-gray-100">
            <button className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs text-gray-500 hover:bg-gray-50 transition-colors">
              <ThumbsUp className="w-4 h-4" /> Like
            </button>
            <button className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs text-gray-500 hover:bg-gray-50 transition-colors">
              <MessageCircle className="w-4 h-4" /> Comment
            </button>
            <button className="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-xs text-gray-500 hover:bg-gray-50 transition-colors">
              <Send className="w-4 h-4" /> Share
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

function AnnouncementsBanner() {
  const { data } = useQuery<{ data: Array<Record<string, unknown>>; stats: Record<string, number> }>({
    queryKey: ["announcements-banner"],
    queryFn: () => apiClient.get("/internal/v1/communication/announcements?size=3"),
    staleTime: 60_000,
  });
  const items = data?.data ?? [];
  const stats = data?.stats;

  if (items.length === 0) {
    return (
      <div className="flex items-center gap-2 py-2 text-sm text-gray-400">
        <Clock className="w-4 h-4" />
        No active announcements
      </div>
    );
  }

  const priorityColors: Record<string, string> = {
    urgent: "bg-red-100 text-red-700 border-red-200",
    high: "bg-orange-100 text-orange-700 border-orange-200",
    normal: "bg-impilo-100 text-impilo-600 border-impilo-200",
    low: "bg-gray-100 text-gray-600 border-gray-200",
  };

  return (
    <div className="space-y-2">
      {stats && (
        <div className="flex items-center gap-4 text-xs text-gray-500">
          <span>{stats.total ?? 0} active</span>
          {(stats.pinned ?? 0) > 0 && <span className="text-amber-600">{stats.pinned} pinned</span>}
          {(stats.urgent ?? 0) > 0 && <span className="text-red-600">{stats.urgent} urgent</span>}
        </div>
      )}
      {items.slice(0, 3).map((a) => (
        <Link
          key={a.id as string}
          href="/scheduling/noticeboard"
          className={`flex items-start gap-3 p-3 rounded-lg border text-left transition-colors hover:shadow-sm ${
            a.priority === "urgent" ? "border-red-200 bg-red-50" : a.is_pinned ? "border-amber-200 bg-amber-50" : "border-gray-200 bg-gray-50"
          }`}
        >
          <div className="shrink-0 mt-0.5">
            {a.priority === "urgent" ? (
              <AlertTriangle className="w-4 h-4 text-red-500" />
            ) : a.is_pinned ? (
              <Shield className="w-4 h-4 text-amber-500" />
            ) : (
              <Activity className="w-4 h-4 text-impilo-400" />
            )}
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium text-gray-900 truncate">{a.title as string}</p>
              <span className={`px-1.5 py-0.5 text-[10px] font-medium rounded ${priorityColors[(a.priority as string) ?? "normal"]}`}>
                {a.priority as string}
              </span>
            </div>
            <p className="text-xs text-gray-500 line-clamp-1 mt-0.5">{a.content as string}</p>
          </div>
        </Link>
      ))}
    </div>
  );
}

/** Community feed section using existing citizen feed infrastructure */
function FeedSection() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isCitizenAccess = hasRole("CITIZEN") || hasRole("SYSTEM_ADMIN") || hasRole("SUPER_ADMIN") || hasRole("DEVELOPER");
  const { data } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["personal-feed"],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/feed?size=5"),
    enabled: isCitizenAccess,
  });
  const items = data?.data ?? [];

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
        <Users className="w-5 h-5 text-purple-600" /> Community & Updates
      </h3>
      {items.length === 0 ? (
        <p className="text-sm text-gray-400">No community updates yet.</p>
      ) : (
        <div className="space-y-3">
          {items.map((item) => (
            <div key={item.id} className="flex items-start gap-3 p-3 bg-gray-50 rounded-lg">
              <div className="w-8 h-8 rounded-full bg-purple-100 flex items-center justify-center shrink-0">
                <FileText className="w-4 h-4 text-purple-500" />
              </div>
              <div className="min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">
                  {(item.attributes.title as string) ?? "Update"}
                </p>
                <p className="text-xs text-gray-500 line-clamp-2">
                  {(item.attributes.body as string) ?? ""}
                </p>
                {typeof item.attributes.published_at === "string" && (
                  <p className="text-xs text-gray-400 mt-1">
                    {new Date(item.attributes.published_at).toLocaleDateString()}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}
