"use client";

/**
 * Results Review — absorbs oros-web sidecar
 * Review and authorize lab results.
 * Route: /lab/results | Zone: lab | Guard: facility
 */

import { useMemo, useState } from "react";
import { FileSearch, Search, Filter, CheckSquare, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useAcknowledgeLabOrder } from "@/hooks/queries/useLabOrders";
import { useLabWorklist, type LabWorklistItem } from "@/hooks/queries/useLabWorklist";

function itemId(item: LabWorklistItem): string {
  return String(item.orderId ?? item.id ?? "");
}

export default function LabResultsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [actionError, setActionError] = useState<string | null>(null);

  const resultsQ = useLabWorklist({
    facilityId: facility?.id,
    type: "LAB",
    status: "RESULT_AVAILABLE",
    size: 50,
  });

  const acknowledgeMutation = useAcknowledgeLabOrder();

  const items = useMemo(() => {
    const raw = resultsQ.data?.data?.items ?? [];
    if (!search.trim()) return raw;
    const needle = search.trim().toLowerCase();
    return raw.filter((item) =>
      [item.orderId, item.patientCpid, item.clinicalNotes, item.ziboOrderCode]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(needle),
    );
  }, [resultsQ.data?.data?.items, search]);

  function toggleSelected(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function authorizeSelected() {
    if (selected.size === 0) return;
    setActionError(null);
    try {
      for (const id of selected) {
        await acknowledgeMutation.mutateAsync({ id, notes: "Authorized from lab results review" });
      }
      setSelected(new Set());
    } catch {
      setActionError("Failed to authorize one or more results. Confirm OROS acknowledgement is available.");
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Results Review"
        subtitle="Review, validate, and authorize laboratory results"
        icon={<FileSearch className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap gap-2">
          <FeatureMaturityBadge
            status="partial"
            detail="RESULT_AVAILABLE worklist + /internal/v1/lab-orders/{id}/acknowledge"
          />
        </div>

        {!facility?.id ? (
          <div className="mb-4 rounded-lg border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
            Select a facility to review resulted orders for this site.
          </div>
        ) : null}

        <div className="space-y-6">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div className="flex gap-3">
              <div className="relative w-80">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search results by patient or order..."
                  className="w-full rounded-lg border border-border py-2 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
                />
              </div>
              <button
                type="button"
                className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background transition-colors"
              >
                <Filter className="h-4 w-4" />
                Filters
              </button>
            </div>
            <button
              type="button"
              disabled={selected.size === 0 || acknowledgeMutation.isPending || !facility?.id}
              onClick={() => void authorizeSelected()}
              className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 transition-colors disabled:opacity-50"
            >
              {acknowledgeMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <CheckSquare className="h-4 w-4" />
              )}
              Authorize Selected ({selected.size})
            </button>
          </div>

          {actionError ? (
            <p className="text-sm text-red-600" role="alert">
              {actionError}
            </p>
          ) : null}

          <div className="rounded-lg border border-border bg-card">
            <div className="border-b border-border px-4 py-3 grid grid-cols-6 gap-4 text-xs font-semibold text-muted-foreground uppercase">
              <span>Select</span>
              <span>Order ID</span>
              <span>Patient CPID</span>
              <span>Priority</span>
              <span>Updated</span>
              <span>Status</span>
            </div>
            {resultsQ.isLoading ? (
              <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" />
                Loading results pending review…
              </div>
            ) : resultsQ.isError ? (
              <div className="p-8 text-center text-sm text-danger">
                Unable to load resulted orders from OROS.
              </div>
            ) : items.length === 0 ? (
              <div className="p-12 text-center">
                <p className="text-sm text-muted-foreground">No results pending review</p>
              </div>
            ) : (
              <ul className="divide-y divide-gray-100">
                {items.map((item) => {
                  const id = itemId(item);
                  return (
                    <li key={id} className="px-4 py-3 grid grid-cols-6 gap-4 text-sm items-center">
                      <input
                        type="checkbox"
                        checked={selected.has(id)}
                        onChange={() => toggleSelected(id)}
                        aria-label={`Select order ${id}`}
                      />
                      <span className="font-medium text-foreground">{id}</span>
                      <span>{item.patientCpid ?? "—"}</span>
                      <span>{item.priority ?? "—"}</span>
                      <span className="text-xs text-muted-foreground">{item.updatedAt ?? item.placedAt ?? "—"}</span>
                      <span>{item.status ?? "—"}</span>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
