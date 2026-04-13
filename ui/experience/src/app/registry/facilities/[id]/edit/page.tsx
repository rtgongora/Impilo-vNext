"use client";

/**
 * Edit facility — admin form.
 * Route: /registry/facilities/[id]/edit
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Loader2, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { FacilityResource } from "@/hooks/queries/useFacilities";
import {
  useUpdateFacility,
  type FacilityAdminPayload,
  type FacilityLevel,
  type FacilityOwnership,
} from "@/hooks/queries/useFacilityAdmin";

const inputClass =
  "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400";

function coerceLevel(v: unknown): FacilityLevel {
  const s = String(v ?? "").toUpperCase();
  if (s === "TERTIARY" || s === "SECONDARY" || s === "PRIMARY" || s === "CLINIC") {
    return s;
  }
  return "PRIMARY";
}

function coerceOwnership(v: unknown): FacilityOwnership {
  const s = String(v ?? "").toUpperCase();
  if (s === "PUBLIC" || s === "PRIVATE" || s === "NGO" || s === "FAITH_BASED") {
    return s;
  }
  return "PUBLIC";
}

export default function EditFacilityPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;

  const { data, isLoading, error } = useQuery<ApiResponse<FacilityResource>>({
    queryKey: ["facilities", id],
    queryFn: () => apiClient.get<ApiResponse<FacilityResource>>(`/internal/v1/facilities/${id}`),
    enabled: !!id,
  });

  const updateMutation = useUpdateFacility(id);

  const [form, setForm] = useState({
    facilityCode: "",
    name: "",
    type: "",
    level: "PRIMARY" as FacilityLevel,
    ownership: "PUBLIC" as FacilityOwnership,
    province: "",
    district: "",
    latitude: "",
    longitude: "",
  });

  const facility = data?.data;

  useEffect(() => {
    if (!facility) return;
    const attrs = facility.attributes as Record<string, unknown>;
    setForm({
      facilityCode: facility.attributes.code ?? "",
      name: facility.attributes.name ?? "",
      type: facility.attributes.facilityType ?? "",
      level: coerceLevel(attrs.level),
      ownership: coerceOwnership(attrs.ownership),
      province: (attrs.province as string) ?? "",
      district: (attrs.district as string) ?? "",
      latitude:
        attrs.latitude !== undefined && attrs.latitude !== null
          ? String(attrs.latitude)
          : "",
      longitude:
        attrs.longitude !== undefined && attrs.longitude !== null
          ? String(attrs.longitude)
          : "",
    });
  }, [facility]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const payload: FacilityAdminPayload = {
      facilityCode: form.facilityCode.trim(),
      name: form.name.trim(),
      type: form.type.trim(),
      level: form.level,
      ownership: form.ownership,
      province: form.province.trim(),
      district: form.district.trim(),
      latitude: Number.parseFloat(form.latitude) || 0,
      longitude: Number.parseFloat(form.longitude) || 0,
    };
    updateMutation.mutate(payload, {
      onSuccess: () => {
        router.push(`/registry/facilities/${id}`);
      },
    });
  }

  return (
    <AppLayout>
      <PageShell title="Edit Facility" subtitle="Update facility registration details">
        <div className="mb-4">
          <Link
            href={`/registry/facilities/${id}`}
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to facility profile
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading facility...</span>
          </div>
        ) : error || !facility ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center text-sm text-red-600">
            Failed to load facility.
          </div>
        ) : (
          <form
            onSubmit={handleSubmit}
            className="max-w-2xl space-y-4 bg-white rounded-lg border border-gray-200 p-6"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">
                  Facility code
                </label>
                <input
                  type="text"
                  required
                  value={form.facilityCode}
                  onChange={(e) => setForm({ ...form, facilityCode: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Name</label>
                <input
                  type="text"
                  required
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Type</label>
                <input
                  type="text"
                  required
                  value={form.type}
                  onChange={(e) => setForm({ ...form, type: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Level</label>
                <select
                  value={form.level}
                  onChange={(e) =>
                    setForm({ ...form, level: e.target.value as FacilityLevel })
                  }
                  className={inputClass}
                >
                  <option value="TERTIARY">Tertiary</option>
                  <option value="SECONDARY">Secondary</option>
                  <option value="PRIMARY">Primary</option>
                  <option value="CLINIC">Clinic</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">
                  Ownership
                </label>
                <select
                  value={form.ownership}
                  onChange={(e) =>
                    setForm({ ...form, ownership: e.target.value as FacilityOwnership })
                  }
                  className={inputClass}
                >
                  <option value="PUBLIC">Public</option>
                  <option value="PRIVATE">Private</option>
                  <option value="NGO">NGO</option>
                  <option value="FAITH_BASED">Faith-based</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Province</label>
                <input
                  type="text"
                  required
                  value={form.province}
                  onChange={(e) => setForm({ ...form, province: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">District</label>
                <input
                  type="text"
                  required
                  value={form.district}
                  onChange={(e) => setForm({ ...form, district: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Latitude</label>
                <input
                  type="number"
                  step="any"
                  value={form.latitude}
                  onChange={(e) => setForm({ ...form, latitude: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Longitude</label>
                <input
                  type="number"
                  step="any"
                  value={form.longitude}
                  onChange={(e) => setForm({ ...form, longitude: e.target.value })}
                  className={inputClass}
                />
              </div>
            </div>

            {updateMutation.isError && (
              <p className="text-sm text-red-600">Could not save changes. Try again.</p>
            )}

            <div className="flex items-center gap-3 pt-2">
              <button
                type="submit"
                disabled={updateMutation.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-impilo-500 text-white rounded-lg hover:bg-impilo-600 disabled:opacity-50 transition-colors"
              >
                {updateMutation.isPending ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="w-4 h-4" />
                    Save changes
                  </>
                )}
              </button>
            </div>
          </form>
        )}
      </PageShell>
    </AppLayout>
  );
}
