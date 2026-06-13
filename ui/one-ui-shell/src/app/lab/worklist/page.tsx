"use client";

/**
 * Lab Worklist — absorbs oros-web sidecar
 * Pending and in-progress lab orders for this facility.
 * Route: /lab/worklist | Zone: lab | Guard: facility
 */

import { useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
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
} from "lucide-react";
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
  { value: "PLACED", label: "Pending collection" },
  { value: "ACCEPTED", label: "Accepted" },
  { value: "IN_PROGRESS", label: "In progress" },
  { value: "RESULT_AVAILABLE", label: "Result available" },
] as const;

const PRIORITY_FILTERS = [
  { value: "", label: "All priorities" },
  { value: "STAT", label: "STAT" },
  { value: "URGENT", label: "Urgent" },
  { value: "ROUTINE", label: "Routine" },
] as const;

function itemId(item: LabWorklistItem): string {
  return String(item.orderId ?? item.id ?? "");
}

function matchesSearch(item: LabWorklistItem, query: string): boolean {
  if (!query.trim()) return true;
  const needle = query.trim().toLowerCase();
  const haystack = [
    item.orderId,
    item.patientCpid,
    item.ziboOrderCode,
    item.clinicalNotes,
    item.status,
    item.priority,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(needle);
}

export default function LabWorklistPage() {
  const searchParams = useSearchParams();
  const initialType = searchParams.get("type") ?? "LAB";
  const facility = useFacilityStore((s) => s.facility);
  const [orderType, setOrderType] = useState(initialType);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [priorityFilter, setPriorityFilter] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const worklistQ = useLabWorklist({
    facilityId: facility?.id,
    type: orderType,
    status: statusFilter || undefined,
    priority: priorityFilter || undefined,
    size: 50,
  });

  const acceptMutation = useAcceptLabWorklistItem();
  const rejectMutation = useRejectLabWorklistItem();

  const summary = worklistQ.data?.data?.summary;
  const items = useMemo(() => {
    const raw = worklistQ.data?.data?.items ?? [];
    return raw.filter((item) => matchesSearch(item, search));
  }, [worklistQ.data?.data?.items, search]);

  const summaryCards = [
    {
      label: "Pending Collection",
      value: String(summary?.pending_collection ?? 0),
      Icon: Clock,
      color: "bg-warning-soft text-amber-600",
    },
    {
      label: "In Progress",
      value: String(summary?.in_progress ?? 0),
      Icon: ListChecks,
      color: "bg-primary-soft text-primary",
    },
    {
      label: "Completed",
      value: String(summary?.completed ?? 0),
      Icon: CheckCircle2,
      color: "bg-green-50 text-green-600",
    },
    {
      label: "Urgent",
      value: String(summary?.urgent ?? 0),
      Icon: AlertCircle,
      color: "bg-danger-soft text-red-600",
    },
  ];

  async function handleAccept(orderId: string) {
    setActionError(null);
    try {
      await acceptMutation.mutateAsync({ orderId });
    } catch {
      setActionError("Failed to accept order. Verify OROS is reachable and the order is still pending.");
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
      setActionError("Failed to reject order. Verify OROS is reachable and the order is still pending.");
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Lab Worklist"
        subtitle="Pending and in-progress lab orders for this facility"
        icon={<ListChecks className="h-6 w-6" />}
      >
        <div className="mb-4">
          <FeatureMaturityBadge
            status="live"
            detail="OROS facility worklist via /internal/v1/lab-worklists"
          />
        </div>

        {!facility?.id ? (
          <div className="rounded-lg border border-warning/35 bg-warning-soft p-6 text-sm text-warning-foreground">
            Select a facility to load the lab worklist for this shift.
          </div>
        ) : null}

        <div className="space-y-6">
          <div className="flex flex-wrap gap-2">
            {(["LAB", "IMAGING", "PHARMACY", "BLOOD"] as const).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => setOrderType(type)}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  orderType === type
                    ? "bg-violet-600 text-white"
                    : "border border-border text-foreground hover:bg-background"
                }`}
              >
                {type === "LAB"
                  ? "Laboratory"
                  : type === "IMAGING"
                    ? "Imaging"
                    : type === "PHARMACY"
                      ? "Pharmacy"
                      : "Blood"}
              </button>
            ))}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {summaryCards.map(({ label, value, Icon, color }) => (
              <div key={label} className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-muted-foreground">{label}</span>
                  <div className={`rounded-lg p-1.5 ${color.split(" ")[0]}`}>
                    <Icon className={`h-4 w-4 ${color.split(" ")[1]}`} />
                  </div>
                </div>
                <p className="text-2xl font-bold text-foreground">{value}</p>
              </div>
            ))}
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search by patient CPID, order ID, or notes..."
                className="w-full rounded-lg border border-border py-2.5 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
              />
            </div>
            <button
              type="button"
              onClick={() => setShowFilters((v) => !v)}
              className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-foreground hover:bg-background transition-colors"
            >
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {showFilters ? (
            <div className="flex flex-wrap gap-3 rounded-lg border border-border bg-card p-4">
              <label className="text-sm text-muted-foreground">
                Status
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="mt-1 block rounded-lg border border-border px-3 py-2 text-sm"
                >
                  {STATUS_FILTERS.map((opt) => (
                    <option key={opt.value || "all"} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-sm text-muted-foreground">
                Priority
                <select
                  value={priorityFilter}
                  onChange={(e) => setPriorityFilter(e.target.value)}
                  className="mt-1 block rounded-lg border border-border px-3 py-2 text-sm"
                >
                  {PRIORITY_FILTERS.map((opt) => (
                    <option key={opt.value || "all"} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          ) : null}

          {actionError ? (
            <p className="text-sm text-red-600" role="alert">
              {actionError}
            </p>
          ) : null}

          {worklistQ.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" />
              Loading lab worklist…
            </div>
          ) : worklistQ.isError ? (
            <div className="rounded-lg border border-danger/28 bg-danger-soft p-6 text-sm text-red-800">
              Unable to load the lab worklist. Confirm OROS is running and the BFF proxy is configured.
            </div>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border bg-background p-12 text-center">
              <ListChecks className="mx-auto h-12 w-12 text-muted-foreground" />
              <h3 className="mt-4 text-sm font-semibold text-foreground">Worklist empty</h3>
              <p className="mt-2 text-sm text-muted-foreground">
                No lab orders match the current filters. New orders appear here as clinicians place them.
              </p>
            </div>
          ) : (
            <div className="rounded-lg border border-border bg-card overflow-hidden">
              <div className="border-b border-border px-4 py-3 grid grid-cols-7 gap-3 text-xs font-semibold text-muted-foreground uppercase">
                <span>Order</span>
                <span>Patient CPID</span>
                <span>Priority</span>
                <span>Status</span>
                <span>Placed</span>
                <span>Notes</span>
                <span>Actions</span>
              </div>
              <ul className="divide-y divide-gray-100">
                {items.map((item) => {
                  const id = itemId(item);
                  const canAct = ["PLACED", "SCHEDULED"].includes(String(item.status ?? "").toUpperCase());
                  return (
                    <li key={id} className="px-4 py-3 grid grid-cols-7 gap-3 text-sm items-center">
                      <span className="font-medium text-foreground">{id || "—"}</span>
                      <span className="text-foreground">{item.patientCpid ?? "—"}</span>
                      <span className="text-foreground">{item.priority ?? "—"}</span>
                      <span className="text-foreground">{item.status ?? "—"}</span>
                      <span className="text-muted-foreground text-xs">{item.placedAt ?? "—"}</span>
                      <span className="text-muted-foreground text-xs truncate">{item.clinicalNotes ?? "—"}</span>
                      <div className="flex flex-wrap gap-2">
                        {canAct ? (
                          <>
                            <button
                              type="button"
                              disabled={acceptMutation.isPending}
                              onClick={() => void handleAccept(id)}
                              className="inline-flex items-center gap-1 rounded-md bg-emerald-600 px-2 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                            >
                              <Check className="h-3 w-3" />
                              Accept
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setRejectingId(id);
                                setRejectReason("");
                                setActionError(null);
                              }}
                              className="inline-flex items-center gap-1 rounded-md border border-danger/28 px-2 py-1 text-xs font-medium text-danger hover:bg-danger-soft"
                            >
                              <X className="h-3 w-3" />
                              Reject
                            </button>
                          </>
                        ) : (
                          <span className="text-xs text-muted-foreground">No actions</span>
                        )}
                      </div>
                      {rejectingId === id ? (
                        <div className="col-span-7 mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
                          <input
                            type="text"
                            value={rejectReason}
                            onChange={(e) => setRejectReason(e.target.value)}
                            placeholder="Rejection reason"
                            className="flex-1 rounded-lg border border-border px-3 py-2 text-sm"
                          />
                          <button
                            type="button"
                            disabled={rejectMutation.isPending}
                            onClick={() => void handleReject(id)}
                            className="rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                          >
                            Confirm reject
                          </button>
                          <button
                            type="button"
                            onClick={() => setRejectingId(null)}
                            className="rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
