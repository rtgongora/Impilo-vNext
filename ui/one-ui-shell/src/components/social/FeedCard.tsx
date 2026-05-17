"use client";

/**
 * FeedCard — single timeline item. Handles reactions, bookmark, comments
 * expand/collapse, report, and audit-safe visibility metadata.
 */

import { useState } from "react";
import {
  Bookmark,
  BookmarkCheck,
  Flag,
  Globe,
  Heart,
  Lock,
  MessageCircle,
  MoreHorizontal,
  Pin,
  Share2,
  ShieldCheck,
  Sparkles,
} from "lucide-react";
import {
  type SocialPost,
  type ReactionType,
  useAddComment,
  usePostComments,
  useReactToPost,
  useReportPost,
  useToggleBookmark,
  useUnreactPost,
} from "@/hooks/queries/useSocial";

interface Props {
  post: SocialPost;
}

export function FeedCard({ post }: Props) {
  const [showComments, setShowComments] = useState(false);
  const [showReportPanel, setShowReportPanel] = useState(false);
  const [commentDraft, setCommentDraft] = useState("");
  const [reportReason, setReportReason] = useState("");

  const reactMut = useReactToPost();
  const unreactMut = useUnreactPost();
  const bookmarkMut = useToggleBookmark();
  const commentsQ = usePostComments(showComments ? post.id : null);
  const addCommentMut = useAddComment();
  const reportMut = useReportPost();

  const viewerReaction = post.reactionSummary?.viewerReaction;
  const reactionTotal = post.reactionSummary?.total ?? 0;

  const handleReact = async (type: ReactionType) => {
    if (viewerReaction === type) {
      await unreactMut.mutateAsync({ postId: post.id });
    } else {
      await reactMut.mutateAsync({ postId: post.id, type });
    }
  };

  const handleBookmark = async () => {
    await bookmarkMut.mutateAsync({ postId: post.id, on: !post.bookmarked });
  };

  const handleAddComment = async () => {
    if (!commentDraft.trim()) return;
    await addCommentMut.mutateAsync({ postId: post.id, body: commentDraft.trim() });
    setCommentDraft("");
  };

  const handleReport = async () => {
    if (!reportReason.trim()) return;
    await reportMut.mutateAsync({ postId: post.id, reason: reportReason.trim() });
    setShowReportPanel(false);
    setReportReason("");
  };

  return (
    <article className="impilo-surface-card overflow-hidden">
      {/* Header */}
      <header className="flex items-start gap-3 px-4 pt-4">
        <span
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white"
          style={{ background: avatarColor(post.author.displayName) }}
          aria-hidden
        >
          {(post.author.displayName || "?").charAt(0).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="truncate text-sm font-semibold text-[color:var(--text-primary)]">
              {post.author.displayName}
            </span>
            {post.author.verified && (
              <ShieldCheck className="h-3.5 w-3.5 text-[color:var(--primary)]" aria-label="Verified" />
            )}
            {post.author.roleBadge && (
              <span className="rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-[color:var(--text-secondary)]">
                {post.author.roleBadge}
              </span>
            )}
            {post.pinned && <Pin className="h-3 w-3 text-amber-500" aria-label="Pinned" />}
          </div>
          <p className="truncate text-xs text-[color:var(--text-muted)]">
            {post.author.facilityName ? `${post.author.facilityName} · ` : ""}
            {formatTimestamp(post.publishedAt ?? post.createdAt)}
            {" · "}
            <VisibilityBadge visibility={post.visibility} />
            {post.source && (
              <>
                {" · "}
                <span className="italic">via {post.source.replace(/_/g, " ")}</span>
              </>
            )}
          </p>
        </div>
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowReportPanel((s) => !s)}
            className="rounded-full p-1.5 text-[color:var(--text-muted)] hover:bg-[color:var(--surface-soft)]"
            aria-label="Post actions"
          >
            <MoreHorizontal className="h-4 w-4" />
          </button>
          {showReportPanel && (
            <div className="absolute right-0 z-10 mt-2 w-64 space-y-2 rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface)] p-3 shadow-lg">
              <p className="text-xs font-semibold text-[color:var(--text-primary)]">Report this post</p>
              <textarea
                value={reportReason}
                onChange={(e) => setReportReason(e.target.value)}
                rows={3}
                placeholder="Why are you reporting this?"
                className="w-full rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-2 py-1.5 text-xs focus:border-[color:var(--primary)] focus:outline-none"
              />
              <div className="flex justify-end gap-1.5">
                <button
                  onClick={() => setShowReportPanel(false)}
                  className="rounded-lg px-2 py-1 text-xs text-[color:var(--text-muted)]"
                >
                  Cancel
                </button>
                <button
                  onClick={handleReport}
                  disabled={!reportReason.trim() || reportMut.isPending}
                  className="inline-flex items-center gap-1 rounded-lg bg-red-500 px-2 py-1 text-xs font-medium text-white disabled:opacity-50"
                >
                  <Flag className="h-3 w-3" /> Report
                </button>
              </div>
            </div>
          )}
        </div>
      </header>

      {/* Body */}
      <div className="px-4 pt-3">
        {post.title && (
          <h3 className="mb-1 text-base font-semibold text-[color:var(--text-primary)]">{post.title}</h3>
        )}
        <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-[color:var(--text-secondary)]">
          {post.body}
        </p>
        {post.topics && post.topics.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {post.topics.map((t) => (
              <span
                key={t}
                className="rounded-full bg-[color:var(--primary-soft)] px-2 py-0.5 text-[11px] text-[color:var(--primary-hover)]"
              >
                #{t}
              </span>
            ))}
          </div>
        )}
        {post.attachments && post.attachments.length > 0 && (
          <ul className="mt-3 space-y-1.5">
            {post.attachments.map((a, idx) => (
              <li
                key={a.id ?? idx}
                className="flex items-center gap-2 rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-2.5 py-2 text-xs text-[color:var(--text-secondary)]"
              >
                <Sparkles className="h-3.5 w-3.5 text-[color:var(--primary)]" />
                <span className="truncate">{a.previewText || a.url || a.kind || "Attachment"}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Action bar */}
      <footer className="mt-3 flex items-center justify-between border-t border-[color:var(--border-soft)] px-2 py-1.5 text-xs">
        <div className="flex items-center gap-0.5">
          <ReactionButton
            active={viewerReaction === "LIKE"}
            onClick={() => handleReact("LIKE")}
            icon={<Heart className={`h-4 w-4 ${viewerReaction === "LIKE" ? "fill-current" : ""}`} />}
            label={reactionTotal > 0 ? String(reactionTotal) : "Like"}
          />
          <ReactionButton
            onClick={() => setShowComments((s) => !s)}
            icon={<MessageCircle className="h-4 w-4" />}
            label={post.commentCount && post.commentCount > 0 ? String(post.commentCount) : "Comment"}
          />
          <ReactionButton
            onClick={() => navigator?.share?.({ text: post.body, title: post.title ?? undefined }).catch(() => {})}
            icon={<Share2 className="h-4 w-4" />}
            label="Share"
          />
        </div>
        <ReactionButton
          active={post.bookmarked}
          onClick={handleBookmark}
          icon={post.bookmarked ? <BookmarkCheck className="h-4 w-4 fill-current" /> : <Bookmark className="h-4 w-4" />}
          label={post.bookmarked ? "Saved" : "Save"}
        />
      </footer>

      {/* Comments */}
      {showComments && (
        <div className="space-y-2 border-t border-[color:var(--border-soft)] bg-[color:var(--surface-soft)]/40 px-4 py-3">
          {commentsQ.isLoading && <p className="text-xs text-[color:var(--text-muted)]">Loading comments…</p>}
          {!commentsQ.isLoading && Array.isArray(commentsQ.data) && (commentsQ.data as unknown[]).length === 0 && (
            <p className="text-xs text-[color:var(--text-muted)]">Be the first to comment.</p>
          )}
          {!commentsQ.isLoading && Array.isArray(commentsQ.data) && (commentsQ.data as Array<Record<string, unknown>>).map((c, i) => (
            <CommentItem key={String(c.id ?? i)} comment={c} />
          ))}
          <div className="flex items-start gap-2 pt-1">
            <textarea
              value={commentDraft}
              onChange={(e) => setCommentDraft(e.target.value)}
              rows={1}
              placeholder="Write a comment…"
              className="min-h-[36px] flex-1 resize-y rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-2.5 py-1.5 text-xs focus:border-[color:var(--primary)] focus:outline-none"
            />
            <button
              onClick={handleAddComment}
              disabled={!commentDraft.trim() || addCommentMut.isPending}
              className="rounded-lg bg-[color:var(--primary)] px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
            >
              Reply
            </button>
          </div>
        </div>
      )}
    </article>
  );
}

function CommentItem({ comment }: { comment: Record<string, unknown> }) {
  const author = comment.author as Record<string, unknown> | undefined;
  const displayName = String(author?.displayName ?? "Someone");
  const body = String(comment.body ?? "");
  const createdAt = comment.createdAt as string | undefined;
  return (
    <div className="flex gap-2.5">
      <span
        className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold text-white"
        style={{ background: avatarColor(displayName) }}
      >
        {displayName.charAt(0).toUpperCase()}
      </span>
      <div className="rounded-lg bg-[color:var(--surface)] px-2.5 py-1.5 text-xs">
        <p className="font-medium text-[color:var(--text-primary)]">{displayName}</p>
        <p className="text-[color:var(--text-secondary)]">{body}</p>
        {createdAt && <p className="mt-0.5 text-[10px] text-[color:var(--text-muted)]">{formatTimestamp(createdAt)}</p>}
      </div>
    </div>
  );
}

function ReactionButton({
  active,
  onClick,
  icon,
  label,
}: {
  active?: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors ${
        active
          ? "text-[color:var(--primary)]"
          : "text-[color:var(--text-muted)] hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)]"
      }`}
    >
      {icon}
      <span>{label}</span>
    </button>
  );
}

export function VisibilityBadge({ visibility }: { visibility: SocialPost["visibility"] }) {
  const isPublic = visibility === "public" || visibility === "tenant";
  return (
    <span className="inline-flex items-center gap-1 align-middle">
      {isPublic ? <Globe className="h-3 w-3" /> : <Lock className="h-3 w-3" />}
      <span className="text-[10px] capitalize">{visibility.replace(/_/g, " ")}</span>
    </span>
  );
}

function formatTimestamp(value?: string | null): string {
  if (!value) return "";
  const ts = new Date(value);
  if (Number.isNaN(ts.getTime())) return "";
  const now = Date.now();
  const diffMs = now - ts.getTime();
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return ts.toLocaleDateString();
}

function avatarColor(name: string): string {
  const hue = [...name].reduce((acc, ch) => acc + ch.charCodeAt(0), 0) % 360;
  return `hsl(${hue}, 55%, 45%)`;
}
