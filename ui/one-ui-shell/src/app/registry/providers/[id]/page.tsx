"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { REGISTRY_ENTITY_COLUMNS } from "@/lib/json-api/generic-table-columns";
/**
 * Provider Detail — View provider profile, specialties, and schedule.
 * Route: /registry/providers/[id]
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  AlertTriangle,
  ArrowLeft,
  UserCircle,
  GraduationCap,
  Building2,
  Calendar,
  Pencil,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ProviderCertificatesAdminPanel } from "@/components/registry/ProviderCertificatesAdminPanel";
import { ProviderLifecyclePanel } from "@/components/registry/ProviderLifecyclePanel";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { ProviderPracticeLocationsMapPanel } from "@/components/maps/ProviderPracticeLocationsMapPanel";

interface ProviderDetail {
  id: string;
  type: "provider";
  attributes: {
    displayName: string;
    email: string;
    phone: string;
    specialties: string[];
    qualifications: Array<{
      name: string;
      institution: string;
      year: number;
    }>;
    facilities: Array<{
      id: string;
      name: string;
    }>;
    status: string;
    schedule: Array<{
      day: string;
      startTime: string;
      endTime: string;
    }>;
    [key: string]: unknown;
  };
}

export default function ProviderDetailPage() {
  const params = useParams();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<ProviderDetail>>({
    queryKey: ["providers", id],
    queryFn: () =>
      apiClient.get<ApiResponse<ProviderDetail>>(`/internal/v1/registry/providers/${id}`),
    enabled: !!id,
  });

  const { data: wcEnvelope } = useQuery({
    queryKey: ["providers", id, "work-context"],
    queryFn: () =>
      apiClient.get<ApiResponse<Record<string, unknown>>>(`/internal/v1/registry/providers/${id}/work-context`),
    enabled: !!id,
  });

  const provider = data?.data;
  const workContext = wcEnvelope?.data;

  // Numeric registry id (W0 bridge) — surfaced flat or under attributes depending on shape.
  const providerBag = (provider ?? {}) as unknown as Record<string, unknown>;
  const attrsBag = (provider?.attributes ?? {}) as Record<string, unknown>;
  const numericProviderId =
    typeof providerBag.providerId === "number"
      ? (providerBag.providerId as number)
      : typeof attrsBag.providerId === "number"
        ? (attrsBag.providerId as number)
        : null;

  return (
    <AppLayout>
      <PageShell title="Provider Profile" subtitle="Provider details and schedule">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link
            href="/registry/providers"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Providers
          </Link>
          <Link
            href={`/registry/providers/${id}/edit`}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-primary-hover bg-info-soft border border-info/25 rounded-lg hover:bg-blue-100 transition-colors"
          >
            <Pencil className="w-4 h-4" />
            Edit
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading provider...</span>
          </div>
        ) : error || !provider ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load provider</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Basic Info */}
            <div className="bg-card rounded-lg border border-border p-5">
              <div className="flex items-start gap-4 mb-4">
                <div className="w-12 h-12 rounded-lg bg-blue-100 flex items-center justify-center shrink-0">
                  <UserCircle className="w-6 h-6 text-primary" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-foreground">
                    {provider.attributes.displayName}
                  </h3>
                  <p className="text-sm text-muted-foreground">{provider.attributes.email}</p>
                  {provider.attributes.phone && (
                    <p className="text-sm text-muted-foreground">{provider.attributes.phone}</p>
                  )}
                </div>
              </div>
              <div>
                <span
                  className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                    provider.attributes.status === "ACTIVE"
                      ? "bg-green-100 text-green-700"
                      : "bg-neutral-100 text-foreground"
                  }`}
                >
                  {provider.attributes.status}
                </span>
              </div>
            </div>

            {/* Lifecycle transition console */}
            <ProviderLifecyclePanel providerPublicId={id} />

            {/* Certificates (registrar lifecycle) */}
            <ProviderCertificatesAdminPanel providerPublicId={id} providerId={numericProviderId} />


            {/* Specialties */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3">Specialties</h3>
              {(provider.attributes.specialties?.length ?? 0) > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {provider.attributes.specialties.map((spec) => (
                    <span
                      key={spec}
                      className="inline-block px-3 py-1 text-xs rounded-full bg-info-soft text-primary-hover font-medium"
                    >
                      {spec}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No specialties listed</p>
              )}
            </div>

            {/* Qualifications */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
                <GraduationCap className="w-4 h-4" /> Qualifications
              </h3>
              {(provider.attributes.qualifications?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {provider.attributes.qualifications.map((qual, i) => (
                    <div key={i} className="p-3 bg-background rounded-lg">
                      <p className="text-sm font-medium text-foreground">{qual.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {qual.institution} &middot; {qual.year}
                      </p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No qualifications listed</p>
              )}
            </div>

            {/* Facilities */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
                <Building2 className="w-4 h-4" /> Associated Facilities
              </h3>
              {(provider.attributes.facilities?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {provider.attributes.facilities.map((fac) => (
                    <Link
                      key={fac.id}
                      href={`/registry/facilities/${fac.id}`}
                      className="flex items-center gap-2 p-3 bg-background rounded-lg hover:bg-neutral-100 text-sm"
                    >
                      <Building2 className="w-4 h-4 text-muted-foreground" />
                      <span className="font-medium text-foreground">{fac.name}</span>
                    </Link>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No facilities assigned</p>
              )}
            </div>

            {(provider.attributes.facilities?.length ?? 0) > 0 ? (
              <ProviderPracticeLocationsMapPanel
                facilityIds={provider.attributes.facilities.map((fac) => fac.id)}
              />
            ) : null}

            {/* Varapi + Tuso work context */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3">Work context (Varapi + Tuso)</h3>
              {workContext ? (
                <JsonApiDataTable data={workContext} columns={REGISTRY_ENTITY_COLUMNS} emptyTitle="No work context" />
              ) : (
                <p className="text-sm text-muted-foreground">Loading or unavailable…</p>
              )}
            </div>

            {/* Schedule */}
            <div className="bg-card rounded-lg border border-border p-5">
              <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
                <Calendar className="w-4 h-4" /> Schedule
              </h3>
              {(provider.attributes.schedule?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {provider.attributes.schedule.map((slot, i) => (
                    <div
                      key={i}
                      className="flex items-center justify-between p-3 bg-background rounded-lg text-sm"
                    >
                      <span className="font-medium text-foreground">{slot.day}</span>
                      <span className="text-muted-foreground">
                        {slot.startTime} - {slot.endTime}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No schedule configured</p>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
