"use client";

/**
 * Home — Role-aware dashboard with contextual quick actions,
 * active shift/facility context, and recent encounter activity.
 * Route: /home | pageTitle: "Home"
 *
 * Lovable reference: ModuleHome with WorkplaceSelectionHub,
 * MyProfessionalHub, PersonalHub, and ExpandableCategoryCards.
 * Simplified for runtime: role-filtered quick actions + real data.
 */

import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Users, BookOpen, BarChart3, Clock, ArrowRight, Building2,
  Activity, Receipt, Pill, Calendar, Shield, Stethoscope,
  ClipboardList, UserPlus, Package, Settings, FileText,
  MapPin, Loader2,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useFacilities, type FacilityResource } from "@/hooks/queries/useFacilities";
import { apiClient } from "@/lib/api-client";

interface QuickAction {
  title: string;
  description: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
}

export default function HomePage() {
  const user = useAuthStore((s) => s.user);
  const facility = useFacilityStore((s) => s.facility);
  const shift = useShiftStore((s) => s.shift);
  const { isClinical, isPrescriber, isDispenser, isQueueManager, isAdmin, isFinance } = useRoleGroup();

  const greeting = getGreeting();

  // Build role-aware quick actions
  const actions: QuickAction[] = [];
  if (isQueueManager) {
    actions.push({
      title: "Patient Queue",
      description: "Open the patient queue dashboard",
      href: "/queue",
      icon: Users,
      color: "bg-blue-100 text-blue-600",
    });
  }
  if (isClinical) {
    actions.push({
      title: "Scheduling",
      description: "View and manage appointments",
      href: "/scheduling",
      icon: Calendar,
      color: "bg-cyan-100 text-cyan-600",
    });
  }
  if (isPrescriber) {
    actions.push({
      title: "Orders & Results",
      description: "Place and review clinical orders",
      href: "/queue",
      icon: ClipboardList,
      color: "bg-purple-100 text-purple-600",
    });
  }
  if (isDispenser) {
    actions.push({
      title: "Pharmacy",
      description: "Dispense medications and manage stock",
      href: "/pharmacy",
      icon: Pill,
      color: "bg-green-100 text-green-600",
    });
  }
  if (isFinance) {
    actions.push({
      title: "Finance",
      description: "Billing, payments, and claims",
      href: "/finance",
      icon: Receipt,
      color: "bg-emerald-100 text-emerald-600",
    });
  }
  if (isAdmin) {
    actions.push({
      title: "Administration",
      description: "User management, roles, and audit",
      href: "/admin",
      icon: Shield,
      color: "bg-red-100 text-red-600",
    });
  }
  // Always available
  actions.push(
    {
      title: "Registry",
      description: "Providers, facilities, and terminology",
      href: "/registry",
      icon: BookOpen,
      color: "bg-amber-100 text-amber-600",
    },
    {
      title: "Reports",
      description: "Clinical and operational reports",
      href: "/reports",
      icon: BarChart3,
      color: "bg-indigo-100 text-indigo-600",
    },
    {
      title: "Settings",
      description: "Account, security, and preferences",
      href: "/settings",
      icon: Settings,
      color: "bg-gray-100 text-gray-600",
    },
  );

  const router = useRouter();
  const hasWorkContext = !!facility;

  // Fetch facilities for workplace selection hub
  const { data: facilitiesData, isLoading: facilitiesLoading } = useFacilities();
  const facilities: FacilityResource[] = facilitiesData?.data ?? [];

  function selectFacility(f: FacilityResource) {
    useFacilityStore.getState().setFacility({
      id: f.id,
      name: f.attributes.name,
      code: f.attributes.code,
      facilityType: f.attributes.facilityType,
      capabilities: f.attributes.capabilities ?? [],
    });
    router.push("/workspace");
  }

  // Fetch recent encounters if clinical
  const { data: recentEncounters } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["home-recent-encounters"],
    queryFn: () => apiClient.get("/internal/v1/encounters?size=5"),
    enabled: isClinical,
  });

  const encounters = recentEncounters?.data ?? [];

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
                  {user?.roles?.length
                    ? user.roles.join(" · ")
                    : "Welcome to Impilo vNext"}
                </p>
              </div>
              {user?.actorType && (
                <span className="px-2.5 py-1 text-xs font-medium rounded-full bg-blue-50 text-blue-700">
                  {user.actorType}
                </span>
              )}
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
                  <span>
                    Shift active since{" "}
                    {new Date(shift.startedAt).toLocaleTimeString()}
                  </span>
                </div>
              )}
              {!shift && !facility && (
                <div className="flex items-center gap-2 text-sm text-amber-700 bg-amber-50 px-3 py-1.5 rounded-lg">
                  <MapPin className="w-4 h-4" />
                  <span>Select where you are working from below</span>
                </div>
              )}
              {facility && !shift && (
                <Link
                  href="/shift"
                  className="flex items-center gap-2 text-sm text-blue-600 bg-blue-50 px-3 py-1.5 rounded-lg hover:bg-blue-100 transition-colors"
                >
                  <Clock className="w-4 h-4" />
                  <span>Start a shift</span>
                  <ArrowRight className="w-3 h-3" />
                </Link>
              )}
            </div>
          </div>

          {/* Workplace Selection Hub — shown when no facility selected */}
          {!hasWorkContext && (
            <div className="bg-white rounded-lg border-2 border-blue-200 p-6">
              <div className="flex items-center gap-2 mb-4">
                <MapPin className="w-5 h-5 text-blue-600" />
                <h3 className="text-base font-semibold text-gray-900">Where are you working from?</h3>
              </div>
              <p className="text-sm text-gray-500 mb-4">
                Select your facility to begin clinical, operational, or administrative work.
              </p>

              {facilitiesLoading ? (
                <div className="flex items-center gap-2 py-6 justify-center">
                  <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
                  <span className="text-sm text-gray-500">Loading facilities...</span>
                </div>
              ) : facilities.length === 0 ? (
                <div className="text-center py-6">
                  <Building2 className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                  <p className="text-sm text-gray-400">No facilities available</p>
                  <Link
                    href="/facility"
                    className="mt-2 inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-800"
                  >
                    Browse all facilities <ArrowRight className="w-3 h-3" />
                  </Link>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  {facilities.slice(0, 6).map((f) => (
                    <button
                      key={f.id}
                      onClick={() => selectFacility(f)}
                      className="text-left bg-gray-50 rounded-lg border border-gray-200 p-4 hover:border-blue-300 hover:bg-blue-50 transition-all group"
                    >
                      <div className="flex items-start gap-3">
                        <Building2 className="w-5 h-5 text-gray-400 group-hover:text-blue-500 shrink-0 mt-0.5" />
                        <div>
                          <p className="text-sm font-medium text-gray-900 group-hover:text-blue-700">
                            {f.attributes.name}
                          </p>
                          <p className="text-xs text-gray-500 mt-0.5">
                            {f.attributes.facilityType} &middot; {f.attributes.code}
                          </p>
                        </div>
                      </div>
                    </button>
                  ))}
                  {facilities.length > 6 && (
                    <Link
                      href="/facility"
                      className="flex items-center justify-center gap-1 text-sm text-blue-600 hover:text-blue-800 p-4 border border-dashed border-gray-300 rounded-lg hover:border-blue-300 transition-colors"
                    >
                      View all {facilities.length} facilities <ArrowRight className="w-3 h-3" />
                    </Link>
                  )}
                </div>
              )}

              {/* Non-facility work modes */}
              {(isAdmin || isFinance) && (
                <div className="mt-4 pt-4 border-t border-gray-200">
                  <p className="text-xs text-gray-500 mb-2">Or work without a facility context:</p>
                  <div className="flex flex-wrap gap-2">
                    {isAdmin && (
                      <Link
                        href="/admin"
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
                      >
                        <Shield className="w-3.5 h-3.5" /> Administration
                      </Link>
                    )}
                    {isFinance && (
                      <Link
                        href="/finance"
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
                      >
                        <Receipt className="w-3.5 h-3.5" /> Finance
                      </Link>
                    )}
                    <Link
                      href="/reports"
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
                    >
                      <BarChart3 className="w-3.5 h-3.5" /> Reports
                    </Link>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Quick Actions */}
          <div>
            <h3 className="text-sm font-semibold text-gray-700 mb-3 uppercase tracking-wide">
              Quick Actions
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {actions.map((action) => {
                const Icon = action.icon;
                return (
                  <Link
                    key={action.href + action.title}
                    href={action.href}
                    className="bg-white rounded-lg border border-gray-200 p-5 hover:border-blue-300 hover:shadow-md transition-all group"
                  >
                    <div className="flex items-start gap-3">
                      <div
                        className={`w-10 h-10 rounded-lg ${action.color} flex items-center justify-center shrink-0`}
                      >
                        <Icon className="w-5 h-5" />
                      </div>
                      <div>
                        <h4 className="font-medium text-gray-900 text-sm group-hover:text-blue-600 transition-colors">
                          {action.title}
                        </h4>
                        <p className="text-xs text-gray-500 mt-0.5">{action.description}</p>
                      </div>
                    </div>
                  </Link>
                );
              })}
            </div>
          </div>

          {/* Recent Encounters (clinical users only) */}
          {isClinical && (
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
                  Recent Encounters
                </h3>
                <Link href="/queue" className="text-xs text-blue-600 hover:text-blue-800">
                  View Queue →
                </Link>
              </div>
              <div className="bg-white rounded-lg border border-gray-200">
                {encounters.length === 0 ? (
                  <div className="p-8 text-center">
                    <Stethoscope className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                    <p className="text-gray-400 text-sm">No recent encounters</p>
                    <p className="text-gray-400 text-xs mt-1">
                      Encounters from your shifts will appear here.
                    </p>
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100">
                    {encounters.map((enc) => {
                      const a = enc.attributes;
                      const status = (a.status as string) ?? "";
                      const isActive = status === "IN_PROGRESS" || status === "ACTIVE";
                      return (
                        <Link
                          key={enc.id}
                          href={`/ehr/${a.patient_id}/encounter/${enc.id}`}
                          className="flex items-center justify-between px-5 py-3 hover:bg-gray-50 transition-colors"
                        >
                          <div className="flex items-center gap-3">
                            <div className={`w-2 h-2 rounded-full ${isActive ? "bg-green-500" : "bg-gray-300"}`} />
                            <div>
                              <p className="text-sm font-medium text-gray-900">
                                {((a.encounter_type as string) ?? "Encounter").replace(/_/g, " ")}
                              </p>
                              <p className="text-xs text-gray-500">
                                {a.started_at
                                  ? new Date(a.started_at as string).toLocaleString()
                                  : "—"}
                              </p>
                            </div>
                          </div>
                          <span
                            className={`px-2 py-0.5 text-xs rounded-full font-medium ${
                              isActive
                                ? "bg-green-100 text-green-700"
                                : "bg-gray-100 text-gray-600"
                            }`}
                          >
                            {status.replace(/_/g, " ")}
                          </span>
                        </Link>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Finance Summary (finance users only) */}
          {isFinance && (
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wide">
                  Finance Overview
                </h3>
                <Link href="/finance" className="text-xs text-blue-600 hover:text-blue-800">
                  Finance Dashboard →
                </Link>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <Link href="/finance/billing" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition-colors">
                  <div className="flex items-center gap-2 mb-2">
                    <Receipt className="w-4 h-4 text-blue-500" />
                    <span className="text-sm font-medium text-gray-900">Billing</span>
                  </div>
                  <p className="text-xs text-gray-500">View and manage bills</p>
                </Link>
                <Link href="/finance/payments" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition-colors">
                  <div className="flex items-center gap-2 mb-2">
                    <FileText className="w-4 h-4 text-green-500" />
                    <span className="text-sm font-medium text-gray-900">Payments</span>
                  </div>
                  <p className="text-xs text-gray-500">Track payment status</p>
                </Link>
                <Link href="/finance/claims" className="bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 transition-colors">
                  <div className="flex items-center gap-2 mb-2">
                    <ClipboardList className="w-4 h-4 text-purple-500" />
                    <span className="text-sm font-medium text-gray-900">Claims</span>
                  </div>
                  <p className="text-xs text-gray-500">Insurance claim tracking</p>
                </Link>
              </div>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}
