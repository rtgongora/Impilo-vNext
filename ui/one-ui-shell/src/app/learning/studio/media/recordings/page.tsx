"use client";

import Link from "next/link";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoMediaAssets } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioMediaRecordingsPage() {
  const { data, isLoading, isError } = useFundoMediaAssets();
  const items =
    ((data?.data as { items?: Array<{ id: string; title: string; status?: string; recordingType?: string; metadata?: { recordingType?: string } }> } | undefined)
      ?.items ?? []);
  const recordings = items.filter(
    (item) =>
      item.recordingType === "SCREEN_RECORDING" ||
      item.metadata?.recordingType === "SCREEN_RECORDING",
  );

  return (
    <FundoStudioWorkspace title="Media Recordings" subtitle="Screen recording metadata queue and draft review list.">
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading recordings…</p>
      ) : isError ? (
        <p className="text-sm text-destructive">Recording drafts could not be loaded.</p>
      ) : recordings.length === 0 ? (
        <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
          Recording drafts are created in Studio Media and can be linked to lessons as draft/reviewed/published assets.
        </div>
      ) : (
        <ul className="space-y-2 text-sm">
          {recordings.map((item) => (
            <li key={item.id} className="flex items-center justify-between rounded border border-border px-3 py-2">
              <span>
                {item.title} · {item.status ?? "DRAFT"}
              </span>
              <Link href={`/learning/studio/media/${item.id}`} className="text-teal-700 hover:underline">
                Open
              </Link>
            </li>
          ))}
        </ul>
      )}
    </FundoStudioWorkspace>
  );
}
