"use client";

/**
 * Tariff library — COSTA {@code /api/costa/tariff-lists} via BFF {@code /internal/v1/finance/costa-intel/tariff-lists}.
 * Route: /finance/tariffs | pageTitle: "Tariff library"
 *
 * Legacy line-item schedule: GET /internal/v1/finance/tariffs (TariffEntity) shown as secondary table when present.
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  Loader2,
  BookOpen,
  AlertCircle,
  ClipboardList,
  CreditCard,
  FileText,
  Receipt,
  User,
  AlertTriangle,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import {
  REFERENCE_TARIFF_WARNING,
  ZIMBABWE_POC_DISCLAIMER,
  groupTariffLists,
  sectionCatalog,
  parseTariffMetadata,
  type CostaTariffListRow,
} from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type TariffListEnvelope = { data: CostaTariffListRow[] };

interface TariffLineResource {
  id: string;
  type: "tariff";
  attributes: {
    serviceCode: string;
    description: string;
    tariffAmount: number;
    currency: string;
    effectiveDate: string;
    status: string;
    [key: string]: unknown;
  };
}

type LegacyTariffsResponse = ApiResponse<TariffLineResource[]>;

function useCostaTariffLibraryLists() {
  return useQuery({
    queryKey: ["finance", "costa-intel", "tariff-lists", "full"],
    queryFn: async () => {
      const res = await apiClient.get<TariffListEnvelope>("/internal/v1/finance/costa-intel/tariff-lists");
      return Array.isArray(res.data) ? res.data : [];
    },
  });
}

function useLegacyLineTariffs() {
  return useQuery<LegacyTariffsResponse>({
    queryKey: ["finance-tariffs", "legacy-lines"],
    queryFn: () => apiClient.get<LegacyTariffsResponse>("/internal/v1/finance/tariffs"),
  });
}

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-700",
  INACTIVE: "bg-neutral-100 text-muted-foreground",
  DRAFT: "bg-yellow-100 text-yellow-700",
  SUPERSEDED: "bg-primary-soft text-primary",
};

export default function TariffsPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const listsQ = useCostaTariffLibraryLists();
  const legacyQ = useLegacyLineTariffs();
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const lists = listsQ.data ?? [];
  const operationalCount = lists.filter((l) => !l.referenceOnly && l.approvedForBilling).length;
  const referenceCount = lists.filter((l) => l.referenceOnly).length;
  const grouped = groupTariffLists(lists);
  const sections = sectionCatalog();

  const legacyRows = legacyQ.data?.data ?? [];
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
    { href: withHandoff("/finance/costa"), label: "COSTA tools", icon: BookOpen, tone: "secondary" as const },
    { href: withHandoff("/finance/billing"), label: "Billing", icon: Receipt, tone: "secondary" as const },
    { href: withHandoff("/finance/claims"), label: "Claims", icon: FileText, tone: "secondary" as const },
    { href: withHandoff("/finance/payments"), label: "Payments", icon: CreditCard, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  const error = listsQ.error || legacyQ.error;
  const isLoading = listsQ.isLoading;

  return (
    <AppLayout>
      <PageShell title="Tariff library" subtitle="COSTA tariff lists — operational, reference, and upload pipeline">
        <div className="mb-4">
          <Link
            href={withHandoff("/finance")}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to finance
          </Link>
        </div>

        {error && !listsQ.data ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load tariff library from COSTA (check BFF and COSTA_BASE_URL)</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading tariff library…</span>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Tariff continuity"
              badgeIcon={BookOpen}
              title="Select an operational tariff for billing; reference tariffs are view-only for mapping and comparison."
              description="Lists are loaded from COSTA tariff intel (seed + uploads). MusheX and invoice handoff are blocked for reference-only lists unless a tariff approver forces operational override."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Finance workspace" },
                { label: "Lists visible", value: String(lists.length) },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Operational (billable)",
                  value: String(operationalCount),
                  detail: "Approved for billing — may drive estimates, charge sheets, invoices, and MusheX handoff.",
                },
                {
                  label: "Reference / mapping",
                  value: String(referenceCount),
                  detail: "Not for production billing until imported and approved as operational.",
                },
                {
                  label: "Next action",
                  value: "Review list → COSTA tools",
                  detail: "Run cost estimate probes and upload workflow from Finance → COSTA & MusheX.",
                },
              ]}
            />

            <div className="rounded-lg border border-warning/35 bg-warning-soft/80 p-4 text-sm text-warning-foreground">
              <p className="font-medium flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                Reference tariffs
              </p>
              <p className="mt-1 text-warning-foreground">{REFERENCE_TARIFF_WARNING}</p>
            </div>

            {lists.length === 0 ? (
              <div className="bg-card rounded-lg border border-border p-12 text-center">
                <BookOpen className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
                <p className="text-muted-foreground text-sm">No tariff lists returned. Run COSTA migrations (V007+) and ensure the BFF can reach costing-engine.</p>
              </div>
            ) : (
              <div className="space-y-8">
                {sections.map((section) => {
                  const entries = grouped.get(section.key) ?? [];
                  if (entries.length === 0) return null;
                  const sorted = [...entries].sort((a, b) => {
                    if (section.key === "uploaded_operational") {
                      return (a.uploadChannel ?? "").localeCompare(b.uploadChannel ?? "");
                    }
                    return a.list.name.localeCompare(b.list.name);
                  });
                  return (
                    <section key={section.key} className="space-y-3">
                      <div>
                        <h2 className="text-base font-semibold text-foreground">{section.title}</h2>
                        <p className="text-sm text-muted-foreground">{section.description}</p>
                      </div>
                      <div className="space-y-4">
                        {section.key === "uploaded_operational"
                          ? (() => {
                              const byChannel = new Map<string, typeof sorted>();
                              for (const e of sorted) {
                                const ch = e.uploadChannel ?? "other";
                                if (!byChannel.has(ch)) byChannel.set(ch, []);
                                byChannel.get(ch)!.push(e);
                              }
                              return Array.from(byChannel.entries()).map(([channel, rows]) => (
                                <div key={channel}>
                                  <h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground mb-2">
                                    {channel === "other" ? "Other uploads" : channel}
                                  </h3>
                                  <TariffListTable rows={rows.map((r) => r.list)} />
                                </div>
                              ));
                            })()
                          : (
                              <TariffListTable rows={sorted.map((r) => r.list)} />
                            )}
                      </div>
                    </section>
                  );
                })}
              </div>
            )}

            {legacyRows.length > 0 ? (
              <div className="space-y-2">
                <h2 className="text-sm font-semibold text-foreground">Legacy line-item schedule (TariffEntity)</h2>
                <p className="text-xs text-muted-foreground">From GET /internal/v1/finance/tariffs — older flat import; prefer tariff library lists above for pricing context.</p>
                <div className="bg-card rounded-lg border border-border overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b bg-background">
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Service Code</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Description</th>
                        <th className="text-right px-4 py-3 font-medium text-muted-foreground">Amount</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Effective</th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {legacyRows.map((tariff) => {
                        const statusStyle = STATUS_STYLES[tariff.attributes.status] ?? "bg-neutral-100 text-muted-foreground";
                        return (
                          <tr key={tariff.id} className="hover:bg-background transition-colors">
                            <td className="px-4 py-3 font-mono text-xs text-foreground">{tariff.attributes.serviceCode}</td>
                            <td className="px-4 py-3 text-muted-foreground">{tariff.attributes.description}</td>
                            <td className="px-4 py-3 text-right font-mono text-foreground">
                              {tariff.attributes.currency}{" "}
                              {tariff.attributes.tariffAmount.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                            </td>
                            <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                              {new Date(tariff.attributes.effectiveDate).toLocaleDateString()}
                            </td>
                            <td className="px-4 py-3">
                              <span className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}>
                                {tariff.attributes.status}
                              </span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : null}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function TariffListTable({ rows }: { rows: CostaTariffListRow[] }) {
  return (
    <div className="bg-card rounded-lg border border-border overflow-x-auto">
      <table className="w-full text-sm min-w-[800px]">
        <thead>
          <tr className="border-b bg-background">
            <th className="text-left px-4 py-3 font-medium text-muted-foreground">Name</th>
            <th className="text-left px-4 py-3 font-medium text-muted-foreground">Code</th>
            <th className="text-left px-4 py-3 font-medium text-muted-foreground">Type / family</th>
            <th className="text-left px-4 py-3 font-medium text-muted-foreground">Operational / reference</th>
            <th className="text-left px-4 py-3 font-medium text-muted-foreground">Currency & dates</th>
            <th className="text-left px-4 py-3 font-medium text-muted-foreground">Validation</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {rows.map((list) => {
            const meta = parseTariffMetadata(list.metadata);
            const isZwPoc = list.externalCode === "ZW-PLACEHOLDER-POC-V0.1";
            return (
              <tr key={list.id} className="hover:bg-background align-top">
                <td className="px-4 py-3 text-foreground">
                  <div className="font-medium">{list.name}</div>
                  {list.description ? <div className="text-xs text-muted-foreground mt-0.5">{list.description}</div> : null}
                  {isZwPoc ? (
                    <div className="text-xs text-warning-foreground mt-1 border-l-2 border-amber-300 pl-2">{ZIMBABWE_POC_DISCLAIMER}</div>
                  ) : null}
                </td>
                <td className="px-4 py-3 font-mono text-xs text-foreground">{list.externalCode}</td>
                <td className="px-4 py-3 text-muted-foreground">
                  <div>{list.tariffType}</div>
                  <div className="text-xs text-muted-foreground">{list.tariffFamily}</div>
                </td>
                <td className="px-4 py-3">
                  {list.referenceOnly ? (
                    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 text-warning-foreground px-2 py-0.5 text-xs">
                      <AlertTriangle className="h-3 w-3" />
                      Reference only
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 text-primary-hover px-2 py-0.5 text-xs">
                      Operational
                    </span>
                  )}
                  <div className="text-xs text-muted-foreground mt-1">
                    Billing: {list.approvedForBilling ? "approved" : "not approved"}
                  </div>
                </td>
                <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                  <div>{list.currency}</div>
                  <div className="text-xs text-muted-foreground">
                    {list.effectiveFrom ?? "—"} → {list.effectiveTo ?? "open"}
                  </div>
                  {meta?.provenance && typeof meta.provenance === "string" ? (
                    <div className="text-xs text-muted-foreground mt-1">Source: {meta.provenance}</div>
                  ) : null}
                </td>
                <td className="px-4 py-3 text-xs text-muted-foreground">
                  <div>{list.validationStatus}</div>
                  <div className="text-muted-foreground">{list.officialStatus}</div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
