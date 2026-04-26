"use client";

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
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

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

  return (
    <AppLayout>
      <PageShell title="Provider Profile" subtitle="Provider details and schedule">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link
            href="/registry/providers"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Providers
          </Link>
          <Link
            href={`/registry/providers/${id}/edit`}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-blue-700 bg-blue-50 border border-blue-200 rounded-lg hover:bg-blue-100 transition-colors"
          >
            <Pencil className="w-4 h-4" />
            Edit
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading provider...</span>
          </div>
        ) : error || !provider ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load provider</p>
          </div>
        ) : (
          <div className="max-w-2xl space-y-6">
            {/* Basic Info */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <div className="flex items-start gap-4 mb-4">
                <div className="w-12 h-12 rounded-lg bg-blue-100 flex items-center justify-center shrink-0">
                  <UserCircle className="w-6 h-6 text-blue-600" />
                </div>
                <div>
                  <h3 className="text-lg font-medium text-gray-900">
                    {provider.attributes.displayName}
                  </h3>
                  <p className="text-sm text-gray-500">{provider.attributes.email}</p>
                  {provider.attributes.phone && (
                    <p className="text-sm text-gray-500">{provider.attributes.phone}</p>
                  )}
                </div>
              </div>
              <div>
                <span
                  className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                    provider.attributes.status === "ACTIVE"
                      ? "bg-green-100 text-green-700"
                      : "bg-gray-100 text-gray-700"
                  }`}
                >
                  {provider.attributes.status}
                </span>
              </div>
            </div>

            {/* Specialties */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Specialties</h3>
              {(provider.attributes.specialties?.length ?? 0) > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {provider.attributes.specialties.map((spec) => (
                    <span
                      key={spec}
                      className="inline-block px-3 py-1 text-xs rounded-full bg-blue-50 text-blue-700 font-medium"
                    >
                      {spec}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No specialties listed</p>
              )}
            </div>

            {/* Qualifications */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <GraduationCap className="w-4 h-4" /> Qualifications
              </h3>
              {(provider.attributes.qualifications?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {provider.attributes.qualifications.map((qual, i) => (
                    <div key={i} className="p-3 bg-gray-50 rounded-lg">
                      <p className="text-sm font-medium text-gray-900">{qual.name}</p>
                      <p className="text-xs text-gray-500">
                        {qual.institution} &middot; {qual.year}
                      </p>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No qualifications listed</p>
              )}
            </div>

            {/* Facilities */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <Building2 className="w-4 h-4" /> Associated Facilities
              </h3>
              {(provider.attributes.facilities?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {provider.attributes.facilities.map((fac) => (
                    <Link
                      key={fac.id}
                      href={`/registry/facilities/${fac.id}`}
                      className="flex items-center gap-2 p-3 bg-gray-50 rounded-lg hover:bg-gray-100 text-sm"
                    >
                      <Building2 className="w-4 h-4 text-gray-400" />
                      <span className="font-medium text-gray-900">{fac.name}</span>
                    </Link>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No facilities assigned</p>
              )}
            </div>

            {/* Varapi + Tuso work context */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3">Work context (Varapi + Tuso)</h3>
              {workContext ? (
                <pre className="text-xs bg-slate-900 text-slate-100 p-3 rounded-lg max-h-64 overflow-auto">
                  {JSON.stringify(workContext, null, 2)}
                </pre>
              ) : (
                <p className="text-sm text-gray-400">Loading or unavailable…</p>
              )}
            </div>

            {/* Schedule */}
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="font-medium text-gray-900 mb-3 flex items-center gap-2">
                <Calendar className="w-4 h-4" /> Schedule
              </h3>
              {(provider.attributes.schedule?.length ?? 0) > 0 ? (
                <div className="space-y-2">
                  {provider.attributes.schedule.map((slot, i) => (
                    <div
                      key={i}
                      className="flex items-center justify-between p-3 bg-gray-50 rounded-lg text-sm"
                    >
                      <span className="font-medium text-gray-900">{slot.day}</span>
                      <span className="text-gray-600">
                        {slot.startTime} - {slot.endTime}
                      </span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-gray-400">No schedule configured</p>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
