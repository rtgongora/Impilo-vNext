"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { ERP_ASSET_COLUMNS } from "@/lib/json-api/generic-table-columns";
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
          <Link href="/erp" className="text-sm text-primary hover:underline">
            ← ERP hub
          </Link>
          <label className="flex flex-wrap items-center gap-2 text-sm text-foreground">
            Asset ID (UUID)
            <input
              className="min-w-[280px] rounded border border-border px-2 py-1 font-mono text-xs"
              value={assetId}
              onChange={(e) => setAssetId(e.target.value)}
              placeholder="00000000-0000-0000-0000-000000000000"
            />
          </label>
        </div>

        <div className="space-y-6">
          <JsonApiDataTable data={details.data} columns={ERP_ASSET_COLUMNS} isLoading={details.isPending} error={details.error as Error | null} emptyTitle="No asset details" />
          <JsonApiDataTable data={schedule.data} columns={ERP_ASSET_COLUMNS} isLoading={schedule.isPending} error={schedule.error as Error | null} emptyTitle="No depreciation schedule" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
