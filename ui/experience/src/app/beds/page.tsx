"use client";

/**
 * Bed Management — Ward grid with bed status, occupancy, and actions.
 * Route: /beds | pageTitle: "Bed Management"
 *
 * Lovable reference: BedManagement component with grid/list views,
 * ward filtering, bed status transitions, and patient assignment.
 */

import { useState } from "react";
import Link from "next/link";
import { Bed, Loader2, AlertCircle, CheckCircle, Clock, Wrench, Sparkles, User } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface WardResource {
  id: string;
  type: "ward";
  attributes: {
    name: string;
    wardType: string;
    floor: string;
    totalBeds: number;
    availableBeds: number;
    occupiedBeds: number;
    status: string;
  };
}

interface BedResource {
  id: string;
  type: "bed";
  attributes: {
    bedNumber: string;
    bedType: string;
    status: string;
    wardId: string;
    wardName: string;
    acuity: string | null;
    patientId: string | null;
    patientName: string | null;
    admittedAt: string | null;
    assignedDoctor: string | null;
  };
}

const BED_STATUS_STYLES: Record<string, { bg: string; icon: typeof CheckCircle }> = {
  AVAILABLE: { bg: "bg-green-100 border-green-300 text-green-700", icon: CheckCircle },
  OCCUPIED: { bg: "bg-blue-100 border-blue-300 text-blue-700", icon: User },
  RESERVED: { bg: "bg-amber-100 border-amber-300 text-amber-700", icon: Clock },
  MAINTENANCE: { bg: "bg-gray-100 border-gray-300 text-gray-600", icon: Wrench },
  CLEANING: { bg: "bg-purple-100 border-purple-300 text-purple-700", icon: Sparkles },
};

export default function BedManagementPage() {
  const facility = useFacilityStore((s) => s.facility);
  const queryClient = useQueryClient();
  const [selectedWard, setSelectedWard] = useState<string | null>(null);

  const { data: wardsData, isLoading: wardsLoading } = useQuery<ApiResponse<WardResource[]>>({
    queryKey: ["wards", facility?.id],
    queryFn: () => apiClient.get(`/internal/v1/beds/wards?facility_id=${facility?.id}`),
    enabled: !!facility?.id,
  });

  const { data: bedsData, isLoading: bedsLoading } = useQuery<ApiResponse<BedResource[]>>({
    queryKey: ["beds", facility?.id, selectedWard],
    queryFn: () => {
      let url = `/internal/v1/beds?facility_id=${facility?.id}`;
      if (selectedWard) url += `&ward_id=${selectedWard}`;
      return apiClient.get(url);
    },
    enabled: !!facility?.id,
  });

  const updateBedStatus = useMutation({
    mutationFn: ({ bedId, status }: { bedId: string; status: string }) =>
      apiClient.post(`/internal/v1/beds/${bedId}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["beds"] });
      queryClient.invalidateQueries({ queryKey: ["wards"] });
    },
  });

  const wards = wardsData?.data ?? [];
  const beds = bedsData?.data ?? [];
  const isLoading = wardsLoading || bedsLoading;

  // Census summary
  const totalBeds = beds.length;
  const available = beds.filter((b) => b.attributes.status === "AVAILABLE").length;
  const occupied = beds.filter((b) => b.attributes.status === "OCCUPIED").length;

  return (
    <AppLayout>
      <PageShell title="Bed Management" subtitle={facility ? `${facility.name} — Ward & Bed Census` : "Select a facility first"}>
        {!facility ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Bed className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">Select a facility to view bed management</p>
            <Link href="/home" className="mt-2 inline-block text-sm text-blue-600 hover:text-blue-800">
              Go to Home →
            </Link>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading bed data...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Census Summary */}
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-white rounded-lg border border-gray-200 p-4 text-center">
                <p className="text-2xl font-bold text-gray-900">{totalBeds}</p>
                <p className="text-xs text-gray-500">Total Beds</p>
              </div>
              <div className="bg-green-50 rounded-lg border border-green-200 p-4 text-center">
                <p className="text-2xl font-bold text-green-700">{available}</p>
                <p className="text-xs text-green-600">Available</p>
              </div>
              <div className="bg-blue-50 rounded-lg border border-blue-200 p-4 text-center">
                <p className="text-2xl font-bold text-blue-700">{occupied}</p>
                <p className="text-xs text-blue-600">Occupied</p>
              </div>
            </div>

            {/* Ward Filter */}
            <div className="flex flex-wrap gap-2">
              <button onClick={() => setSelectedWard(null)}
                className={`px-3 py-1.5 text-xs font-medium rounded-full transition-colors ${!selectedWard ? "bg-blue-600 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>
                All Wards
              </button>
              {wards.map((ward) => (
                <button key={ward.id} onClick={() => setSelectedWard(ward.id)}
                  className={`px-3 py-1.5 text-xs font-medium rounded-full transition-colors ${selectedWard === ward.id ? "bg-blue-600 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>
                  {ward.attributes.name} ({ward.attributes.availableBeds}/{ward.attributes.totalBeds})
                </button>
              ))}
            </div>

            {/* Bed Grid */}
            {beds.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Bed className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No beds configured for this facility</p>
              </div>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
                {beds.map((bed) => {
                  const a = bed.attributes;
                  const style = BED_STATUS_STYLES[a.status] ?? BED_STATUS_STYLES.AVAILABLE;
                  const StatusIcon = style.icon;
                  return (
                    <div key={bed.id} className={`rounded-lg border-2 p-3 ${style.bg} transition-all hover:shadow-md`}>
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-sm font-bold">{a.bedNumber}</span>
                        <StatusIcon className="w-4 h-4" />
                      </div>
                      <p className="text-[10px] uppercase tracking-wide opacity-75">{a.status}</p>
                      {a.status === "OCCUPIED" && a.patientName && (
                        <div className="mt-2 pt-2 border-t border-current/20">
                          <p className="text-xs font-medium truncate">{a.patientName}</p>
                          {a.acuity && (
                            <span className={`text-[10px] px-1.5 py-0.5 rounded mt-1 inline-block ${
                              a.acuity === "CRITICAL" ? "bg-red-200 text-red-800"
                              : a.acuity === "HIGH" ? "bg-orange-200 text-orange-800"
                              : "bg-gray-200 text-gray-700"
                            }`}>{a.acuity}</span>
                          )}
                        </div>
                      )}
                      {/* Quick actions */}
                      <div className="mt-2 flex gap-1">
                        {a.status === "CLEANING" && (
                          <button onClick={() => updateBedStatus.mutate({ bedId: bed.id, status: "AVAILABLE" })}
                            className="text-[10px] px-1.5 py-0.5 bg-green-600 text-white rounded hover:bg-green-700">
                            Mark Available
                          </button>
                        )}
                        {a.status === "AVAILABLE" && (
                          <button onClick={() => updateBedStatus.mutate({ bedId: bed.id, status: "RESERVED" })}
                            className="text-[10px] px-1.5 py-0.5 bg-amber-600 text-white rounded hover:bg-amber-700">
                            Reserve
                          </button>
                        )}
                        {a.status === "OCCUPIED" && (
                          <button onClick={() => updateBedStatus.mutate({ bedId: bed.id, status: "CLEANING" })}
                            className="text-[10px] px-1.5 py-0.5 bg-purple-600 text-white rounded hover:bg-purple-700">
                            Discharge
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
