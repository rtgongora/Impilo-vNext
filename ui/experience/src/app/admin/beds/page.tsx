"use client";

/**
 * Admin Bed/Ward Management — CRUD for wards and beds.
 * Route: /admin/beds
 */

import { useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Bed, Plus, Loader2, Building2, ClipboardList, User } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";
import { facilityOpsKeys } from "@/hooks/queries/useFacilityOperations";

export default function AdminBedsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [wardName, setWardName] = useState("");
  const [wardType, setWardType] = useState("GENERAL");
  const [totalBeds, setTotalBeds] = useState(6);
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

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
      if (facility?.id) {
        queryClient.invalidateQueries({ queryKey: facilityOpsKeys.all(facility.id) });
      }
      setShowForm(false);
      setWardName("");
    },
  });

  const wards = wardsData?.data ?? [];
  const totalConfiguredBeds = wards.reduce((sum, ward) => sum + Number(ward.attributes.totalBeds ?? 0), 0);
  const activeWards = wards.filter((ward) => Number(ward.attributes.totalBeds ?? 0) > 0).length;
  const handoffParams = new URLSearchParams();

  if (patientId) handoffParams.set("patientId", patientId);
  if (encounterId) handoffParams.set("encounterId", encounterId);
  if (source) handoffParams.set("source", source);

  const withHandoff = (href: string) => {
    const query = handoffParams.toString();
    return query ? `${href}?${query}` : href;
  };

  const actions = [
    encounterId && patientId
      ? { href: `/ehr/${patientId}/encounter/${encounterId}`, label: "Encounter", icon: ClipboardList }
      : null,
    patientId
      ? { href: `/ehr/${patientId}`, label: "Chart", icon: User, tone: "secondary" as const }
      : null,
    { href: withHandoff("/beds"), label: "Bed Operations", icon: Bed, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell title="Bed & Ward Administration" subtitle="Manage wards and bed inventory">
        <div className="mb-4 flex items-center justify-between">
          <Link href="/admin" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="w-4 h-4" /> Back to Admin
          </Link>
          <button onClick={() => setShowForm(!showForm)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600">
            <Plus className="w-4 h-4" /> Add Ward
          </button>
        </div>

        <div className="space-y-6">
          <WorkflowHeader
            badge="Capacity configuration"
            badgeIcon={Building2}
            title="Keep bed administration tied to the current facility and downstream admission handoff instead of isolating capacity setup from live bed operations."
            description="Admin beds now surfaces facility scope, any incoming closure source, and the next operational move before staff configure wards."
            context={[
              { label: "Facility", value: facility?.name ?? "No facility selected" },
              { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Admin workspace" },
              { label: "Ward catalog", value: `${wards.length} ward${wards.length === 1 ? "" : "s"}` },
            ]}
            actions={actions}
            metrics={[
              {
                label: "Configured wards",
                value: String(activeWards),
                detail: "Ward records currently supporting downstream bed assignment operations.",
              },
              {
                label: "Configured beds",
                value: String(totalConfiguredBeds),
                detail: "Total capacity represented by the current ward setup.",
              },
              {
                label: "Next action",
                value: showForm ? "Create ward" : "Review capacity",
                detail:
                  encounterId && patientId
                    ? "Use this admin view without losing the linked encounter or chart context, then return to bed operations when the capacity setup is confirmed."
                    : "Review or update facility capacity here, then move back to live bed operations for the next handoff step.",
              },
            ]}
          />

          <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
              Capacity loop status
            </p>
            <p className="mt-2 text-sm text-slate-800">
              {source === "discharge"
                ? "This admin capacity view was reached from encounter outcome, so the loop here is confirming that ward configuration supports the linked admission or transfer handoff."
                : "This workspace manages bed configuration, but it should still connect cleanly back to live bed operations and the source encounter when context exists."}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Update capacity in place here, then move back to bed operations, the encounter, or the chart when the next action depends on live assignment rather than setup.
            </p>
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
              className="mt-3 px-4 py-2 bg-impilo-500 text-white text-sm rounded-lg hover:bg-impilo-600 disabled:opacity-50">
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
                    <span className="text-impilo-500 font-medium">{a.occupiedBeds as number} occupied</span>
                    <span className="text-gray-500">{a.totalBeds as number} total</span>
                    <Link href="/beds" className="text-impilo-500 hover:text-impilo-700 font-medium">Manage →</Link>
                  </div>
                </div>
              );
            })}
          </div>
        )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
