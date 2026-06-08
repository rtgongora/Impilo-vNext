"use client";

/**
 * Lab Reconciliation — absorbs oros-web sidecar
 * Reconcile lab orders with external LIS systems.
 * Route: /lab/reconciliation | Zone: lab | Guard: shift
 */

import { useMemo, useState } from "react";
import {
  RefreshCcw,
  Search,
  Filter,
  AlertTriangle,
  CheckCircle2,
  Loader2,
  Link2,
  Check,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  useLabReconciliation,
  useMatchLabReconciliation,
  useResolveLabReconciliation,
  type LabReconciliationItem,
} from "@/hooks/queries/useLabReconciliation";

function itemId(item: LabReconciliationItem): string {
  return String(item.recId ?? item.id ?? "");
}

function matchesSearch(item: LabReconciliationItem, query: string): boolean {
  if (!query.trim()) return true;
  const needle = query.trim().toLowerCase();
  const haystack = [
    item.recId,
    item.orderId,
    item.externalKey,
    item.externalSystem,
    item.patientCpid,
    item.status,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return haystack.includes(needle);
}

export default function LabReconciliationPage() {
  const [search, setSearch] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [statusFilter, setStatusFilter] = useState("");
  const [matchingId, setMatchingId] = useState<string | null>(null);
  const [matchOrderId, setMatchOrderId] = useState("");
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [resolveNotes, setResolveNotes] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);

  const reconcileQ = useLabReconciliation({ size: 50 });
  const matchMutation = useMatchLabReconciliation();
  const resolveMutation = useResolveLabReconciliation();

  const summary = reconcileQ.data?.data?.summary;
  const items = useMemo(() => {
    const raw = reconcileQ.data?.data?.items ?? [];
    return raw.filter((item) => {
      if (statusFilter && String(item.status ?? "").toUpperCase() !== statusFilter) {
        return false;
      }
      return matchesSearch(item, search);
    });
  }, [reconcileQ.data?.data?.items, search, statusFilter]);

  const summaryCards = [
    {
      label: "Matched",
      value: String(summary?.matched ?? 0),
      Icon: CheckCircle2,
      color: "bg-green-50 text-green-600",
    },
    {
      label: "Pending",
      value: String(summary?.pending ?? 0),
      Icon: AlertTriangle,
      color: "bg-amber-50 text-amber-600",
    },
    {
      label: "Resolved",
      value: String(summary?.resolved ?? 0),
      Icon: CheckCircle2,
      color: "bg-impilo-50 text-impilo-500",
    },
  ];

  async function handleMatch(recId: string) {
    if (!matchOrderId.trim()) {
      setActionError("An order ID is required to match.");
      return;
    }
    setActionError(null);
    try {
      await matchMutation.mutateAsync({ recId, orderId: matchOrderId.trim() });
      setMatchingId(null);
      setMatchOrderId("");
    } catch {
      setActionError("Failed to match reconciliation entry. Verify OROS is reachable.");
    }
  }

  async function handleResolve(recId: string) {
    setActionError(null);
    try {
      await resolveMutation.mutateAsync({ recId, notes: resolveNotes.trim() || undefined });
      setResolvingId(null);
      setResolveNotes("");
    } catch {
      setActionError("Failed to resolve reconciliation entry. Verify OROS is reachable.");
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Lab Reconciliation"
        subtitle="Reconcile lab orders and results with external Laboratory Information Systems"
        icon={<RefreshCcw className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <FeatureMaturityBadge
            status="live"
            detail="OROS reconcile queue via /internal/v1/lab-reconciliation"
          />

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {summaryCards.map(({ label, value, Icon, color }) => (
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
                placeholder="Search by order ID or accession number..."
                className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
              />
            </div>
            <button
              type="button"
              onClick={() => setShowFilters((v) => !v)}
              className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
            >
              <Filter className="h-4 w-4" />
              Filters
            </button>
            <button
              type="button"
              onClick={() => void reconcileQ.refetch()}
              className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 transition-colors"
            >
              <RefreshCcw className="h-4 w-4" />
              Refresh
            </button>
          </div>

          {showFilters ? (
            <div className="rounded-lg border border-gray-200 bg-white p-4">
              <label className="text-sm text-gray-600">
                Status
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="mt-1 block rounded-lg border border-gray-300 px-3 py-2 text-sm"
                >
                  <option value="">All statuses</option>
                  <option value="PENDING">Pending</option>
                  <option value="MATCHED">Matched</option>
                  <option value="RESOLVED">Resolved</option>
                </select>
              </label>
            </div>
          ) : null}

          {actionError ? (
            <p className="text-sm text-red-600" role="alert">
              {actionError}
            </p>
          ) : null}

          {reconcileQ.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-gray-500">
              <Loader2 className="h-5 w-5 animate-spin" />
              Loading reconciliation queue…
            </div>
          ) : reconcileQ.isError ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-800">
              Unable to load reconciliation data. Confirm OROS is running and the BFF proxy is configured.
            </div>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
              <RefreshCcw className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-4 text-sm font-semibold text-gray-900">No reconciliation data</h3>
              <p className="mt-2 text-sm text-gray-600">
                Hybrid-mode orders appear here when external LIS results need matching.
              </p>
            </div>
          ) : (
            <div className="rounded-lg border border-gray-200 bg-white overflow-hidden">
              <div className="border-b border-gray-200 px-4 py-3 grid grid-cols-7 gap-3 text-xs font-semibold text-gray-500 uppercase">
                <span>Rec ID</span>
                <span>External key</span>
                <span>System</span>
                <span>Patient CPID</span>
                <span>Order</span>
                <span>Status</span>
                <span>Actions</span>
              </div>
              <ul className="divide-y divide-gray-100">
                {items.map((item) => {
                  const id = itemId(item);
                  const canAct = String(item.status ?? "").toUpperCase() === "PENDING";
                  return (
                    <li key={id} className="px-4 py-3 grid grid-cols-7 gap-3 text-sm items-center">
                      <span className="font-medium text-gray-900 truncate">{id || "—"}</span>
                      <span className="text-gray-700 truncate">{item.externalKey ?? "—"}</span>
                      <span className="text-gray-600">{item.externalSystem ?? "—"}</span>
                      <span className="text-gray-700">{item.patientCpid ?? "—"}</span>
                      <span className="text-gray-600">{item.orderId ?? "—"}</span>
                      <span className="text-gray-600">{item.status ?? "—"}</span>
                      <div className="flex flex-wrap gap-2">
                        {canAct ? (
                          <>
                            <button
                              type="button"
                              onClick={() => {
                                setMatchingId(id);
                                setMatchOrderId(item.orderId ?? "");
                                setActionError(null);
                              }}
                              className="inline-flex items-center gap-1 rounded-md bg-violet-600 px-2 py-1 text-xs font-medium text-white hover:bg-violet-700"
                            >
                              <Link2 className="h-3 w-3" />
                              Match
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setResolvingId(id);
                                setResolveNotes("");
                                setActionError(null);
                              }}
                              className="inline-flex items-center gap-1 rounded-md border border-gray-300 px-2 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50"
                            >
                              <Check className="h-3 w-3" />
                              Resolve
                            </button>
                          </>
                        ) : (
                          <span className="text-xs text-gray-400">No actions</span>
                        )}
                      </div>
                      {matchingId === id ? (
                        <div className="col-span-7 mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
                          <input
                            type="text"
                            value={matchOrderId}
                            onChange={(e) => setMatchOrderId(e.target.value)}
                            placeholder="Order ID to match"
                            className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
                          />
                          <button
                            type="button"
                            disabled={matchMutation.isPending}
                            onClick={() => void handleMatch(id)}
                            className="rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                          >
                            Confirm match
                          </button>
                          <button
                            type="button"
                            onClick={() => setMatchingId(null)}
                            className="rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : null}
                      {resolvingId === id ? (
                        <div className="col-span-7 mt-2 flex flex-col gap-2 sm:flex-row sm:items-center">
                          <input
                            type="text"
                            value={resolveNotes}
                            onChange={(e) => setResolveNotes(e.target.value)}
                            placeholder="Resolution notes (optional)"
                            className="flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm"
                          />
                          <button
                            type="button"
                            disabled={resolveMutation.isPending}
                            onClick={() => void handleResolve(id)}
                            className="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                          >
                            Confirm resolve
                          </button>
                          <button
                            type="button"
                            onClick={() => setResolvingId(null)}
                            className="rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50"
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
