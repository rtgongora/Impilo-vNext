"use client";

/**
 * Professional Profile — Professional control layer.
 * Route: /professional
 *
 * NOT the live work environment. This page surfaces:
 * - Registration & Licensing status
 * - Facility affiliations
 * - Professional notices and compliance alerts
 * - Quick actions (start work session, telemedicine, etc.)
 *
 * Health OS Identity Doctrine: professional capacity is an overlay on
 * the person anchor, not a separate login.
 */

import { useMemo } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  BadgeCheck,
  Bell,
  Building2,
  Calendar,
  ChevronRight,
  ClipboardList,
  ExternalLink,
  Monitor,
  RefreshCw,
  Shield,
  Stethoscope,
  User,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** Facility affiliation shape from the BFF. */
interface Affiliation {
  id: string;
  attributes: { facilityName: string; role: string };
}

/** Professional notice shape from the BFF. */
interface ProfessionalNotice {
  id: string;
  attributes: { text: string; severity: "warning" | "info"; category: string };
}

function useAffiliations() {
  return useQuery<ApiResponse<Affiliation[]>>({
    queryKey: ["affiliations"],
    queryFn: () => apiClient.get("/internal/v1/identity/affiliations"),
  });
}

function useProviderNotices() {
  return useQuery<ApiResponse<ProfessionalNotice[]>>({
    queryKey: ["provider-notices"],
    queryFn: () => apiClient.get("/internal/v1/identity/notices"),
  });
}

export default function ProfessionalProfilePage() {
  const { user } = useAuthStore();
  const { data, isLoading } = useLinkedIds();

  const { data: affData, isLoading: affLoading } = useAffiliations();
  const { data: noticeData, isLoading: noticeLoading } = useProviderNotices();
  const affiliations = (affData?.data ?? []).map((a) => ({ id: a.id, name: a.attributes.facilityName, role: a.attributes.role }));
  const notices = (noticeData?.data ?? []).map((n) => ({ id: n.id, text: n.attributes.text, severity: n.attributes.severity, category: n.attributes.category }));
  const linkedAttrs = data?.data?.attributes;
  const providerId = linkedAttrs?.providerId ?? user?.providerId;
  const providerStatus = linkedAttrs?.providerStatus ?? "Active";
  const licenceValid = linkedAttrs?.licenceValid ?? true;

  const licenceExpiry = useMemo(() => {
    const date = new Date();
    date.setFullYear(date.getFullYear() + 1);
    return date.toLocaleDateString("en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  }, []);

  return (
    <AppLayout>
      <div className="max-w-3xl mx-auto space-y-6">
        {/* Page header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">
              Professional Profile
            </h1>
            <p className="mt-1 text-sm text-gray-500">
              Manage your professional registration, affiliations, and
              compliance.
            </p>
          </div>
          <div className="hidden sm:flex items-center gap-2">
            <Link
              href="/home/profile"
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
            >
              <User className="w-3.5 h-3.5" />
              Personal Profile
            </Link>
          </div>
        </div>

        {/* Registration & Licensing */}
        <section className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 bg-gray-50">
            <h2 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <BadgeCheck className="h-4 w-4 text-impilo-500" />
              Registration & Licensing
            </h2>
          </div>
          <div className="px-6 py-4">
            {isLoading ? (
              <div className="animate-pulse space-y-3">
                <div className="h-4 bg-gray-100 rounded w-3/4" />
                <div className="h-4 bg-gray-100 rounded w-1/2" />
                <div className="h-4 bg-gray-100 rounded w-2/3" />
              </div>
            ) : (
              <div className="space-y-3">
                <div className="flex items-center justify-between py-1">
                  <span className="text-sm text-gray-500">Provider ID</span>
                  <span className="text-sm font-mono font-semibold text-gray-900">
                    {providerId ?? "Not linked"}
                  </span>
                </div>
                <div className="flex items-center justify-between py-1">
                  <span className="text-sm text-gray-500">Registration</span>
                  <span
                    className={[
                      "inline-flex items-center gap-1.5 text-sm font-medium",
                      providerStatus === "Active"
                        ? "text-emerald-700"
                        : "text-amber-700",
                    ].join(" ")}
                  >
                    <span
                      className={[
                        "h-2 w-2 rounded-full",
                        providerStatus === "Active"
                          ? "bg-emerald-500"
                          : "bg-amber-500",
                      ].join(" ")}
                    />
                    {providerStatus} (MCAZ)
                  </span>
                </div>
                <div className="flex items-center justify-between py-1">
                  <span className="text-sm text-gray-500">Licence</span>
                  <span
                    className={[
                      "text-sm font-medium",
                      licenceValid ? "text-emerald-700" : "text-brand-red",
                    ].join(" ")}
                  >
                    {licenceValid ? "Valid" : "Expired"} until {licenceExpiry}
                  </span>
                </div>
                <div className="flex items-center gap-2 pt-2">
                  <Link
                    href="/home/credentials"
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-impilo-600 bg-impilo-50 border border-impilo-200 rounded-lg hover:bg-impilo-100 transition-colors"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    Renew
                  </Link>
                  <Link
                    href="/home/credentials"
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                  >
                    View Details
                    <ExternalLink className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            )}
          </div>
        </section>

        {/* Facility Affiliations */}
        <section className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 bg-gray-50">
            <h2 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Building2 className="h-4 w-4 text-impilo-500" />
              Facility Affiliations
            </h2>
          </div>
          <div className="divide-y divide-gray-100">
            {affLoading ? (
              <div className="px-6 py-6 animate-pulse space-y-3">
                <div className="h-4 bg-gray-100 rounded w-3/4" />
                <div className="h-4 bg-gray-100 rounded w-1/2" />
              </div>
            ) : affiliations.length === 0 ? (
              <div className="px-6 py-6 text-sm text-gray-400 text-center">
                No facility affiliations yet. Your employer&apos;s HR department can link you to a facility.
              </div>
            ) : affiliations.map((aff) => (
              <div
                key={aff.id}
                className="px-6 py-3 flex items-center justify-between"
              >
                <div className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-impilo-50 flex items-center justify-center">
                    <Building2 className="h-4 w-4 text-impilo-500" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-gray-900">
                      {aff.name}
                    </p>
                    <p className="text-xs text-gray-500">{aff.role}</p>
                  </div>
                </div>
                <ChevronRight className="h-4 w-4 text-gray-300" />
              </div>
            ))}
          </div>
          <div className="px-6 py-3 border-t border-gray-100">
            <Link
              href="/registry/facilities"
              className="inline-flex items-center gap-1.5 text-xs font-medium text-impilo-600 hover:text-impilo-700 transition-colors"
            >
              View All Affiliations
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </section>

        {/* Professional Notices */}
        <section className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 bg-gray-50">
            <h2 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Bell className="h-4 w-4 text-impilo-500" />
              Professional Notices
            </h2>
          </div>
          <div className="divide-y divide-gray-100">
            {noticeLoading ? (
              <div className="px-6 py-6 animate-pulse space-y-3">
                <div className="h-4 bg-gray-100 rounded w-3/4" />
                <div className="h-4 bg-gray-100 rounded w-1/2" />
              </div>
            ) : notices.length === 0 ? (
              <div className="px-6 py-6 text-sm text-gray-400 text-center">
                No professional notices at this time.
              </div>
            ) : notices.map((notice) => {
              const NoticeIcon = notice.severity === "warning" ? Bell : ClipboardList;
              return (
                <div
                  key={notice.id}
                  className="px-6 py-3 flex items-center gap-3"
                >
                  <div
                    className={[
                      "h-8 w-8 rounded-lg flex items-center justify-center",
                      notice.severity === "warning"
                        ? "bg-amber-50"
                        : "bg-impilo-50",
                    ].join(" ")}
                  >
                    <NoticeIcon
                      className={[
                        "h-4 w-4",
                        notice.severity === "warning"
                          ? "text-amber-600"
                          : "text-impilo-600",
                      ].join(" ")}
                    />
                  </div>
                  <p className="text-sm text-gray-700">{notice.text}</p>
                </div>
              );
            })}
          </div>
          <div className="px-6 py-3 border-t border-gray-100">
            <Link
              href="/home/notifications"
              className="inline-flex items-center gap-1.5 text-xs font-medium text-impilo-600 hover:text-impilo-700 transition-colors"
            >
              View All Notices
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </section>

        {/* Quick Actions */}
        <section className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 bg-gray-50">
            <h2 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Shield className="h-4 w-4 text-impilo-500" />
              Quick Actions
            </h2>
          </div>
          <div className="p-4 grid gap-3 sm:grid-cols-2">
            <Link
              href="/facility"
              className="flex items-center gap-3 rounded-xl border border-gray-200 p-4 hover:border-impilo-200 hover:bg-impilo-50/50 transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-impilo-50 flex items-center justify-center group-hover:bg-impilo-100 transition-colors">
                <Stethoscope className="h-5 w-5 text-impilo-600" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900">
                  Start Work Session
                </p>
                <p className="text-xs text-gray-500">Select facility & shift</p>
              </div>
              <ArrowRight className="h-4 w-4 text-gray-300 group-hover:text-impilo-500 transition-colors" />
            </Link>

            <Link
              href="/telemedicine"
              className="flex items-center gap-3 rounded-xl border border-gray-200 p-4 hover:border-impilo-200 hover:bg-impilo-50/50 transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-blue-50 flex items-center justify-center group-hover:bg-blue-100 transition-colors">
                <Monitor className="h-5 w-5 text-blue-600" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900">
                  Telemedicine Queue
                </p>
                <p className="text-xs text-gray-500">Remote consultations</p>
              </div>
              <ArrowRight className="h-4 w-4 text-gray-300 group-hover:text-blue-500 transition-colors" />
            </Link>

            <Link
              href="/clinical"
              className="flex items-center gap-3 rounded-xl border border-gray-200 p-4 hover:border-impilo-200 hover:bg-impilo-50/50 transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-emerald-50 flex items-center justify-center group-hover:bg-emerald-100 transition-colors">
                <Calendar className="h-5 w-5 text-emerald-600" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900">
                  Independent Practice
                </p>
                <p className="text-xs text-gray-500">Private consultations</p>
              </div>
              <ArrowRight className="h-4 w-4 text-gray-300 group-hover:text-emerald-500 transition-colors" />
            </Link>

            <Link
              href="/home/profile"
              className="flex items-center gap-3 rounded-xl border border-gray-200 p-4 hover:border-gray-300 hover:bg-gray-50 transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-gray-100 flex items-center justify-center group-hover:bg-gray-200 transition-colors">
                <User className="h-5 w-5 text-gray-600" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-gray-900">
                  Personal Profile
                </p>
                <p className="text-xs text-gray-500">Back to personal view</p>
              </div>
              <ArrowRight className="h-4 w-4 text-gray-300 group-hover:text-gray-500 transition-colors" />
            </Link>
          </div>
        </section>
      </div>
    </AppLayout>
  );
}
