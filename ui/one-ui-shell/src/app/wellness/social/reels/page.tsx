"use client";

import { useMemo, useRef, useState } from "react";
import { Film, Loader2, Play, Upload } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useReelFeed, useUploadReel, useReelPlayback } from "@/hooks/queries/useSimbaSocial";

function asRows(pages?: { data: unknown[] }[]): Array<Record<string, unknown>> {
  if (!pages) return [];
  return pages.flatMap((p) => (Array.isArray(p.data) ? p.data : [])) as Array<Record<string, unknown>>;
}

/** Wellness reels — short supportive videos. Media lives in document-service (MinIO); Simba refs it. */
export default function WellnessReelsPage() {
  const feed = useReelFeed();
  const upload = useUploadReel();
  const fileRef = useRef<HTMLInputElement>(null);

  const [caption, setCaption] = useState("");
  const [activeReelId, setActiveReelId] = useState<string | null>(null);
  const playback = useReelPlayback(activeReelId);

  const reels = useMemo(() => asRows(feed.data?.pages), [feed.data]);

  function submitUpload() {
    const file = fileRef.current?.files?.[0];
    if (!file) return;
    const form = new FormData();
    form.append("file", file);
    if (caption.trim()) form.append("caption", caption);
    form.append("visibility", "PUBLIC_WITHIN_IMPILO");
    upload.mutate(form, {
      onSuccess: () => {
        setCaption("");
        if (fileRef.current) fileRef.current.value = "";
      },
    });
  }

  const playbackUrl = playback.data?.data?.playback_url;

  return (
    <AppLayout>
      <PageShell
        title="Wellness reels"
        subtitle="Short, supportive wellness videos from the community."
        icon={<Film className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-6">
          <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
            <h2 className="text-sm font-semibold text-slate-700">Share a reel</h2>
            <input ref={fileRef} type="file" accept="video/*" className="block text-sm" />
            <input
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              placeholder="Caption (no clinical values, please)"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
            <button
              type="button"
              onClick={submitUpload}
              disabled={upload.isPending}
              className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {upload.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
              Upload reel
            </button>
            {upload.isError && (
              <p className="text-xs text-red-600">{(upload.error as Error).message}</p>
            )}
          </section>

          {activeReelId && (
            <section className="rounded-xl border border-slate-200 bg-black p-2">
              {playback.isLoading ? (
                <div className="flex h-56 items-center justify-center text-slate-400">
                  <Loader2 className="h-6 w-6 animate-spin" />
                </div>
              ) : playbackUrl ? (
                <video
                  controls
                  autoPlay
                  src={playbackUrl}
                  className="max-h-[70vh] w-full rounded-lg"
                />
              ) : (
                <div className="flex h-56 items-center justify-center text-slate-400">
                  Playback unavailable
                </div>
              )}
            </section>
          )}

          {feed.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            {reels.map((r) => (
              <button
                key={String(r.reelId)}
                type="button"
                onClick={() => setActiveReelId(String(r.reelId))}
                className="group relative flex aspect-[9/16] flex-col justify-end overflow-hidden rounded-xl border border-slate-200 bg-slate-100 p-3 text-left"
              >
                <span className="absolute inset-0 flex items-center justify-center text-slate-300 group-hover:text-emerald-500">
                  <Play className="h-8 w-8" />
                </span>
                <span className="relative z-10 line-clamp-2 text-xs font-medium text-slate-700">
                  {String(r.caption ?? "Wellness reel")}
                </span>
              </button>
            ))}
          </div>

          {!feed.isLoading && reels.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center text-sm text-slate-500">
              No reels yet. Be the first to share one!
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
