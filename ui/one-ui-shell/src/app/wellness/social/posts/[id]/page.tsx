"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { Bookmark, Flag, Heart, Loader2, MessageCircle, Send } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAddComment,
  useBookmark,
  usePost,
  usePostComments,
  useReact,
  useReportContent,
} from "@/hooks/queries/useSimbaSocial";

/** The six supportive wellness-social reactions (mirrors SocialInteractionService allow-list). */
const REACTIONS: [string, string][] = [
  ["ENCOURAGE", "💪 Encourage"],
  ["LIKE", "👍 Like"],
  ["CELEBRATE", "🎉 Celebrate"],
  ["HELPFUL", "💡 Helpful"],
  ["SUPPORT", "🤝 Support"],
  ["THANK_YOU", "🙏 Thank you"],
];

interface Comment {
  commentId?: string;
  id?: number;
  actorCpid?: string;
  actorType?: string;
  body: string;
  createdAt: string;
}

/** Wellness-social post detail — the post plus its discussion thread, reactions and save. */
export default function WellnessSocialPostDetailPage() {
  const params = useParams<{ id: string }>();
  const postId = params?.id;

  const postQ = usePost(postId);
  const commentsQ = usePostComments(postId);
  const addComment = useAddComment();
  const react = useReact();
  const bookmark = useBookmark();
  const report = useReportContent();

  const [commentBody, setCommentBody] = useState("");
  const [showReactions, setShowReactions] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const post = postQ.data?.data;
  const comments = useMemo<Comment[]>(() => {
    const raw = commentsQ.data?.data;
    return Array.isArray(raw) ? (raw as Comment[]) : [];
  }, [commentsQ.data]);

  function submitComment() {
    if (!postId || !commentBody.trim()) return;
    setError(null);
    addComment.mutate(
      { postId, body: commentBody },
      {
        onSuccess: () => setCommentBody(""),
        onError: (e) => setError(e.message),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Post"
        subtitle="A wellness-community post and its discussion."
        icon={<MessageCircle className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-2xl space-y-6">
          {postQ.isLoading && (
            <div className="flex items-center justify-center py-16 text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin" />
            </div>
          )}

          {postQ.isError && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              We could not load this post. It may be private or no longer available.
            </div>
          )}

          {post && (
            <article className="rounded-xl border border-slate-200 bg-white p-4">
              <div className="mb-1 flex items-center justify-between">
                <span className="text-xs font-medium text-slate-500">
                  {post.actorType} · {new Date(post.createdAt).toLocaleString()}
                </span>
                <button
                  type="button"
                  onClick={() =>
                    report.mutate({
                      subject_type: "POST",
                      subject_id: post.postId,
                      subject_owner_cpid: post.actorCpid,
                      reason: "ABUSE",
                    })
                  }
                  title="Report"
                  className="text-slate-300 hover:text-red-500"
                >
                  <Flag className="h-4 w-4" />
                </button>
              </div>
              <p className="whitespace-pre-wrap text-sm text-slate-800">{post.body}</p>
              <div className="relative mt-3 flex items-center gap-4 text-sm text-slate-500">
                <button
                  type="button"
                  onClick={() => setShowReactions((s) => !s)}
                  className="inline-flex items-center gap-1 hover:text-emerald-600"
                  aria-haspopup="menu"
                  aria-expanded={showReactions}
                >
                  <Heart className="h-4 w-4" />
                  {post.reactionCount}
                </button>
                {showReactions && (
                  <div
                    role="menu"
                    className="absolute bottom-8 left-0 z-10 flex flex-wrap gap-1 rounded-lg border border-slate-200 bg-white p-2 shadow-lg"
                  >
                    {REACTIONS.map(([value, label]) => (
                      <button
                        key={value}
                        type="button"
                        role="menuitem"
                        onClick={() => {
                          react.mutate({ subjectType: "POST", subjectId: post.postId, reaction: value });
                          setShowReactions(false);
                        }}
                        className="rounded-md px-2 py-1 text-xs hover:bg-emerald-50"
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                )}
                <span className="inline-flex items-center gap-1">
                  <MessageCircle className="h-4 w-4" />
                  {post.commentCount}
                </span>
                <button
                  type="button"
                  onClick={() => bookmark.mutate({ subjectType: "POST", subjectId: post.postId })}
                  title="Save"
                  className="inline-flex items-center gap-1 hover:text-emerald-600"
                >
                  <Bookmark className="h-4 w-4" />
                </button>
              </div>
            </article>
          )}

          {post && (
            <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
              <h2 className="text-sm font-semibold text-slate-700">Discussion</h2>
              <div className="flex items-start gap-2">
                <textarea
                  value={commentBody}
                  onChange={(e) => setCommentBody(e.target.value)}
                  placeholder="Add an encouraging comment…"
                  rows={2}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
                />
                <button
                  type="button"
                  aria-label="Post comment"
                  onClick={submitComment}
                  disabled={addComment.isPending || !commentBody.trim()}
                  className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  {addComment.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Send className="h-4 w-4" />
                  )}
                </button>
              </div>
              {error && <p className="text-xs text-red-600">{error}</p>}

              {commentsQ.isLoading && (
                <div className="flex items-center justify-center py-6 text-slate-400">
                  <Loader2 className="h-5 w-5 animate-spin" />
                </div>
              )}

              {!commentsQ.isLoading && comments.length === 0 && (
                <p className="py-4 text-center text-sm text-slate-400">
                  No comments yet. Be the first to encourage.
                </p>
              )}

              <ul className="space-y-3">
                {comments.map((c, i) => (
                  <li key={c.commentId ?? c.id ?? i} className="rounded-lg bg-slate-50 p-3">
                    <div className="mb-1 text-xs font-medium text-slate-500">
                      {c.actorType ?? "Member"} · {new Date(c.createdAt).toLocaleString()}
                    </div>
                    <p className="whitespace-pre-wrap text-sm text-slate-800">{c.body}</p>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
