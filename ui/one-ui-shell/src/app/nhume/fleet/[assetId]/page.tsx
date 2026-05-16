"use client";

/**
 * Fleet asset detail.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Truck, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useNhumeAsset } from "@/hooks/useNhume";

export default function NhumeFleetAssetPage() {
  const params = useParams<{ assetId: string }>();
  const id = params?.assetId as string | undefined;
  const { data, isPending, isError } = useNhumeAsset(id);

  return (
    <AppLayout>
      <PageShell
        title="Fleet Asset"
        subtitle="Capability profile, telemetry and assignment context"
        icon={<Truck className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/nhume/fleet" className="inline-flex items-center gap-1 text-sm text-gray-600 hover:text-gray-900">
            <ArrowLeft className="h-4 w-4" /> Back to fleet
          </Link>
        </div>
        {isPending && (
          <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center text-gray-500">
            <Loader2 className="inline-block h-5 w-5 animate-spin text-teal-500 mr-2" /> Loading…
          </div>
        )}
        {isError && <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">Asset not found.</div>}
        {data && (
          <div className="rounded-2xl border border-gray-200 bg-white p-5">
            <h2 className="text-xl font-bold text-gray-900">{String((data as Record<string, unknown>).registration_number ?? id)}</h2>
            <pre className="mt-4 max-h-[600px] overflow-auto rounded-xl bg-gray-50 p-4 text-xs text-gray-800">
              {JSON.stringify(data, null, 2)}
            </pre>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
