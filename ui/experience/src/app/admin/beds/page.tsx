"use client";

/**
 * Admin Bed/Ward Management — CRUD for wards and beds.
 * Route: /admin/beds
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Bed, Plus, Loader2, Building2 } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function AdminBedsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [wardName, setWardName] = useState("");
  const [wardType, setWardType] = useState("GENERAL");
  const [totalBeds, setTotalBeds] = useState(6);

  const { data: wardsData, isLoading } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["admin-wards", facility?.id],
    queryFn: () => apiClient.get(`/internal/v1/beds/wards?facility_id=${facility?.id}`),
    enabled: !!facility?.id,
  });

  const createWard = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post("/internal/v1/admin/wards", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-wards"] });
      setShowForm(false);
      setWardName("");
    },
  });

  const wards = wardsData?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Bed & Ward Administration" subtitle="Manage wards and bed inventory">
        <div className="mb-4 flex items-center justify-between">
          <Link href="/admin" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="w-4 h-4" /> Back to Admin
          </Link>
          <button onClick={() => setShowForm(!showForm)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700">
            <Plus className="w-4 h-4" /> Add Ward
          </button>
        </div>

        {showForm && (
          <div className="bg-white rounded-lg border border-gray-200 p-5 mb-4">
            <h3 className="font-medium text-gray-900 mb-3">New Ward</h3>
            <div className="grid grid-cols-3 gap-3">
              <input type="text" placeholder="Ward name" value={wardName} onChange={(e) => setWardName(e.target.value)}
                className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
              <select value={wardType} onChange={(e) => setWardType(e.target.value)}
                className="px-3 py-2 text-sm border border-gray-300 rounded-lg">
                <option value="GENERAL">General</option><option value="MEDICAL">Medical</option>
                <option value="SURGICAL">Surgical</option><option value="ICU">ICU</option>
                <option value="MATERNITY">Maternity</option><option value="PEDIATRIC">Pediatric</option>
                <option value="EMERGENCY">Emergency</option>
              </select>
              <input type="number" min="1" max="50" value={totalBeds} onChange={(e) => setTotalBeds(Number(e.target.value))}
                className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            </div>
            <button onClick={() => createWard.mutate({ name: wardName, wardType, totalBeds, facilityId: facility?.id })}
              disabled={!wardName || createWard.isPending}
              className="mt-3 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700 disabled:opacity-50">
              {createWard.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : "Create Ward & Beds"}
            </button>
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : wards.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Bed className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No wards configured. {facility ? "Add a ward to get started." : "Select a facility first."}</p>
          </div>
        ) : (
          <div className="space-y-3">
            {wards.map((ward) => {
              const a = ward.attributes;
              return (
                <div key={ward.id} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <Building2 className="w-5 h-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium text-gray-900">{a.name as string}</p>
                      <p className="text-xs text-gray-500">{a.wardType as string} · Floor: {(a.floor as string) || "—"}</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-4 text-xs">
                    <span className="text-green-600 font-medium">{a.availableBeds as number} available</span>
                    <span className="text-blue-600 font-medium">{a.occupiedBeds as number} occupied</span>
                    <span className="text-gray-500">{a.totalBeds as number} total</span>
                    <Link href="/beds" className="text-blue-600 hover:text-blue-800 font-medium">Manage →</Link>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
