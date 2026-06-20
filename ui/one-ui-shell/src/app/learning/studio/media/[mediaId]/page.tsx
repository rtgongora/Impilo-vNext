"use client";

import Link from "next/link";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoMediaAssets } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioMediaAssetPage({ params }: { params: { mediaId: string } }) {
  const { data, isLoading, isError } = useFundoMediaAssets();
  const items =
    ((data?.data as { items?: Array<{ id: string; title: string; mediaType?: string; status?: string; metadata?: Record<string, unknown> }> } | undefined)
      ?.items ?? []);
  const asset = items.find((item) => item.id === params.mediaId);

  return (
    <FundoStudioWorkspace title="Media Asset Detail" subtitle="Media metadata, transcript/caption references, and lesson linking.">
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading media asset…</p>
      ) : isError ? (
        <p className="text-sm text-destructive">Media assets could not be loaded from the learning BFF.</p>
      ) : asset ? (
        <div className="space-y-3 rounded border border-border bg-card p-4 text-sm text-foreground">
          <p className="font-semibold">{asset.title}</p>
          <p className="text-muted-foreground">
            {asset.mediaType ?? "VIDEO"} · {asset.status ?? "DRAFT"}
          </p>
          {asset.metadata ? (
            <dl className="space-y-2 rounded bg-muted p-3 text-xs">
              {Object.entries(asset.metadata).map(([key, value]) => (
                <div key={key} className="grid grid-cols-[minmax(8rem,30%)_1fr] gap-2">
                  <dt className="font-medium text-muted-foreground">{key}</dt>
                  <dd className="break-all font-mono text-foreground">
                    {value == null ? "—" : typeof value === "object" ? JSON.stringify(value) : String(value)}
                  </dd>
                </div>
              ))}
            </dl>
          ) : null}
          <Link href="/learning/studio/media" className="text-teal-700 hover:underline">
            Back to studio media
          </Link>
        </div>
      ) : (
        <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
          Media asset reference: <span className="font-mono">{params.mediaId}</span>
          <p className="mt-2 text-muted-foreground">No matching asset in the current catalog slice.</p>
        </div>
      )}
    </FundoStudioWorkspace>
  );
}
