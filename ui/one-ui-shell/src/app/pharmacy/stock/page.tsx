"use client";

/**
 * Pharmacy Stock — View pharmacy stock levels.
 * Route: /pharmacy/stock
 */

import { Loader2, AlertTriangle, Package, AlertCircle } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, ClipboardList, FileText, Pill, User } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { WorkflowHeader } from "@/components/workflow/WorkflowHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface StockItem {
  id: string;
  type: "pharmacy_stock";
  attributes: {
    medicationName: string;
    currentStock: number;
    reorderLevel: number;
    unit: string;
    lastRestocked: string | null;
    [key: string]: unknown;
  };
}

export default function PharmacyStockPage() {
  const facility = useFacilityStore((state) => state.facility);
  const searchParams = useSearchParams();
  const { data, isLoading, error } = useQuery<ApiResponse<StockItem[]>>({
    queryKey: ["pharmacy-stock"],
    queryFn: () => apiClient.get<ApiResponse<StockItem[]>>("/internal/v1/pharmacy/stock"),
  });
  const patientId = searchParams.get("patientId");
  const encounterId = searchParams.get("encounterId");
  const source = searchParams.get("source");

  const items = data?.data ?? [];
  const lowStockCount = items.filter((item) => item.attributes.currentStock <= item.attributes.reorderLevel).length;
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
    patientId
      ? { href: `/ehr/${patientId}/medications`, label: "Medication Review", icon: FileText, tone: "secondary" as const }
      : null,
    { href: withHandoff("/pharmacy/prescriptions"), label: "Prescriptions", icon: FileText, tone: "secondary" as const },
    { href: withHandoff("/pharmacy/dispense"), label: "Dispense", icon: Pill, tone: "secondary" as const },
  ].filter((value): value is NonNullable<typeof value> => Boolean(value));

  return (
    <AppLayout>
      <PageShell title="Pharmacy Stock" subtitle="Current medication stock levels">
        <div className="mb-4">
          <Link
            href={withHandoff("/pharmacy")}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to pharmacy
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading stock data...</span>
          </div>
        ) : error ? (
          <div className="bg-danger-soft rounded-lg border border-danger/28 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load stock data</p>
          </div>
        ) : items.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Package className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No stock items found</p>
          </div>
        ) : (
          <div className="space-y-6">
            <WorkflowHeader
              badge="Stock continuity"
              badgeIcon={Package}
              title="Keep stock awareness tied to the prescription and dispense loop so medication handoff is not blocked by invisible supply constraints."
              description="Pharmacy stock now surfaces the facility in scope, source workflow, and the next medication action before staff review inventory constraints."
              context={[
                { label: "Facility", value: facility?.name ?? "No facility selected" },
                { label: "Source", value: source === "discharge" ? "Outcome handoff" : "Pharmacy workspace" },
                { label: "Stock items", value: `${items.length} item${items.length === 1 ? "" : "s"}` },
              ]}
              actions={actions}
              metrics={[
                {
                  label: "Low stock",
                  value: String(lowStockCount),
                  detail: "Medications at or below reorder point that may affect dispensing continuity.",
                },
                {
                  label: "In stock",
                  value: String(items.length - lowStockCount),
                  detail: "Items currently above reorder threshold and ready to support dispensing.",
                },
                {
                  label: "Next action",
                  value: lowStockCount > 0 ? "Escalate supply risk" : "Support dispensing",
                  detail:
                    encounterId && patientId
                      ? "Use this stock view to confirm the encounter-linked medication plan is fulfillable before returning to dispense or chart."
                      : "Review supply constraints here, then move into prescriptions or dispense for the next medication action.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Stock loop status
              </p>
              <p className="mt-2 text-sm text-foreground">
                {source === "discharge"
                  ? "This stock view was reached from encounter outcome, so the loop here is confirming medication availability without losing the linked encounter or chart."
                  : "This workspace monitors broader inventory, but stock checks should still connect cleanly back to prescriptions, dispense, and the source encounter when context exists."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Review supply risks here, then move back to prescriptions, dispense, the encounter, or the chart when the medication handoff needs a clinical or operational decision.
              </p>
            </div>

            <div className="bg-card rounded-lg border border-border overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-background">
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Medication</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Current Stock</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Reorder Level</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Unit</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Last Restocked</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => {
                    const isLow = item.attributes.currentStock <= item.attributes.reorderLevel;
                    return (
                      <tr key={item.id} className="border-b last:border-b-0 hover:bg-background">
                        <td className="px-4 py-3 font-medium text-foreground">
                          {item.attributes.medicationName}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <span className={isLow ? "text-red-600 font-medium" : ""}>
                            {item.attributes.currentStock}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">{item.attributes.reorderLevel}</td>
                        <td className="px-4 py-3 text-muted-foreground">{item.attributes.unit}</td>
                        <td className="px-4 py-3 text-muted-foreground">
                          {item.attributes.lastRestocked
                            ? new Date(item.attributes.lastRestocked).toLocaleDateString()
                            : "\u2014"}
                        </td>
                        <td className="px-4 py-3">
                          {isLow ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full font-medium bg-red-100 text-danger">
                              <AlertCircle className="w-3 h-3" /> Low Stock
                            </span>
                          ) : (
                            <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-green-100 text-green-700">
                              In Stock
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
