"use client";

/**
 * Support Tickets — absorbs support-console sidecar
 * Create, track, and resolve support requests.
 * Route: /support/tickets | Zone: support | Guard: auth
 */

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Ticket, Plus, Search, Filter, Clock, CheckCircle2, Loader2 } from "lucide-react";
import { HelpdeskLearningSuggestions } from "@/components/learning/HelpdeskLearningSuggestions";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { HelpdeskIntelligenceAssist } from "@/components/support/HelpdeskIntelligenceAssist";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { apiClient } from "@/lib/api-client";

function readTicketStatus(t: unknown): string {
  if (typeof t !== "object" || !t) return "";
  const rec = t as Record<string, unknown>;
  if (typeof rec.status === "string") return rec.status;
  const attrs = rec.attributes as Record<string, unknown> | undefined;
  if (attrs && typeof attrs.status === "string") return attrs.status;
  return "";
}

function normalizeStatus(s: string) {
  return s.trim().toUpperCase();
}

export default function TicketsPage() {
  const { isClinical, isDispenser, isCitizen } = useRoleGroup();
  const facility = useFacilityStore((s) => s.facility);

  const useProviderList = Boolean(facility?.id && (isClinical || isDispenser));
  const useCitizenList = Boolean(isCitizen && !useProviderList);

  const providerQuery = useQuery({
    queryKey: ["support-tickets", "provider", facility?.id],
    queryFn: () =>
      apiClient.get<{ data: Array<Record<string, unknown>> }>(
        `/internal/v1/mobile/provider/support/tickets?facility_id=${facility?.id}`
      ),
    enabled: useProviderList,
    staleTime: 30_000,
  });

  const citizenQuery = useQuery({
    queryKey: ["support-tickets", "citizen"],
    queryFn: () =>
      apiClient.get<{ data: Array<Record<string, unknown>> }>(`/internal/v1/mobile/citizen/support/tickets?size=200`),
    enabled: useCitizenList,
    staleTime: 30_000,
  });

  const tickets: unknown[] = useMemo(
    () => (useProviderList
      ? (providerQuery.data?.data ?? [])
      : useCitizenList
        ? (citizenQuery.data?.data ?? [])
        : []),
    [citizenQuery.data?.data, providerQuery.data?.data, useCitizenList, useProviderList],
  );

  const loading = useProviderList ? providerQuery.isPending : useCitizenList ? citizenQuery.isPending : false;
  const errored = useProviderList ? providerQuery.isError : useCitizenList ? citizenQuery.isError : false;

  const counts = useMemo(() => {
    let open = 0;
    let inProgress = 0;
    let resolved = 0;
    for (const row of tickets) {
      const s = normalizeStatus(readTicketStatus(row));
      if (!s) continue;
      if (["OPEN", "NEW", "SUBMITTED", "CREATED"].includes(s)) open += 1;
      else if (["IN_PROGRESS", "ASSIGNED", "TRIAGED", "PENDING", "WAITING"].includes(s)) inProgress += 1;
      else if (["RESOLVED", "CLOSED", "COMPLETED", "DONE", "CANCELLED"].includes(s)) resolved += 1;
    }
    return { open, inProgress, resolved, total: tickets.length };
  }, [tickets]);

  const summaryCards = useProviderList || useCitizenList;

  return (
    <AppLayout>
      <PageShell
        title="Support Tickets"
        subtitle="Create, track, and resolve support requests"
        icon={<Ticket className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <HelpdeskIntelligenceAssist />
          <HelpdeskLearningSuggestions issueType="GENERAL" title="Suggested training before you open a ticket" />

          {/* Status summary — real counts when a ticket list API is available for this session */}
          {summaryCards && (
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              {loading && (
                <div className="col-span-full flex items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white py-8 text-sm text-gray-500">
                  <Loader2 className="h-5 w-5 animate-spin text-impilo-500" />
                  Loading ticket summary…
                </div>
              )}
              {!loading && errored && (
                <div className="col-span-full rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                  Ticket counts could not be loaded. You can still create a ticket below when the action is enabled in your
                  environment.
                </div>
              )}
              {!loading && !errored &&
                [
                  { label: "Open", value: String(counts.open), Icon: Clock, color: "bg-impilo-50 text-impilo-500" },
                  { label: "In Progress", value: String(counts.inProgress), Icon: Clock, color: "bg-amber-50 text-amber-600" },
                  { label: "Resolved", value: String(counts.resolved), Icon: CheckCircle2, color: "bg-green-50 text-green-600" },
                  { label: "Total", value: String(counts.total), Icon: Ticket, color: "bg-gray-50 text-gray-600" },
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
          )}
          {!summaryCards && (
            <div className="rounded-lg border border-gray-200 bg-gray-50 px-4 py-3 text-sm text-gray-600">
              Ticket statistics load for citizen accounts (personal support) or when you select a facility as clinical or
              dispenser staff (facility-linked tickets). Administrative sessions without that context do not call the
              mobile ticket APIs from this page.
            </div>
          )}

          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="flex gap-3">
              <div className="relative w-72">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search tickets..."
                  className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
                />
              </div>
              <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <Filter className="h-4 w-4" />
                Filters
              </button>
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 transition-colors">
              <Plus className="h-4 w-4" />
              New Ticket
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <Ticket className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No support tickets</h3>
            <p className="mt-2 text-sm text-gray-600">
              Create a support ticket for system issues, access requests, or general assistance.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
