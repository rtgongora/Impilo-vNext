"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
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
          <Link href="/erp" className="text-sm text-impilo-500 hover:underline">
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
          <QueryResultPanel title="Fixed asset details" isPending={details.isPending} isLoading={details.isPending} isError={details.isError} error={details.error} data={details.data} />
          <QueryResultPanel title="Depreciation schedule" isPending={schedule.isPending} isLoading={schedule.isPending} isError={schedule.isError} error={schedule.error} data={schedule.data} />
        </div>
      </PageShell>
    </AppLayout>
  );
}
