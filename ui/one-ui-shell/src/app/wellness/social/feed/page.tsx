"use client";

import { useMemo, useState } from "react";
import {
  Flag,
  Heart,
  Loader2,
  MessageCircle,
  Send,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  flattenFeed,
  useCreatePost,
  useReact,
  useReportContent,
  useSocialFeed,
  type SocialPost,
} from "@/hooks/queries/useSimbaSocial";

const FILTERS: [string, string][] = [
  ["ALL", "For you"],
  ["MY_ACTIVITY", "My activity"],
];

/** Wellness-social feed — supportive, consent-governed posts (distinct from the generic social feed). */
export default function WellnessSocialFeedPage() {
  const [filter, setFilter] = useState("ALL");
  const feed = useSocialFeed(filter);
  const createPost = useCreatePost();
  const react = useReact();
  const report = useReportContent();

  const [body, setBody] = useState("");
  const [visibility, setVisibility] = useState("PUBLIC_WITHIN_IMPILO");
  const [error, setError] = useState<string | null>(null);

  const posts = useMemo(() => flattenFeed(feed.data?.pages), [feed.data]);

  function submitPost() {
    if (!body.trim()) return;
    setError(null);
    createPost.mutate(
      { body, visibility },
      {
        onSuccess: () => setBody(""),
        onError: (e) => setError(e.message),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Wellness community"
        subtitle="Encourage and be encouraged — a supportive, private-by-default wellness space."
        icon={<Users className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-6">
          <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
            <textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Share a wellness win or encouragement…"
              rows={3}
              className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
            <div className="flex items-center justify-between">
              <select
                value={visibility}
                onChange={(e) => setVisibility(e.target.value)}
                className="rounded-lg border border-slate-300 px-2 py-1.5 text-xs"
              >
                <option value="PRIVATE">Only me</option>
                <option value="FOLLOWER">My followers</option>
                <option value="PUBLIC_WITHIN_IMPILO">Everyone on Impilo</option>
              </select>
              <button
                type="button"
                onClick={submitPost}
                disabled={createPost.isPending || !body.trim()}
                className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {createPost.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
                Post
              </button>
            </div>
            {error && <p className="text-xs text-red-600">{error}</p>}
          </section>

          <div className="flex gap-2">
            {FILTERS.map(([v, label]) => (
              <button
                key={v}
                type="button"
                onClick={() => setFilter(v)}
                className={`rounded-full px-4 py-1.5 text-sm font-medium ${
                  filter === v ? "bg-emerald-600 text-white" : "bg-slate-100 text-slate-600"
                }`}
              >
                {label}
              </button>
            ))}
          </div>

          {feed.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {!feed.isLoading && posts.length === 0 && (
            <div className="rounded-xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center text-sm text-slate-500">
              No posts yet. Share the first wellness win!
            </div>
          )}

          <div className="space-y-4">
            {posts.map((p: SocialPost) => (
              <article key={p.postId} className="rounded-xl border border-slate-200 bg-white p-4">
                <div className="mb-1 flex items-center justify-between">
                  <span className="text-xs font-medium text-slate-500">
                    {p.actorType} · {new Date(p.createdAt).toLocaleString()}
                  </span>
                  <button
                    type="button"
                    onClick={() =>
                      report.mutate({
                        subject_type: "POST",
                        subject_id: p.postId,
                        subject_owner_cpid: p.actorCpid,
                        reason: "ABUSE",
                      })
                    }
                    title="Report"
                    className="text-slate-300 hover:text-red-500"
                  >
                    <Flag className="h-4 w-4" />
                  </button>
                </div>
                <p className="whitespace-pre-wrap text-sm text-slate-800">{p.body}</p>
                <div className="mt-3 flex items-center gap-4 text-sm text-slate-500">
                  <button
                    type="button"
                    onClick={() =>
                      react.mutate({ subjectType: "POST", subjectId: p.postId, reaction: "ENCOURAGE" })
                    }
                    className="inline-flex items-center gap-1 hover:text-emerald-600"
                  >
                    <Heart className="h-4 w-4" />
                    {p.reactionCount}
                  </button>
                  <span className="inline-flex items-center gap-1">
                    <MessageCircle className="h-4 w-4" />
                    {p.commentCount}
                  </span>
                </div>
              </article>
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
