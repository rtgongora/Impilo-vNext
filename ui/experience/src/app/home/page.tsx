"use client";

/**
 * Home — Role-aware dashboard with workplace hub, professional stats,
 * module categories, and recent activity.
 *
 * Lovable reference: ModuleHome with WorkplaceSelectionHub,
 * MyProfessionalHub (Dashboard/Affiliations/Schedule/Credentials),
 * and ExpandableCategoryCards.
 */

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Users, BookOpen, BarChart3, Clock, ArrowRight, Building2,
  Activity, Receipt, Pill, Calendar, Shield, Stethoscope,
  ClipboardList, Package, Settings, FileText, MapPin, Loader2,
  ChevronRight, Video, ShoppingCart, Database, AlertTriangle,
  Briefcase, Heart, Globe, Siren, Award, GraduationCap, User,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { useFacilities, type FacilityResource } from "@/hooks/queries/useFacilities";
import { useProviderLicenses, hasActiveLicense } from "@/hooks/queries/useLicenses";
import { useProviderPrivileges } from "@/hooks/queries/useProviderPrivileges";
import { useCommunityGroups, useJoinGroup } from "@/hooks/queries/useCommunity";
import { apiClient } from "@/lib/api-client";

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

// ── Module categories (aligned to Lovable ExpandableCategoryCards) ──
function getModuleCategories(roles: {
  isClinical: boolean; isAdmin: boolean; isFinance: boolean; isDispenser: boolean;
}): ModuleCategory[] {
  const cats: ModuleCategory[] = [];

  if (roles.isClinical) {
    cats.push({
      id: "clinical",
      title: "Clinical Care",
      icon: Stethoscope,
      color: "bg-blue-500",
      modules: [
        { label: "Clinical Hub", description: "All 10 clinical modules", href: "/clinical", icon: Stethoscope, color: "bg-blue-100 text-blue-600" },
        { label: "Patient Queue", description: "Waiting patients & triage", href: "/queue", icon: Users, color: "bg-orange-100 text-orange-600" },
        { label: "Patient Search", description: "Find patients by name or ID", href: "/queue/search", icon: Users, color: "bg-gray-100 text-gray-600" },
        { label: "Bed Management", description: "Ward status & admissions", href: "/beds", icon: Building2, color: "bg-purple-100 text-purple-600" },
        { label: "Scheduling", description: "Appointments & booking", href: "/scheduling", icon: Calendar, color: "bg-cyan-100 text-cyan-600" },
        { label: "Telemedicine", description: "Virtual consultations", href: "/telemedicine", icon: Video, color: "bg-teal-100 text-teal-600" },
        { label: "Shift Handoff", description: "Care continuity reports", href: "/shift/handover", icon: Clock, color: "bg-amber-100 text-amber-600" },
        { label: "Discharge & Exit", description: "Discharges, deaths & exits", href: "/queue", icon: Users, color: "bg-amber-100 text-amber-600" },
        { label: "Control Tower", description: "Real-time facility ops", href: "/queue", icon: BarChart3, color: "bg-rose-100 text-rose-600" },
        { label: "Operations", description: "Shifts, roster & workforce", href: "/shift", icon: Clock, color: "bg-cyan-100 text-cyan-600" },
      ],
    });
  }

  if (roles.isClinical || roles.isDispenser) {
    cats.push({
      id: "orders",
      title: "Orders & Pharmacy",
      icon: Pill,
      color: "bg-green-500",
      modules: [
        ...(roles.isDispenser ? [
          { label: "Pharmacy", description: "Dispensing & medication tracking", href: "/pharmacy", icon: Pill, color: "bg-green-100 text-green-600" },
          { label: "Prescriptions", description: "View pending prescriptions", href: "/pharmacy/prescriptions", icon: FileText, color: "bg-blue-100 text-blue-600" },
          { label: "Stock", description: "Pharmacy stock levels", href: "/pharmacy/stock", icon: Package, color: "bg-amber-100 text-amber-600" },
        ] : []),
        ...(roles.isClinical ? [
          { label: "Walk-in Registration", description: "New patient intake", href: "/queue/walk-in", icon: Users, color: "bg-orange-100 text-orange-600" },
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
        { label: "Billing", description: "Bills & invoices", href: "/finance/billing", icon: FileText, color: "bg-blue-100 text-blue-600" },
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
      { label: "Terminology", description: "ICD, SNOMED, LOINC", href: "/registry/terminology", icon: BookOpen, color: "bg-blue-100 text-blue-600" },
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
        { label: "Patient PHID", description: "Generate patient health IDs", href: "/id-services?tab=generate", icon: Users, color: "bg-blue-100 text-blue-600" },
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
      id: "omnichannel",
      title: "Omnichannel & Access",
      icon: Shield,
      color: "bg-teal-500",
      modules: [
        { label: "Omnichannel Hub", description: "All channels in one view", href: "/omnichannel", icon: Shield, color: "bg-teal-100 text-teal-600" },
        { label: "SMS Journeys", description: "Text-based health interactions", href: "/omnichannel?tab=sms", icon: FileText, color: "bg-amber-100 text-amber-600" },
        { label: "Callbacks", description: "Human handoff queue", href: "/omnichannel?tab=callbacks", icon: Users, color: "bg-rose-100 text-rose-600" },
        { label: "Disclosure Rules", description: "Channel data policies", href: "/omnichannel?tab=disclosure", icon: Shield, color: "bg-indigo-100 text-indigo-600" },
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
        { label: "Schemes", description: "Plan administration", href: "/coverage?tab=schemes", icon: Shield, color: "bg-blue-100 text-blue-600" },
      ],
    });
  }

  cats.push({
    id: "operations",
    title: "Operations & Inventory",
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
        { label: "Voice Dictation", description: "Speech-to-text for notes", href: "/clinical-tools", icon: Shield, color: "bg-pink-100 text-pink-600" },
        { label: "Offline Sync", description: "Sync status & conflicts", href: "/clinical-tools?tab=offline", icon: Shield, color: "bg-blue-100 text-blue-600" },
        { label: "Documents", description: "Document management", href: "/clinical-tools?tab=documents", icon: FileText, color: "bg-blue-100 text-blue-600" },
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

  return cats;
}

type HomeTab = "work" | "professional" | "personal";

export default function HomePage() {
  const user = useAuthStore((s) => s.user);
  const facility = useFacilityStore((s) => s.facility);
  const shift = useShiftStore((s) => s.shift);
  const roleGroup = useRoleGroup();
  const { isClinical, isAdmin, isFinance, isDispenser, isCitizen } = roleGroup;
  const [expandedCategory, setExpandedCategory] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<HomeTab>(() => {
    if (typeof window === "undefined") return "work";
    return (sessionStorage.getItem("exp:home-tab") as HomeTab) || "work";
  });
  function switchTab(tab: HomeTab) {
    setActiveTab(tab);
    sessionStorage.setItem("exp:home-tab", tab);
  }

  const greeting = getGreeting();
  const router = useRouter();
  const hasWorkContext = !!facility;

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

  // Community groups for Personal tab
  const { data: groupsData } = useCommunityGroups();
  const communityGroups = groupsData?.data ?? [];
  const joinGroup = useJoinGroup();

  function selectFacility(f: FacilityResource) {
    useFacilityStore.getState().setFacility({
      id: f.id,
      name: f.attributes.name,
      code: f.attributes.code,
      facilityType: f.attributes.facilityType,
      capabilities: f.attributes.capabilities ?? [],
    });
    useWorkModeStore.getState().setMode("clinical");
    router.push("/workspace");
  }

  // Fetch license data for clinical providers
  const { data: licenseData } = useProviderLicenses(isClinical ? user?.id : undefined);
  const licenses = licenseData?.data ?? [];
  const licenseActive = licenses.length === 0 || hasActiveLicense(licenses);

  // Fetch recent encounters
  const { data: recentEncounters } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["home-recent-encounters"],
    queryFn: () => apiClient.get("/internal/v1/encounters?size=5"),
    enabled: isClinical,
  });
  const encounters = recentEncounters?.data ?? [];

  // Fetch today's appointments
  const { data: appointmentsData } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["home-today-appointments"],
    queryFn: () => apiClient.get("/internal/v1/appointments?size=5"),
    enabled: isClinical,
  });
  const appointments = appointmentsData?.data ?? [];

  // Module categories
  const categories = getModuleCategories({ isClinical, isAdmin, isFinance, isDispenser });

  return (
    <AppLayout>
      <PageShell title="Home">
        <div className="space-y-6">
          {/* Welcome + Context */}
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-xl font-semibold text-gray-900">
                  {greeting}, {user?.displayName ?? "User"}
                </h2>
                <p className="text-sm text-gray-500 mt-1">
                  {user?.roles?.length ? user.roles.join(" · ") : "Welcome to Impilo vNext"}
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
                  <span className="px-2.5 py-1 text-xs font-medium rounded-full bg-blue-50 text-blue-700">
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
                <Link href="/shift" className="flex items-center gap-2 text-sm text-blue-600 bg-blue-50 px-3 py-1.5 rounded-lg hover:bg-blue-100 transition-colors">
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
              <Stethoscope className="w-4 h-4" /> Professional
            </button>
            <button onClick={() => switchTab("personal")}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === "personal" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"}`}>
              <Heart className="w-4 h-4" /> Personal
            </button>
          </div>

          {/* ═══ WORK TAB ═══ */}
          {activeTab === "work" && (<>

          {/* Workplace Selection Hub — when no facility */}
          {!hasWorkContext && (
            <div className="bg-white rounded-lg border-2 border-blue-200 p-6">
              <div className="flex items-center gap-2 mb-4">
                <MapPin className="w-5 h-5 text-blue-600" />
                <h3 className="text-base font-semibold text-gray-900">Where are you working from?</h3>
              </div>
              <p className="text-sm text-gray-500 mb-4">Select your facility to begin clinical, operational, or administrative work.</p>
              {facilitiesLoading ? (
                <div className="flex items-center gap-2 py-6 justify-center">
                  <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                  <span className="text-sm text-gray-500">Loading facilities...</span>
                </div>
              ) : facilities.length === 0 ? (
                <div className="text-center py-6">
                  <Building2 className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                  <p className="text-sm text-gray-400">No facilities available</p>
                  <Link href="/facility" className="mt-2 inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-800">
                    Browse all facilities <ArrowRight className="w-3 h-3" />
                  </Link>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  {facilities.slice(0, 6).map((f) => (
                    <button key={f.id} onClick={() => selectFacility(f)}
                      className="text-left bg-gray-50 rounded-lg border border-gray-200 p-4 hover:border-blue-300 hover:bg-blue-50 transition-all group">
                      <div className="flex items-start gap-3">
                        <Building2 className="w-5 h-5 text-gray-400 group-hover:text-blue-500 shrink-0 mt-0.5" />
                        <div>
                          <p className="text-sm font-medium text-gray-900 group-hover:text-blue-700">{f.attributes.name}</p>
                          <p className="text-xs text-gray-500 mt-0.5">{f.attributes.facilityType} &middot; {f.attributes.code}</p>
                        </div>
                      </div>
                    </button>
                  ))}
                  {facilities.length > 6 && (
                    <Link href="/facility" className="flex items-center justify-center gap-1 text-sm text-blue-600 hover:text-blue-800 p-4 border border-dashed border-gray-300 rounded-lg hover:border-blue-300 transition-colors">
                      View all {facilities.length} facilities <ArrowRight className="w-3 h-3" />
                    </Link>
                  )}
                </div>
              )}
              {(isAdmin || isFinance) && (
                <div className="mt-4 pt-4 border-t border-gray-200">
                  <p className="text-xs text-gray-500 mb-2">Or work without a facility context:</p>
                  <div className="flex flex-wrap gap-2">
                    {isAdmin && (
                      <button onClick={() => { useWorkModeStore.getState().setMode("admin"); router.push("/admin"); }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors">
                        <Shield className="w-3.5 h-3.5" /> Administration
                      </button>
                    )}
                    {isFinance && (
                      <button onClick={() => { useWorkModeStore.getState().setMode("finance"); router.push("/finance"); }}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors">
                        <Receipt className="w-3.5 h-3.5" /> Finance
                      </button>
                    )}
                    <Link href="/reports" className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors">
                      <BarChart3 className="w-3.5 h-3.5" /> Reports
                    </Link>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Professional Dashboard — stats + schedule (Lovable MyProfessionalHub) */}
          {isClinical && hasWorkContext && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Today's Schedule */}
              <div className="bg-white rounded-lg border border-gray-200">
                <div className="px-5 py-3 border-b flex items-center justify-between">
                  <h3 className="text-sm font-medium text-gray-900">Today&apos;s Schedule</h3>
                  <Link href="/scheduling" className="text-xs text-blue-600 hover:text-blue-800">View All →</Link>
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
                  <Link href="/queue" className="text-xs text-blue-600 hover:text-blue-800">Queue →</Link>
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
                <ClipboardList className="w-5 h-5 text-blue-600" />
                Communication Noticeboard
              </h3>
              <Link href="/communication" className="text-xs text-blue-600 hover:text-blue-800">
                View All →
              </Link>
            </div>
            <div className="flex flex-wrap gap-2 mb-3">
              <Link href="/communication?tab=messages"
                className="inline-flex items-center gap-2 px-5 py-3 text-sm font-medium border border-gray-200 rounded-lg hover:bg-blue-50 hover:border-blue-300 transition-colors">
                <FileText className="w-5 h-5 text-blue-600" />
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
                        isExpanded ? "border-blue-300 bg-blue-50 shadow-sm" : "border-gray-200 bg-white hover:border-gray-300 hover:shadow-sm"
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
                                <p className="text-sm text-gray-900 group-hover:text-blue-600">{mod.label}</p>
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
                <Link href="/finance" className="text-xs text-blue-600 hover:text-blue-800">Finance Dashboard →</Link>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Link href="/finance/billing" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><Receipt className="w-4 h-4 text-blue-500" /><span className="text-sm font-medium text-gray-900">Billing</span></div>
                  <p className="text-xs text-gray-500">View and manage bills</p>
                </Link>
                <Link href="/finance/payments" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition-colors">
                  <div className="flex items-center gap-2 mb-2"><FileText className="w-4 h-4 text-green-500" /><span className="text-sm font-medium text-gray-900">Payments</span></div>
                  <p className="text-xs text-gray-500">Track payment status</p>
                </Link>
                <Link href="/finance/claims" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition-colors">
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
              {/* Credentials & License */}
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
                    <Award className="w-5 h-5 text-blue-600" /> Credentials & License
                  </h3>
                  <Link href="/home/credentials" className="text-xs text-blue-600 hover:text-blue-800">
                    Full Details →
                  </Link>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {/* License Card */}
                  <div className={`rounded-lg border p-4 ${licenseActive ? "border-green-200 bg-green-50" : "border-red-200 bg-red-50"}`}>
                    <p className="text-xs text-gray-500 mb-1">Professional License</p>
                    <p className={`text-sm font-semibold ${licenseActive ? "text-green-700" : "text-red-700"}`}>
                      {licenseActive ? "Active" : "Requires Attention"}
                    </p>
                    {licenses.length > 0 && (
                      <p className="text-xs text-gray-500 mt-1">
                        {licenses.length} license{licenses.length > 1 ? "s" : ""} on record
                      </p>
                    )}
                  </div>
                  {/* CPD Card */}
                  <div className="rounded-lg border border-gray-200 bg-gray-50 p-4">
                    <p className="text-xs text-gray-500 mb-1">CPD Progress</p>
                    <p className="text-sm font-semibold text-gray-900">Current Cycle</p>
                    <div className="mt-2 bg-gray-200 rounded-full h-2">
                      <div className="bg-blue-500 rounded-full h-2" style={{ width: "72%" }} />
                    </div>
                    <p className="text-xs text-gray-500 mt-1">18/25 points earned</p>
                  </div>
                  {/* Certifications Card */}
                  <div className="rounded-lg border border-gray-200 bg-gray-50 p-4">
                    <p className="text-xs text-gray-500 mb-1">Certifications</p>
                    <div className="space-y-1.5 mt-1">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-gray-700">BLS Provider</span>
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-green-100 text-green-700">Valid</span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-gray-700">ACLS Provider</span>
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">Expiring</span>
                      </div>
                    </div>
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
                        <button onClick={() => selectFacility(f)}
                          className="text-xs text-blue-600 hover:text-blue-800 font-medium">
                          Start Shift →
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-sm text-gray-400">No facility affiliations found.</p>
                )}
              </div>

              {/* License-Based Work Modes */}
              {isClinical && (
                <div className="bg-white rounded-lg border border-gray-200 p-6">
                  <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                    <Globe className="w-5 h-5 text-teal-600" /> Independent & Field Work
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <button onClick={() => {
                      useWorkModeStore.getState().setMode("independent_practice", {
                        licenseNumber: licenses[0]?.licenseNumber ?? "",
                        licenseCategory: licenses[0]?.cadre ?? user?.roles?.[0] ?? "",
                      });
                      router.push("/queue");
                    }}
                      className="text-left bg-amber-50 rounded-lg border border-amber-200 p-4 hover:border-amber-400 transition-all group">
                      <Briefcase className="w-5 h-5 text-amber-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-amber-700">Independent Practice</p>
                      <p className="text-xs text-gray-500 mt-0.5">Work under your own license without facility context</p>
                    </button>
                    <button onClick={() => {
                      useWorkModeStore.getState().setMode("emergency_response", {
                        licenseNumber: licenses[0]?.licenseNumber ?? "",
                        licenseCategory: licenses[0]?.cadre ?? user?.roles?.[0] ?? "",
                      });
                      router.push("/queue");
                    }}
                      className="text-left bg-red-50 rounded-lg border border-red-200 p-4 hover:border-red-400 transition-all group">
                      <Siren className="w-5 h-5 text-red-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-red-700">Emergency Response</p>
                      <p className="text-xs text-gray-500 mt-0.5">Emergency work under license authority</p>
                    </button>
                    <button onClick={() => {
                      useWorkModeStore.getState().setMode("community_outreach", {
                        licenseNumber: licenses[0]?.licenseNumber ?? "",
                        licenseCategory: licenses[0]?.cadre ?? user?.roles?.[0] ?? "",
                      });
                      router.push("/queue");
                    }}
                      className="text-left bg-teal-50 rounded-lg border border-teal-200 p-4 hover:border-teal-400 transition-all group">
                      <Heart className="w-5 h-5 text-teal-600 mb-2" />
                      <p className="text-sm font-medium text-gray-900 group-hover:text-teal-700">Community Outreach</p>
                      <p className="text-xs text-gray-500 mt-0.5">Community health program work</p>
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
                  <Link href="/scheduling" className="flex flex-col items-center gap-2 p-4 rounded-lg bg-blue-50 border border-blue-200 hover:border-blue-400 transition-colors text-center">
                    <Calendar className="w-6 h-6 text-blue-600" />
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

              {/* Personal Links */}
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2 mb-4">
                  <User className="w-5 h-5 text-gray-600" /> Account & Settings
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <Link href="/home/profile" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-blue-300 transition-colors">
                    <User className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">Profile</p>
                      <p className="text-xs text-gray-500">View and edit your profile</p>
                    </div>
                  </Link>
                  <Link href="/home/preferences" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-blue-300 transition-colors">
                    <Settings className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">Preferences</p>
                      <p className="text-xs text-gray-500">Language, notifications, display</p>
                    </div>
                  </Link>
                  <Link href="/settings/security" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-blue-300 transition-colors">
                    <Shield className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">Security & Privacy</p>
                      <p className="text-xs text-gray-500">Password, MFA, sessions</p>
                    </div>
                  </Link>
                  <Link href="/marketplace" className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 border border-gray-200 hover:border-blue-300 transition-colors">
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
                          className="text-xs text-blue-600 hover:text-blue-800 font-medium"
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

/** Compact announcements banner showing pinned/urgent items from the noticeboard */
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
    normal: "bg-blue-100 text-blue-700 border-blue-200",
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
              <Activity className="w-4 h-4 text-blue-500" />
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
  const { data } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["personal-feed"],
    queryFn: () => apiClient.get("/internal/v1/mobile/citizen/feed?size=5"),
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
                {item.attributes.published_at && (
                  <p className="text-xs text-gray-400 mt-1">
                    {new Date(item.attributes.published_at as string).toLocaleDateString()}
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
