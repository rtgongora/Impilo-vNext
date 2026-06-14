"use client";

import Link from "next/link";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoMediaAssets } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioMediaVoiceoversPage() {
  const { data, isLoading, isError } = useFundoMediaAssets();
  const items =
    ((data?.data as { items?: Array<{ id: string; title: string; status?: string; mediaType?: string }> } | undefined)
      ?.items ?? []);
  const voiceovers = items.filter((item) => item.mediaType === "AUDIO" || item.title.toLowerCase().includes("voiceover"));

  return (
    <FundoStudioWorkspace title="Media Voiceovers" subtitle="Voiceover script drafts and future TTS adapter handoff queue.">
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading voiceover drafts…</p>
      ) : isError ? (
        <p className="text-sm text-destructive">Voiceover queue could not be loaded.</p>
      ) : voiceovers.length === 0 ? (
        <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
          Voiceover generation is provider-agnostic and currently draft-first. No external provider is hard-coded by default.
        </div>
      ) : (
        <ul className="space-y-2 text-sm">
          {voiceovers.map((item) => (
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
