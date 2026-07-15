"use client";

import Link from "next/link";
import { Bookmark, ChevronRight, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSavedFeed } from "@/hooks/queries/useSimbaSocial";

/** Saved / bookmarked wellness-social content for the signed-in person. */
export default function WellnessSocialSavedPage() {
  const q = useSavedFeed();
  const saved = q.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Saved"
        subtitle="Wellness-community posts you've bookmarked to revisit."
        icon={<Bookmark className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-4">
          {q.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {q.isError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              We could not load your saved items. Please try again.
            </div>
          )}

          {!q.isLoading && !q.isError && saved.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center">
              <Bookmark className="mx-auto mb-3 h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-500">
                Nothing saved yet. Tap the bookmark on a post to keep it here.
              </p>
              <Link
                href="/wellness/social/feed"
                className="mt-4 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white"
              >
                Browse the community
              </Link>
            </div>
          )}

          <ul className="space-y-2">
            {saved.map((b) => (
              <li key={b.bookmarkId}>
                <Link
                  href={`/wellness/social/${b.subjectType === "POST" ? "posts" : "reels"}/${b.subjectId}`}
                  className="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3 hover:border-emerald-300"
                >
                  <div>
                    <span className="text-xs font-medium uppercase tracking-wide text-emerald-600">
                      {b.subjectType}
                    </span>
                    <p className="text-sm text-slate-700">Saved {new Date(b.createdAt).toLocaleString()}</p>
                  </div>
                  <ChevronRight className="h-4 w-4 text-slate-400" />
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </PageShell>
    </AppLayout>
  );
}
