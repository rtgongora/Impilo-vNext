"use client";

import Link from "next/link";
import { useMemo } from "react";
import { Heart, Loader2, MessageCircle, UserCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { flattenFeed, useSocialFeed, type SocialPost } from "@/hooks/queries/useSimbaSocial";

/** My activity — the wellness-social posts the signed-in person has authored. */
export default function WellnessSocialMyActivityPage() {
  const feed = useSocialFeed("MY_ACTIVITY");
  const posts = useMemo(() => flattenFeed(feed.data?.pages), [feed.data]);

  return (
    <AppLayout>
      <PageShell
        title="My activity"
        subtitle="Everything you've shared with the wellness community."
        icon={<UserCircle className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-4">
          {feed.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {feed.isError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              We could not load your activity. Please try again.
            </div>
          )}

          {!feed.isLoading && !feed.isError && posts.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center">
              <UserCircle className="mx-auto mb-3 h-8 w-8 text-slate-300" />
              <p className="text-sm text-slate-500">You haven&apos;t posted yet.</p>
              <Link
                href="/wellness/social/feed"
                className="mt-4 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white"
              >
                Share a wellness win
              </Link>
            </div>
          )}

          <div className="space-y-4">
            {posts.map((p: SocialPost) => (
              <Link
                key={p.postId}
                href={`/wellness/social/posts/${p.postId}`}
                className="block rounded-xl border border-slate-200 bg-white p-4 hover:border-emerald-300"
              >
                <div className="mb-1 text-xs font-medium text-slate-500">
                  {p.visibility} · {new Date(p.createdAt).toLocaleString()}
                </div>
                <p className="whitespace-pre-wrap text-sm text-slate-800">{p.body}</p>
                <div className="mt-3 flex items-center gap-4 text-sm text-slate-400">
                  <span className="inline-flex items-center gap-1">
                    <Heart className="h-4 w-4" />
                    {p.reactionCount}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <MessageCircle className="h-4 w-4" />
                    {p.commentCount}
                  </span>
                </div>
              </Link>
            ))}
          </div>

          {feed.hasNextPage && (
            <div className="text-center">
              <button
                type="button"
                onClick={() => void feed.fetchNextPage()}
                disabled={feed.isFetchingNextPage}
                className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-700"
              >
                {feed.isFetchingNextPage && <Loader2 className="h-4 w-4 animate-spin" />}
                Load more
              </button>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
