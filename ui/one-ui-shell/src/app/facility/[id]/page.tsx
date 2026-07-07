"use client";

/**
 * Facility Detail — View facility info, workspaces, staff, services.
 * Route: /facility/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  AlertTriangle,
  ArrowLeft,
  Building2,
  Users,
  LayoutGrid,
  Activity,
  Fingerprint,
  CheckCircle2,
  XCircle,
  Clock,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useFacilityProvenance } from "@/hooks/queries/useFacilityImports";
import { useFacilityStatusComposite } from "@/hooks/queries/useFacilityStatusComposite";
import { FacilityLegitimacyPanel } from "@/components/facility/FacilityLegitimacyPanel";
import { formatServiceError } from "@/lib/service-error";

interface FacilityDetail {
  id: string;
  type: "facility";
  attributes: {
    name: string;
    code: string;
    facilityType: string;
    status: string;
    capabilities: string[];
    district: string;
    province: string;
    staffCount: number;
    workspaceCount: number;
    bedCapacity: number;
    occupiedBeds: number;
    workspaces: Array<{
      id: string;
      name: string;
      workspaceType: string;
      status: string;
    }>;
    services: string[];
    [key: string]: unknown;
  };
}

export default function FacilityDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<FacilityDetail>>({
    queryKey: ["facility-detail", id],
    queryFn: () => apiClient.get<ApiResponse<FacilityDetail>>(`/internal/v1/facilities/${id}`),
    enabled: !!id,
  });

  const facility = data?.data;

  // Import provenance + configuration-completeness (admin-scoped; renders only when available).
  const { data: provData } = useFacilityProvenance(id);
  const provenance = provData?.data;

  // Per-source legitimacy + derived platform-access verdict (fail-closed BFF proxy → TUSO).
  const { data: legitData, isError: legitIsError, error: legitError } = useFacilityStatusComposite(id);
  const legitimacy = legitData?.data;

  return (
    <AppLayout>
      <PageShell title="Facility Details" subtitle="Facility information and capacity">
        <div className="mb-4">
          <Link
            href="/facility"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Facilities
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading facility...</span>
          </div>
        ) : error || !facility ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load facility</p>
          </div>
        ) : (
          <div className="max-w-3xl space-y-6">
            {/* Header */}
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-start gap-4 mb-4">
                <div className="w-12 h-12 rounded-lg bg-primary-soft flex items-center justify-center shrink-0">
                  <Building2 className="w-6 h-6 text-primary" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-foreground">{facility.attributes.name}</h3>
                  <p className="text-sm text-muted-foreground">
                    {facility.attributes.code} &middot; {facility.attributes.facilityType}
                  </p>
                </div>
              </div>
              <dl className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                <div>
                  <dt className="text-muted-foreground">Status</dt>
                  <dd className="mt-0.5">
                    <span
                      className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                        facility.attributes.status === "ACTIVE"
                          ? "bg-green-100 text-green-700"
                          : "bg-neutral-100 text-foreground"
                      }`}
                    >
                      {facility.attributes.status}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">District</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {facility.attributes.district || "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Province</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {facility.attributes.province || "\u2014"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Staff</dt>
                  <dd className="font-medium text-foreground mt-0.5 flex items-center gap-1">
                    <Users className="w-4 h-4 text-muted-foreground" />
                    {facility.attributes.staffCount ?? "\u2014"}
                  </dd>
                </div>
              </dl>
            </div>

            {/* Capacity Stats */}
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-card rounded-lg border border-border p-4 text-center">
                <LayoutGrid className="w-6 h-6 text-impilo-400 mx-auto mb-1" />
                <p className="text-xl font-bold text-foreground">
                  {facility.attributes.workspaceCount ?? 0}
                </p>
                <p className="text-xs text-muted-foreground">Workspaces</p>
              </div>
              <div className="bg-card rounded-lg border border-border p-4 text-center">
                <Activity className="w-6 h-6 text-green-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-foreground">
                  {facility.attributes.bedCapacity ?? 0}
                </p>
                <p className="text-xs text-muted-foreground">Bed Capacity</p>
              </div>
              <div className="bg-card rounded-lg border border-border p-4 text-center">
                <Users className="w-6 h-6 text-amber-500 mx-auto mb-1" />
                <p className="text-xl font-bold text-foreground">
                  {facility.attributes.occupiedBeds ?? 0}
                </p>
                <p className="text-xs text-muted-foreground">Occupied Beds</p>
              </div>
            </div>

            {/* Import provenance + configuration-completeness (imported facilities) */}
            {provenance && (
              <div className="bg-card rounded-lg border border-border p-5 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="font-medium text-foreground flex items-center gap-2">
                    <Fingerprint className="w-4 h-4 text-primary" /> Identity & import provenance
                  </h3>
                  <FeatureMaturityBadge
                    status="connected"
                    detail="TUSO facility provenance read-model"
                  />
                </div>

                <dl className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                  <div>
                    <dt className="text-muted-foreground">Internal Impilo/TUSO facility ID</dt>
                    <dd className="font-mono text-xs text-foreground mt-0.5 break-all">
                      {provenance.identity.facilityUuid || provenance.identity.internalFacilityId || "—"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">
                      {provenance.identity.facilityCodeLabel}
                    </dt>
                    <dd className="font-medium text-foreground mt-0.5">
                      {provenance.identity.facilityCode || "—"}{" "}
                      <span className="text-xs text-muted-foreground">(not a login / access credential)</span>
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Source label</dt>
                    <dd className="font-medium text-foreground mt-0.5">
                      {provenance.identity.sourceLabel || "—"}
                      {provenance.identity.sourceRow ? ` · row ${provenance.identity.sourceRow}` : ""}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">Lifecycle state</dt>
                    <dd className="mt-0.5">
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-amber-100 text-amber-800">
                        {provenance.identity.lifecycleState || "—"}
                      </span>
                    </dd>
                  </div>
                </dl>

                {provenance.identity.externalIdentifiers.length > 0 && (
                  <div>
                    <p className="text-xs text-muted-foreground mb-1">External identifiers</p>
                    <div className="flex flex-wrap gap-2">
                      {provenance.identity.externalIdentifiers.map((ext) => (
                        <span
                          key={`${ext.system}:${ext.value}`}
                          className="inline-block px-2 py-1 text-xs rounded-md bg-neutral-100 text-foreground"
                          title={ext.issuingAuthority}
                        >
                          {ext.system}: {ext.value}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Acceptable missing fields — visible missingness, not defaults */}
                {(() => {
                  const m = provenance.acceptableMissing;
                  const missing: string[] = [];
                  if (m.missingLatitude) missing.push("Latitude");
                  if (m.missingLongitude) missing.push("Longitude");
                  if (m.missingFacilityType) missing.push("Facility type");
                  if (m.missingOwnership) missing.push("Ownership");
                  if (m.missingOperatingStatus) missing.push("Operating status");
                  return missing.length > 0 ? (
                    <div>
                      <p className="text-xs text-muted-foreground mb-1">Missing (kept visible, not defaulted)</p>
                      <div className="flex flex-wrap gap-2">
                        {missing.map((label) => (
                          <span
                            key={label}
                            className="inline-flex items-center gap-1 px-2 py-1 text-xs rounded-md bg-red-50 text-red-600"
                          >
                            <XCircle className="w-3 h-3" /> Missing {label.toLowerCase()}
                          </span>
                        ))}
                      </div>
                    </div>
                  ) : null;
                })()}

                {/* Configuration completeness checklist */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs text-muted-foreground">Setup completeness</p>
                    <span className="text-xs text-muted-foreground">
                      Downstream: {provenance.downstreamMaterialisationStatus}
                    </span>
                  </div>
                  <ul className="grid grid-cols-1 md:grid-cols-2 gap-1.5">
                    {provenance.checklist.map((c) => {
                      const icon =
                        c.status === "PRESENT" ? (
                          <CheckCircle2 className="w-4 h-4 text-green-600" />
                        ) : c.status === "PENDING_DOWNSTREAM" ? (
                          <Clock className="w-4 h-4 text-amber-500" />
                        ) : (
                          <XCircle className="w-4 h-4 text-red-400" />
                        );
                      return (
                        <li key={c.key} className="flex items-center gap-2 text-sm">
                          {icon}
                          <span className="text-foreground">{c.label}</span>
                          <span className="text-xs text-muted-foreground">
                            {c.status === "PENDING_DOWNSTREAM" ? `pending · ${c.owner}` : c.status.toLowerCase()}
                          </span>
                        </li>
                      );
                    })}
                  </ul>
                </div>
              </div>
            )}

            {/* Legitimacy & platform access (fail-closed BFF proxy → TUSO source-legitimacy) */}
            {legitimacy ? (
              <FacilityLegitimacyPanel composite={legitimacy} facilityId={id} />
            ) : legitIsError ? (
              <div
                role="alert"
                data-testid="facility-legitimacy-unavailable"
                className="bg-card rounded-lg border border-amber-300 dark:border-amber-800 p-5"
              >
                <h3 className="font-medium text-foreground mb-1">Legitimacy &amp; platform access</h3>
                <p className="text-sm text-amber-700 dark:text-amber-400">
                  Facility legitimacy could not be verified — the TUSO source-legitimacy service is
                  unavailable. Nothing was assumed; the platform-access verdict is withheld until the
                  service is reachable.
                </p>
                <p className="mt-1 text-xs text-muted-foreground">{formatServiceError(legitError)}</p>
              </div>
            ) : null}

            {/* Workspaces */}
            {(facility.attributes.workspaces?.length ?? 0) > 0 && (
              <div className="bg-card rounded-lg border border-border p-5">
                <h3 className="font-medium text-foreground mb-3">Workspaces</h3>
                <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
                  {facility.attributes.workspaces.map((ws) => (
                    <Link
                      key={ws.id}
                      href={`/workspace/${ws.id}`}
                      className="p-3 bg-background rounded-lg hover:bg-neutral-100 transition-colors"
                    >
                      <p className="text-sm font-medium text-foreground">{ws.name}</p>
                      <div className="flex items-center justify-between mt-1">
                        <span className="text-xs text-muted-foreground">{ws.workspaceType}</span>
                        <span
                          className={`inline-block px-1.5 py-0.5 text-xs rounded-full ${
                            ws.status === "ACTIVE"
                              ? "bg-green-100 text-green-700"
                              : "bg-neutral-100 text-muted-foreground"
                          }`}
                        >
                          {ws.status}
                        </span>
                      </div>
                    </Link>
                  ))}
                </div>
              </div>
            )}

            {/* Capabilities */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3">Capabilities</h3>
              {facility.attributes.capabilities.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {facility.attributes.capabilities.map((cap) => (
                    <span
                      key={cap}
                      className="inline-block px-3 py-1 text-xs rounded-full bg-primary-soft text-primary font-medium"
                    >
                      {cap}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No capabilities listed</p>
              )}
            </div>

            {/* Services */}
            {(facility.attributes.services?.length ?? 0) > 0 && (
              <div className="bg-card rounded-lg border border-border p-5">
                <h3 className="font-medium text-foreground mb-3">Services</h3>
                <div className="flex flex-wrap gap-2">
                  {facility.attributes.services.map((svc) => (
                    <span
                      key={svc}
                      className="inline-block px-3 py-1 text-xs rounded-full bg-green-50 text-green-700 font-medium"
                    >
                      {svc}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
