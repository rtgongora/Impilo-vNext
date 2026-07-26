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
import { ProviderCertificatesPanel } from "@/components/registry/ProviderCertificatesPanel";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
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
  const { data, isLoading, isError: linkedIdsUnavailable } = useLinkedIds();

  const { data: affData, isLoading: affLoading, isError: affUnavailable } = useAffiliations();
  const { data: noticeData, isLoading: noticeLoading, isError: noticesUnavailable } = useProviderNotices();
  const affiliations = (affData?.data ?? []).map((a) => ({ id: a.id, name: a.attributes.facilityName, role: a.attributes.role }));
  const notices = (noticeData?.data ?? []).map((n) => ({ id: n.id, text: n.attributes.text, severity: n.attributes.severity, category: n.attributes.category }));
  const linkedAttrs = data?.data?.attributes;
  const providerId = linkedAttrs?.providerId ?? user?.providerId;
  // These two defaults were the whole defect on this page: an unreachable registry rendered a
  // green "Active (MCAZ)" and a valid licence. Regulatory standing is the one claim on this
  // screen a provider may act on, and it must never be manufactured from a failed read.
  const providerStatus = linkedAttrs?.providerStatus ?? "Active";
  const licenceValid = linkedAttrs?.licenceValid ?? true;

  return (
    <AppLayout>
      <div className="max-w-3xl mx-auto space-y-6">
        {/* Page header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">
              Professional Profile
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Manage your professional registration, affiliations, and
              compliance.
            </p>
          </div>
          <div className="hidden sm:flex items-center gap-2">
            <Link
              href="/home/profile"
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-muted-foreground bg-card border border-border rounded-lg hover:bg-background transition-colors"
            >
              <User className="w-3.5 h-3.5" />
              Personal Profile
            </Link>
          </div>
        </div>

        {/* Registration & Licensing */}
        <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-border bg-background">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <BadgeCheck className="h-4 w-4 text-primary" />
              Registration & Licensing
            </h2>
          </div>
          <div className="px-6 py-4">
            {isLoading ? (
              <div className="animate-pulse space-y-3">
                <div className="h-4 bg-neutral-100 rounded w-3/4" />
                <div className="h-4 bg-neutral-100 rounded w-1/2" />
                <div className="h-4 bg-neutral-100 rounded w-2/3" />
              </div>
            ) : linkedIdsUnavailable ? (
              <div className="rounded-lg border border-red-200 bg-red-50 p-4">
                <p className="text-sm font-medium text-red-700">
                  Registration and licence status could not be read.
                </p>
                <p className="mt-1 text-xs text-red-700">
                  This screen is not evidence that your registration is active or your licence
                  valid. Confirm with your council before relying on it for clinical practice.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="flex items-center justify-between py-1">
                  <span className="text-sm text-muted-foreground">Provider ID</span>
                  <span className="text-sm font-mono font-semibold text-foreground">
                    {providerId ?? "Not linked"}
                  </span>
                </div>
                <div className="flex items-center justify-between py-1">
                  <span className="text-sm text-muted-foreground">Registration</span>
                  <span
                    className={[
                      "inline-flex items-center gap-1.5 text-sm font-medium",
                      providerStatus === "Active"
                        ? "text-primary-hover"
                        : "text-warning-foreground",
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
                  <span className="text-sm text-muted-foreground">Licence</span>
                  <span
                    className={[
                      "text-sm font-medium",
                      licenceValid ? "text-primary-hover" : "text-brand-red",
                    ].join(" ")}
                  >
                    {licenceValid ? "Valid" : "Expired"}
                  </span>
                </div>
                <div className="flex items-center gap-2 pt-2">
                  <Link
                    href="/home/credentials"
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-primary bg-primary-soft border border-primary/25 rounded-lg hover:bg-primary-soft transition-colors"
                  >
                    <RefreshCw className="w-3.5 h-3.5" />
                    Renew
                  </Link>
                  <Link
                    href="/home/credentials"
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-muted-foreground bg-card border border-border rounded-lg hover:bg-background transition-colors"
                  >
                    View Details
                    <ExternalLink className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            )}
          </div>
        </section>

        {/* My certificates (self-service list + PDF download) */}
        <ProviderCertificatesPanel />

        {/* Facility Affiliations */}
        <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-border bg-background">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <Building2 className="h-4 w-4 text-primary" />
              Facility Affiliations
            </h2>
          </div>
          <div className="divide-y divide-gray-100">
            {affLoading ? (
              <div className="px-6 py-6 animate-pulse space-y-3">
                <div className="h-4 bg-neutral-100 rounded w-3/4" />
                <div className="h-4 bg-neutral-100 rounded w-1/2" />
              </div>
            ) : affUnavailable ? (
              /* "No affiliations yet — ask HR to link you" sends a provider who is already
                 affiliated to chase a non-problem, and hides the facility context their work
                 depends on. */
              <div className="px-6 py-6 text-sm font-medium text-red-700 text-center bg-red-50">
                Facility affiliations could not be read. This is not a record that you have none.
              </div>
            ) : affiliations.length === 0 ? (
              <div className="px-6 py-6 text-sm text-muted-foreground text-center">
                No facility affiliations yet. Your employer&apos;s HR department can link you to a facility.
              </div>
            ) : affiliations.map((aff) => (
              <div
                key={aff.id}
                className="px-6 py-3 flex items-center justify-between"
              >
                <div className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-primary-soft flex items-center justify-center">
                    <Building2 className="h-4 w-4 text-primary" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-foreground">
                      {aff.name}
                    </p>
                    <p className="text-xs text-muted-foreground">{aff.role}</p>
                  </div>
                </div>
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              </div>
            ))}
          </div>
          <div className="px-6 py-3 border-t border-border">
            <Link
              href="/registry/facilities"
              className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:text-primary-hover transition-colors"
            >
              View All Affiliations
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </section>

        {/* Professional Notices */}
        <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-border bg-background">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <Bell className="h-4 w-4 text-primary" />
              Professional Notices
            </h2>
          </div>
          <div className="divide-y divide-gray-100">
            {noticeLoading ? (
              <div className="px-6 py-6 animate-pulse space-y-3">
                <div className="h-4 bg-neutral-100 rounded w-3/4" />
                <div className="h-4 bg-neutral-100 rounded w-1/2" />
              </div>
            ) : noticesUnavailable ? (
              /* Notices carry compliance alerts. "No professional notices at this time" is an
                 all-clear on the provider's regulatory standing — never issue it from an outage. */
              <div className="px-6 py-6 text-sm font-medium text-red-700 text-center bg-red-50">
                Professional notices could not be read. This is not an all-clear — a compliance
                notice may be outstanding.
              </div>
            ) : notices.length === 0 ? (
              <div className="px-6 py-6 text-sm text-muted-foreground text-center">
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
                        ? "bg-warning-soft"
                        : "bg-primary-soft",
                    ].join(" ")}
                  >
                    <NoticeIcon
                      className={[
                        "h-4 w-4",
                        notice.severity === "warning"
                          ? "text-amber-600"
                          : "text-primary",
                      ].join(" ")}
                    />
                  </div>
                  <p className="text-sm text-foreground">{notice.text}</p>
                </div>
              );
            })}
          </div>
          <div className="px-6 py-3 border-t border-border">
            <Link
              href="/home/notifications"
              className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:text-primary-hover transition-colors"
            >
              View All Notices
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </section>

        {/* Quick Actions */}
        <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-border bg-background">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <Shield className="h-4 w-4 text-primary" />
              Quick Actions
            </h2>
          </div>
          <div className="p-4 grid gap-3 sm:grid-cols-2">
            <Link
              href="/facility"
              className="flex items-center gap-3 rounded-xl border border-border p-4 hover:border-primary/25 hover:bg-primary-soft/50 transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-primary-soft flex items-center justify-center group-hover:bg-primary-soft transition-colors">
                <Stethoscope className="h-5 w-5 text-primary" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-foreground">
                  Start Work Session
                </p>
                <p className="text-xs text-muted-foreground">Select facility & shift</p>
              </div>
              <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-primary transition-colors" />
            </Link>

            <Link
              href="/telemedicine"
              className="flex items-center gap-3 rounded-xl border border-border p-4 hover:border-primary/25 hover:bg-primary-soft/50 transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-info-soft flex items-center justify-center group-hover:bg-blue-100 transition-colors">
                <Monitor className="h-5 w-5 text-primary" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-foreground">
                  Telemedicine Queue
                </p>
                <p className="text-xs text-muted-foreground">Remote consultations</p>
              </div>
              <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-blue-500 transition-colors" />
            </Link>

            <Link
              href="/clinical"
              className="flex items-center gap-3 rounded-xl border border-border p-4 hover:border-primary/25 hover:bg-primary-soft/50 transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-success-soft flex items-center justify-center group-hover:bg-emerald-100 transition-colors">
                <Calendar className="h-5 w-5 text-primary" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-foreground">
                  Independent Practice
                </p>
                <p className="text-xs text-muted-foreground">Private consultations</p>
              </div>
              <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-emerald-500 transition-colors" />
            </Link>

            <Link
              href="/home/profile"
              className="flex items-center gap-3 rounded-xl border border-border p-4 hover:border-border hover:bg-background transition-colors group"
            >
              <div className="h-10 w-10 rounded-lg bg-neutral-100 flex items-center justify-center group-hover:bg-neutral-100 transition-colors">
                <User className="h-5 w-5 text-muted-foreground" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-foreground">
                  Personal Profile
                </p>
                <p className="text-xs text-muted-foreground">Back to personal view</p>
              </div>
              <ArrowRight className="h-4 w-4 text-muted-foreground group-hover:text-muted-foreground transition-colors" />
            </Link>
          </div>
        </section>
      </div>

      <NompiloHint
        message="This is your professional control panel. Review your registration, licences, and facility affiliations here. To start seeing patients, tap 'Start Work Session'."
      />
    </AppLayout>
  );
}
