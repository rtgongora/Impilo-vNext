"use client";

import Link from "next/link";
import { Package, ArrowRight } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { StockManagementPanel } from "@/components/workspace-ops/StockManagementPanel";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export default function WellnessCommoditiesPage() {
  const facility = useFacilityStore((s) => s.facility);

  return (
    <AppLayout>
      <PageShell
        title="Programme commodities (Dura)"
        subtitle="Stock for wellness programmes is owned by Dura (inventory-service). Simba is wellness — it does not own or manage stock; this is a convenience window into the Dura ledger."
        icon={<Package className="h-6 w-6" />}
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        <div className="mb-6 rounded-xl border border-teal-200 bg-teal-50/70 p-4 text-sm text-teal-950">
          <p>
            Commodities for wellness programmes are stock positions owned by <span className="font-semibold">Dura</span>
            {" "}(inventory-service), not Simba. Use the embedded stock panel below or open the full{" "}
            <Link href="/inventory/stock-management" className="font-semibold text-teal-800 underline">
              stock management workspace
            </Link>{" "}
            for ledger posts and procurement signals.
          </p>
          <Link
            href="/inventory/stock-management"
            className="mt-3 inline-flex items-center gap-1 text-sm font-medium text-teal-800 hover:text-teal-900"
          >
            Open full stock management <ArrowRight className="h-4 w-4" />
          </Link>
        </div>

        {!facility ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Select a facility to scope commodity stock.
            <div className="mt-3">
              <Link href="/facility" className="font-medium underline">
                Choose facility
              </Link>
            </div>
          </div>
        ) : (
          <StockManagementPanel facilityId={facility.id} />
        )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
