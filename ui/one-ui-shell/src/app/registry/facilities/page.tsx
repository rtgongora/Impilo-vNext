"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Building2, ClipboardList, Loader2, Plus, Search, ShieldCheck, TriangleAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { FacilitiesGeoMapPanel } from "@/components/maps/FacilitiesGeoMapPanel";
import { FacilityDataQualityBanner } from "@/components/registry/FacilityDataQualityBanner";
import {
  useFacilityDashboardSummary,
  useFacilityRegistryFacilities,
} from "@/hooks/queries/useFacilityRegulatory";

const STATUS_STYLES: Record<string, string> = {
  REGISTERED_ACTIVE: "bg-success-soft text-primary-hover border border-success/25",
  PENDING_INSPECTION: "bg-warning-soft text-warning-foreground border border-warning/35",
  PENDING_RECTIFICATION: "bg-danger-soft text-danger border border-danger/28",
  PENDING_COMMITTEE_REVIEW: "bg-sky-50 text-sky-700 border border-sky-200",
  RESTRICTED: "bg-orange-50 text-orange-700 border border-orange-200",
  SUSPENDED: "bg-danger-soft text-danger border border-danger/28",
};

function statusLabel(status: string) {
  return status.replaceAll("_", " ");
}

export default function FacilityRegistryPage() {
  const [searchTerm, setSearchTerm] = useState("");
  const [regulatoryStatus, setRegulatoryStatus] = useState<string>("");
  const { data, isLoading, error } = useFacilityRegistryFacilities({
    search: searchTerm || undefined,
    regulatoryStatus: regulatoryStatus || undefined,
    page: 0,
    size: 30,
  });
  const dashboard = useFacilityDashboardSummary();

  const facilities = data?.data.items ?? [];
  const metrics = useMemo(
    () => [
      {
        title: "Registered active",
        value: dashboard.data?.data.activeFacilities ?? 0,
        icon: ShieldCheck,
        tone: "bg-success-soft text-primary-hover border-emerald-100",
      },
      {
        title: "Pending inspection",
        value: dashboard.data?.data.pendingInspection ?? 0,
        icon: ClipboardList,
        tone: "bg-warning-soft text-warning-foreground border-amber-100",
      },
      {
        title: "Overdue shortfalls",
        value: dashboard.data?.data.overdueComplianceActions ?? 0,
        icon: TriangleAlert,
        tone: "bg-danger-soft text-danger border-rose-100",
      },
    ],
    [dashboard.data],
  );

  return (
    <AppLayout>
      <PageShell
        title="Facility Regulatory Operations"
        subtitle="HPA-governed facility registration, inspections, compliance, renewal, and certificate oversight"
        serviceSlug="tuso"
      >
        <RegistryPlaneContextBar />
        <FacilityDataQualityBanner />
        <div className="mb-4">
          <Link
            href="/registry"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to registry hub
          </Link>
        </div>

        <div className="mb-6 grid gap-4 md:grid-cols-3">
          {metrics.map((metric) => {
            const Icon = metric.icon;
            return (
              <div key={metric.title} className={`rounded-2xl border p-5 ${metric.tone}`}>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-[0.2em] opacity-80">{metric.title}</p>
                    <p className="mt-3 text-3xl font-semibold">{metric.value}</p>
                  </div>
                  <div className="rounded-2xl bg-card/70 p-3">
                    <Icon className="h-6 w-6" />
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        <div className="mb-6">
          <FacilitiesGeoMapPanel
            title="Tuso facility registry map"
            subtitle="HPA-governed facilities with Ndila coordinates"
            search={searchTerm || undefined}
            size={100}
          />
        </div>

        <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card p-4">
          <div className="flex flex-1 flex-wrap items-center gap-3">
            <div className="relative min-w-[240px] flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                type="text"
                placeholder="Search by facility name or code"
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                className="w-full rounded-xl border border-border px-10 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
            <select
              value={regulatoryStatus}
              onChange={(event) => setRegulatoryStatus(event.target.value)}
              className="rounded-xl border border-border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            >
              <option value="">All lifecycle states</option>
              <option value="REGISTERED_ACTIVE">Registered active</option>
              <option value="PENDING_INSPECTION">Pending inspection</option>
              <option value="PENDING_RECTIFICATION">Pending rectification</option>
              <option value="PENDING_COMMITTEE_REVIEW">Pending committee review</option>
              <option value="RESTRICTED">Restricted</option>
              <option value="SUSPENDED">Suspended</option>
            </select>
          </div>
          <Link
            href="/registry/facilities/new"
            className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-emerald-700"
          >
            <Plus className="h-4 w-4" />
            New application
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            Loading facility registry...
          </div>
        ) : error ? (
          <div className="rounded-2xl border border-danger/28 bg-danger-soft p-6 text-center text-sm text-danger">
            Facility regulatory data could not be loaded right now.
          </div>
        ) : facilities.length === 0 ? (
          <div className="rounded-2xl border border-border bg-card p-12 text-center">
            <Building2 className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No facilities matched the current search.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-2xl border border-border bg-card">
            <table className="w-full text-sm">
              <thead className="bg-background text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Facility</th>
                  <th className="px-4 py-3 font-medium">Institution File</th>
                  <th className="px-4 py-3 font-medium">Lifecycle</th>
                  <th className="px-4 py-3 font-medium">Location</th>
                  <th className="px-4 py-3 font-medium">Open work</th>
                  <th className="px-4 py-3 font-medium text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {facilities.map((facility) => (
                  <tr key={facility.facilityId} className="border-t border-border align-top hover:bg-background/60">
                    <td className="px-4 py-4">
                      <div className="font-medium text-foreground">{facility.name}</div>
                      <div className="mt-1 text-xs text-muted-foreground">
                        {facility.facilityCode} • {facility.facilityType}
                      </div>
                    </td>
                    <td className="px-4 py-4 text-muted-foreground">
                      {facility.institutionFileNumber ?? "Pending allocation"}
                    </td>
                    <td className="px-4 py-4">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${
                          STATUS_STYLES[facility.regulatoryStatus] ?? "bg-neutral-100 text-foreground border border-border"
                        }`}
                      >
                        {statusLabel(facility.regulatoryStatus)}
                      </span>
                      {facility.latestCertificateExpiryDate ? (
                        <p className="mt-2 text-xs text-muted-foreground">
                          Cert expires {facility.latestCertificateExpiryDate}
                        </p>
                      ) : null}
                    </td>
                    <td className="px-4 py-4 text-muted-foreground">
                      {[facility.district, facility.province].filter(Boolean).join(", ") || "Not stated"}
                    </td>
                    <td className="px-4 py-4 text-muted-foreground">
                      <div>{facility.openApplications} open application(s)</div>
                      <div className="text-xs text-rose-600">{facility.overdueActions} overdue shortfall(s)</div>
                    </td>
                    <td className="px-4 py-4 text-right">
                      <Link
                        href={`/registry/facilities/${facility.facilityId}`}
                        className="inline-flex rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:border-emerald-300 hover:text-primary-hover"
                      >
                        Open workspace
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
