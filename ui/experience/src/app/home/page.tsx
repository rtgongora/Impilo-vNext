"use client";

/**
 * Home — Dashboard with welcome message, quick actions, and recent activity.
 * Route: /home | pageTitle: "Home"
 */

import Link from "next/link";
import {
  Users,
  BookOpen,
  BarChart3,
  Clock,
  ArrowRight,
  Building2,
  Activity,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";

const QUICK_ACTIONS = [
  {
    title: "Start Queue",
    description: "Open the patient queue dashboard",
    href: "/queue",
    icon: Users,
    color: "bg-blue-100 text-blue-600",
  },
  {
    title: "View Registry",
    description: "Browse providers, facilities, and terminology",
    href: "/registry",
    icon: BookOpen,
    color: "bg-green-100 text-green-600",
  },
  {
    title: "Reports",
    description: "View clinical and operational reports",
    href: "/reports",
    icon: BarChart3,
    color: "bg-purple-100 text-purple-600",
  },
] as const;

export default function HomePage() {
  const user = useAuthStore((s) => s.user);
  const facility = useFacilityStore((s) => s.facility);
  const shift = useShiftStore((s) => s.shift);

  const greeting = getGreeting();

  return (
    <AppLayout>
      <PageShell title="Home">
        <div className="space-y-6">
          {/* Welcome Message */}
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <h2 className="text-xl font-semibold text-gray-900">
              {greeting}, {user?.displayName ?? "User"}
            </h2>
            <p className="text-sm text-gray-500 mt-1">
              Welcome to Impilo vNext Health Information Exchange
            </p>

            {/* Context Summary */}
            <div className="mt-4 flex flex-wrap gap-4">
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
              {!shift && (
                <Link
                  href="/facility"
                  className="flex items-center gap-2 text-sm text-blue-600 bg-blue-50 px-3 py-1.5 rounded-lg hover:bg-blue-100 transition-colors"
                >
                  <Clock className="w-4 h-4" />
                  <span>Start a shift</span>
                  <ArrowRight className="w-3 h-3" />
                </Link>
              )}
            </div>
          </div>

          {/* Quick Actions */}
          <div>
            <h3 className="text-sm font-semibold text-gray-700 mb-3 uppercase tracking-wide">
              Quick Actions
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {QUICK_ACTIONS.map((action) => {
                const Icon = action.icon;
                return (
                  <Link
                    key={action.href}
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

          {/* Recent Activity */}
          <div>
            <h3 className="text-sm font-semibold text-gray-700 mb-3 uppercase tracking-wide">
              Recent Activity
            </h3>
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="space-y-3">
                <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                  <Clock className="w-4 h-4 text-gray-400 shrink-0" />
                  <div className="flex-1">
                    <p className="text-sm text-gray-700">System initialized</p>
                    <p className="text-xs text-gray-400">
                      Your recent clinical and administrative activities will appear here
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
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
