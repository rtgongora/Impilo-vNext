"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFixedAssetDepreciationSchedule, useFixedAssetDetails } from "@/hooks/queries/useAssets";

export default function ErpFixedAssetsPage() {
  const sp = useSearchParams();
  const fromQuery = sp.get("assetId") ?? "";
  const [assetId, setAssetId] = useState(fromQuery);
  const eff = assetId.trim();

  const details = useFixedAssetDetails(eff || null);
  const schedule = useFixedAssetDepreciationSchedule(eff || null);

  return (
    <AppLayout>
      <PageShell
        title="Fixed assets"
        subtitle="Financial detail and depreciation schedule from asset-registry via BFF"
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link href="/erp" className="text-sm text-blue-600 hover:underline">
            ← ERP hub
          </Link>
          <label className="flex flex-wrap items-center gap-2 text-sm text-slate-700">
            Asset ID (UUID)
            <input
              className="min-w-[280px] rounded border border-slate-300 px-2 py-1 font-mono text-xs"
              value={assetId}
              onChange={(e) => setAssetId(e.target.value)}
              placeholder="00000000-0000-0000-0000-000000000000"
            />
          </label>
        </div>

        <div className="space-y-6">
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Fixed asset details</h3>
            <pre className="mt-2 max-h-64 overflow-auto text-xs">
              {JSON.stringify(details.data ?? details.error ?? details.isLoading, null, 2)}
            </pre>
          </section>
          <section className="rounded-lg border border-slate-200 bg-slate-50 p-4">
            <h3 className="text-sm font-semibold text-slate-800">Depreciation schedule</h3>
            <pre className="mt-2 max-h-64 overflow-auto text-xs">
              {JSON.stringify(schedule.data ?? schedule.error ?? schedule.isLoading, null, 2)}
            </pre>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
