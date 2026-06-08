"use client";

import { useMemo, useState } from "react";
import {
  ListChecks,
  Search,
  Filter,
  Clock,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Check,
  X,
  ScanLine,
} from "lucide-react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useAcceptLabWorklistItem,
  useLabWorklist,
  useRejectLabWorklistItem,
  type LabWorklistItem,
} from "@/hooks/queries/useLabWorklist";

const STATUS_FILTERS = [
  { value: "", label: "All statuses" },
  { value: "PLACED", label: "Pending acquisition" },
  { value: "ACCEPTED", label: "Accepted" },
  { value: "IN_PROGRESS", label: "In progress" },
  { value: "RESULT_AVAILABLE", label: "Report available" },
] as const;

function itemId(item: LabWorklistItem): string {
  return String(item.orderId ?? item.id ?? "");
}

function matchesSearch(item: LabWorklistItem, query: string): boolean {
  if (!query.trim()) return true;
  const needle = query.trim().toLowerCase();
  const haystack = [item.orderId, item.patientCpid, item.ziboOrderCode, item.clinicalNotes, item.status, item.priority]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(needle);
}

export default function ImagingWorklistPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const worklistQ = useLabWorklist({
    facilityId: facility?.id,
    type: "IMAGING",
    status: statusFilter || undefined,
    size: 50,
  });

  const acceptMutation = useAcceptLabWorklistItem();
  const rejectMutation = useRejectLabWorklistItem();

  const summary = worklistQ.data?.data?.summary;
  const items = useMemo(() => {
    const raw = worklistQ.data?.data?.items ?? [];
    return raw.filter((item) => matchesSearch(item, search));
  }, [worklistQ.data?.data?.items, search]);

  async function handleAccept(orderId: string) {
    setActionError(null);
    try {
      await acceptMutation.mutateAsync({ orderId });
    } catch {
      setActionError("Failed to accept imaging order.");
    }
  }

  async function handleReject(orderId: string) {
    if (!rejectReason.trim()) {
      setActionError("A rejection reason is required.");
      return;
    }
    setActionError(null);
    try {
      await rejectMutation.mutateAsync({ orderId, reason: rejectReason.trim() });
      setRejectingId(null);
      setRejectReason("");
    } catch {
      setActionError("Failed to reject imaging order.");
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Imaging Worklist"
        subtitle="Pending and in-progress imaging orders for this facility"
        icon={<ScanLine className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <FeatureMaturityBadge status="live" detail="OROS facility worklist via /internal/v1/lab-worklists?type=IMAGING" />
          <Link href="/imaging/facility" className="text-sm font-medium text-impilo-600 hover:underline">
            Facility imaging dashboard →
          </Link>
        </div>

        {!facility?.id ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-900">
            Select a facility to load the imaging worklist.
          </div>
        ) : null}

        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "Pending", value: String(summary?.pending_collection ?? 0), Icon: Clock, color: "bg-amber-50 text-amber-600" },
              { label: "In progress", value: String(summary?.in_progress ?? 0), Icon: ListChecks, color: "bg-impilo-50 text-impilo-500" },
              { label: "Completed", value: String(summary?.completed ?? 0), Icon: CheckCircle2, color: "bg-green-50 text-green-600" },
              { label: "Urgent", value: String(summary?.urgent ?? 0), Icon: AlertCircle, color: "bg-red-50 text-red-600" },
            ].map(({ label, value, Icon, color }) => (
              <div key={label} className="rounded-lg border border-gray-200 bg-white p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-500">{label}</span>
                  <div className={`rounded-lg p-1.5 ${color.split(" ")[0]}`}>
                    <Icon className={`h-4 w-4 ${color.split(" ")[1]}`} />
                  </div>
                </div>
                <p className="text-2xl font-bold text-gray-900">{value}</p>
              </div>
            ))}
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by patient CPID, order ID, or notes…"
                className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-4 text-sm"
              />
            </div>
            <button
              type="button"
              onClick={() => setShowFilters((v) => !v)}
              className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
            >
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {showFilters ? (
            <label className="text-sm text-gray-600">
              Status
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="mt-1 block rounded-lg border border-gray-300 px-3 py-2 text-sm"
              >
                {STATUS_FILTERS.map((opt) => (
                  <option key={opt.value || "all"} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </label>
          ) : null}

          {actionError ? <p className="text-sm text-red-600">{actionError}</p> : null}

          {worklistQ.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-gray-500">
              <Loader2 className="h-5 w-5 animate-spin" />
              Loading imaging worklist…
            </div>
          ) : worklistQ.isError ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-800">
              Unable to load imaging worklist. Confirm OROS and BFF proxy are configured.
            </div>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
              <ScanLine className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-4 text-sm font-semibold text-gray-900">Imaging worklist empty</h3>
            </div>
          ) : (
            <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
              {items.map((item) => {
                const id = itemId(item);
                const canAct = ["PLACED", "SCHEDULED"].includes(String(item.status ?? "").toUpperCase());
                return (
                  <li key={id} className="px-4 py-3 text-sm">
                    <div className="grid grid-cols-6 gap-3 items-center">
                      <span className="font-medium">{id}</span>
                      <span>{item.patientCpid ?? "—"}</span>
                      <span>{item.priority ?? "—"}</span>
                      <span>{item.status ?? "—"}</span>
                      <span className="text-xs text-gray-500 truncate">{item.clinicalNotes ?? "—"}</span>
                      <div className="flex gap-2">
                        {canAct ? (
                          <>
                            <button type="button" onClick={() => void handleAccept(id)} className="rounded-md bg-emerald-600 px-2 py-1 text-xs text-white">
                              <Check className="inline h-3 w-3" /> Accept
                            </button>
                            <button type="button" onClick={() => setRejectingId(id)} className="rounded-md border border-red-200 px-2 py-1 text-xs text-red-700">
                              <X className="inline h-3 w-3" /> Reject
                            </button>
                          </>
                        ) : (
                          <span className="text-xs text-gray-400">No actions</span>
                        )}
                      </div>
                    </div>
                    {rejectingId === id ? (
                      <div className="mt-2 flex gap-2">
                        <input
                          value={rejectReason}
                          onChange={(e) => setRejectReason(e.target.value)}
                          placeholder="Rejection reason"
                          className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
                        />
                        <button type="button" onClick={() => void handleReject(id)} className="rounded-lg bg-red-600 px-3 py-2 text-sm text-white">
                          Confirm
                        </button>
                      </div>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
