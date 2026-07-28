import type { ComponentType } from "react";
import {
  Users,
  BookOpen,
  BarChart3,
  Clock,
  Building2,
  Receipt,
  Pill,
  Calendar,
  Shield,
  Stethoscope,
  ClipboardList,
  Package,
  Settings,
  FileText,
  Video,
  ShoppingCart,
  Database,
  TestTube2,
  Scan,
  MessageSquare,
  Radio,
  LayoutGrid,
  BedDouble,
  Layers,
} from "lucide-react";
import { WORKER_ACCESS_LABELS } from "@/lib/identity-context";

/**
 * Navigation-affordance data for /home's module grid — label/icon/href PRESENTATION, gated by
 * role flags (roles.isClinical etc.) that come from useRoleGroup(), itself derived from the
 * authenticated JWT's roles, not anything editable from the browser. Editing this file cannot
 * create access: every href here is independently re-guarded downstream by routes.ts
 * (guard !== "none") and the destination's own backend authz — see
 * workSurfaceModules.test.ts, which proves both of those claims directly rather than assuming
 * them. A future TSHEPO-issued capability-ID list (the same presentation-only pattern
 * lib/work-home/section-registry.ts already uses for Work Home) would be the more architecturally
 * clean version of this file, but is a separate, larger change — not required for the safety
 * property above to hold today.
 */

export interface WorkSurfaceModuleItem {
  label: string;
  description: string;
  href: string;
  icon: ComponentType<{ className?: string }>;
  color: string;
  serviceSlug?: string;
  requiresClinical?: boolean;
  requiresAdmin?: boolean;
  requiresFinance?: boolean;
  requiresDispenser?: boolean;
}

export interface WorkSurfaceCategory {
  id: string;
  title: string;
  icon: ComponentType<{ className?: string }>;
  color: string;
  modules: WorkSurfaceModuleItem[];
}

export type WorkSurfaceRoleFlags = {
  isClinical: boolean;
  isAdmin: boolean;
  isFinance: boolean;
  isDispenser: boolean;
};

export function getModuleCategories(roles: {
  isClinical: boolean; isAdmin: boolean; isFinance: boolean; isDispenser: boolean;
}): WorkSurfaceCategory[] {
  const cats: WorkSurfaceCategory[] = [];

  if (roles.isClinical || roles.isDispenser) {
    cats.push({
      id: "clinical",
      title: "Clinical Care & Orders",
      icon: Stethoscope,
      color: "bg-primary",
      modules: [
        ...(roles.isClinical ? [
          { label: "Clinical Hub", description: "All 10 clinical modules", href: "/clinical", icon: Stethoscope, color: "bg-primary-soft text-primary" },
          { label: "Queues & Wards", description: "Intake, triage, waiting, and ward status", href: "/queue", icon: Users, color: "bg-orange-100 text-orange-600" },
          { label: "Walk-in Registration", description: "Register a patient directly into queue flow", href: "/queue/walk-in", icon: Users, color: "bg-amber-100 text-amber-600" },
          { label: "Bookings & Appointments", description: "Scheduling, waitlist, and planned arrivals", href: "/scheduling", icon: Calendar, color: "bg-cyan-100 text-cyan-600" },
          { label: "Referrals", description: "Telemedicine and referral coordination", href: "/telemedicine", icon: Video, color: "bg-teal-100 text-teal-600" },
          { label: "Laboratory (LIMS)", description: "Select a chart, then continue into orders and results", href: "/queue/search?workflow=lims", icon: TestTube2, color: "bg-violet-100 text-violet-700" },
          { label: "Imaging (PACS)", description: "Select a chart, then continue into imaging review", href: "/queue/search?workflow=pacs", icon: Scan, color: "bg-rose-100 text-danger" },
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
        { label: "Communication Hub", description: "Messages, pages, and calls", href: "/communication", icon: MessageSquare, color: "bg-primary-soft text-primary" },
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
        { label: "Billing", description: "Bills & invoices", href: "/finance/billing", icon: FileText, color: "bg-primary-soft text-primary" },
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
      { label: "Providers", description: "Provider registry", href: "/registry/providers", icon: Stethoscope, color: "bg-teal-100 text-teal-600", serviceSlug: "varapi" },
      { label: "Facilities", description: "Facility registry", href: "/registry/facilities", icon: Building2, color: "bg-purple-100 text-purple-600", serviceSlug: "tuso" },
      { label: "Products", description: "Product catalogue", href: "/registry/products", icon: Package, color: "bg-orange-100 text-orange-600", serviceSlug: "msika" },
      { label: "Terminology", description: "ICD, SNOMED, LOINC", href: "/registry/terminology", icon: BookOpen, color: "bg-primary-soft text-primary", serviceSlug: "zibo" },
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
        { label: "Patient PHID", description: "Generate patient health IDs", href: "/id-services?tab=generate", icon: Users, color: "bg-primary-soft text-primary" },
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
        { label: "INDAWO Sites", description: "Premises registry", href: "/public-health?tab=sites", icon: Shield, color: "bg-emerald-100 text-primary", serviceSlug: "indawo" },
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
        { label: "Settlement", description: "Remittance & payouts", href: "/coverage?tab=settlement", icon: Receipt, color: "bg-emerald-100 text-primary" },
        { label: "Schemes", description: "Plan administration", href: "/coverage?tab=schemes", icon: Shield, color: "bg-primary-soft text-primary" },
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
      { label: "Marketplace", description: "Health products & vendors", href: "/marketplace", icon: ShoppingCart, color: "bg-purple-100 text-purple-600", serviceSlug: "msika" },
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
        { label: "Offline Sync", description: "Sync status & conflicts", href: "/clinical-tools?tab=offline", icon: Shield, color: "bg-primary-soft text-primary" },
        { label: "Documents", description: "Document management", href: "/clinical-tools?tab=documents", icon: FileText, color: "bg-primary-soft text-primary" },
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
        { label: "Production Command Centre", description: "Discover services, demo paths, and live integration health", href: "/production-command-centre", icon: LayoutGrid, color: "bg-primary-soft text-primary" },
        { label: WORKER_ACCESS_LABELS.workerAccess, description: "Providers, staff & local access", href: "/admin/users", icon: Users, color: "bg-red-100 text-red-600" },
        { label: "Audit Trail", description: "System audit logs", href: "/admin/audit", icon: ClipboardList, color: "bg-amber-100 text-amber-600" },
        { label: "System Settings", description: "Configuration & security", href: "/admin", icon: Settings, color: "bg-neutral-100 text-muted-foreground" },
      ],
    });
    cats.push({
      id: "ai-governance",
      title: "AI Governance",
      icon: Shield,
      color: "bg-cyan-600",
      modules: [
        { label: "AI Governance Hub", description: "Policy, audit & decision controls", href: "/ai-governance", icon: Shield, color: "bg-cyan-100 text-cyan-700" },
        { label: "Governance Datasets", description: "Register and classify datasets", href: "/ai-governance?tab=datasets", icon: Database, color: "bg-indigo-100 text-primary-hover" },
        { label: "Decision Rules", description: "Policy rules for AI access", href: "/ai-governance?tab=rules", icon: Shield, color: "bg-violet-100 text-violet-700" },
        { label: "Policy Publishing", description: "Publish AI governance policy versions", href: "/ai-governance?tab=policy", icon: FileText, color: "bg-emerald-100 text-primary-hover" },
      ],
    });
  }

  if (roles.isClinical || roles.isAdmin || roles.isFinance || roles.isDispenser) {
    cats.push({
      id: "production-readiness",
      title: "Production readiness",
      icon: LayoutGrid,
      color: "bg-primary",
      modules: [
        ...(roles.isAdmin ? [
          {
            label: "Production Command Centre",
            description: "Service discovery, maturity labels, and integration probes",
            href: "/production-command-centre",
            icon: LayoutGrid,
            color: "bg-primary-soft text-primary",
          },
        ] : []),
        ...(roles.isClinical || roles.isAdmin ? [
          {
            label: "Data & intelligence",
            description: "Quality, pipelines, integration, and audit intelligence",
            href: "/data-intelligence",
            icon: Database,
            color: "bg-indigo-100 text-primary-hover",
          },
          {
            label: "Inpatient workspace",
            description: "Admissions, nursing workbench, and ward operations",
            href: "/clinical/inpatient",
            icon: BedDouble,
            color: "bg-purple-100 text-warning-foreground",
          },
          {
            label: "Core transactions",
            description: "Transaction audit feed and state history",
            href: "/core-transaction",
            icon: Layers,
            color: "bg-neutral-100 text-foreground",
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
            color: "bg-neutral-100 text-foreground",
          },
        ] : []),
      ],
    });
  }

  return cats;
}
