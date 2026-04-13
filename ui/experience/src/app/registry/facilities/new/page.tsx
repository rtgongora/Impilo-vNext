"use client";

/**
 * Create facility — admin form.
 * Route: /registry/facilities/new
 */

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Loader2, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateFacility,
  type FacilityAdminPayload,
  type FacilityLevel,
  type FacilityOwnership,
} from "@/hooks/queries/useFacilityAdmin";

const inputClass =
  "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400";

export default function NewFacilityPage() {
  const router = useRouter();
  const createMutation = useCreateFacility();
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
    createMutation.mutate(payload, {
      onSuccess: () => {
        router.push("/registry/facilities");
      },
    });
  }

  return (
    <AppLayout>
      <PageShell title="New Facility" subtitle="Register a healthcare facility">
        <div className="mb-4">
          <Link
            href="/registry/facilities"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to facilities
          </Link>
        </div>

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
                placeholder="e.g. Hospital, Clinic"
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

          {createMutation.isError && (
            <p className="text-sm text-red-600">Could not create facility. Check the form and try again.</p>
          )}

          <div className="flex items-center gap-3 pt-2">
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-impilo-500 text-white rounded-lg hover:bg-impilo-600 disabled:opacity-50 transition-colors"
            >
              {createMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Save className="w-4 h-4" />
                  Create facility
                </>
              )}
            </button>
          </div>
        </form>
      </PageShell>
    </AppLayout>
  );
}
