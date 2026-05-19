"use client";

/**
 * TimelineComposer — Status/post composer at the top of the timeline.
 * Supports text, announcement, question, event, poll, learning, and health
 * promotion posts; audience and visibility selection; save as draft;
 * scheduled publishing; and Nompilo assist (improve, simplify, translate,
 * suggest tags, safety check).
 *
 * Backed by useCreatePost / useNompiloComposer hooks against the BFF.
 */

import { useMemo, useState } from "react";
import {
  Calendar,
  FileText,
  Globe,
  HelpCircle,
  Lock,
  Megaphone,
  MessagesSquare,
  Sparkles,
  Tag,
  Wand2,
  X,
} from "lucide-react";
import {
  type CreatePostInput,
  type SocialPostKind,
  type SocialVisibility,
  type SocialCommunity,
  type SocialGroup,
  type SocialPage,
  useCreatePost,
  useNompiloComposer,
  type ComposerOp,
} from "@/hooks/queries/useSocial";

interface Props {
  /** Pre-selected community/group/page to post into. */
  defaultScope?: { communityId?: string; groupId?: string; pageId?: string };
  /** Hints rendered in the audience selector. */
  communities?: SocialCommunity[];
  groups?: SocialGroup[];
  pages?: SocialPage[];
  /** Whether the current actor can publish official announcements (facility admins, public health officers). */
  canPublishAnnouncements?: boolean;
  /** Optional callback fired after a successful create. */
  onCreated?: () => void;
}

const POST_KINDS: { kind: SocialPostKind; label: string; description: string; icon: typeof FileText }[] = [
  { kind: "text", label: "Update", description: "Share an update with your network", icon: MessagesSquare },
  { kind: "question", label: "Question", description: "Ask the community", icon: HelpCircle },
  { kind: "event", label: "Event", description: "Outreach, webinar, training", icon: Calendar },
  { kind: "learning", label: "Learning", description: "Share a resource", icon: FileText },
  { kind: "health_promotion", label: "Health promotion", description: "Public-friendly health message", icon: Megaphone },
  { kind: "announcement", label: "Announcement", description: "Official update", icon: Megaphone },
];

const VISIBILITY_OPTIONS: { value: SocialVisibility; label: string; description: string; icon: typeof Globe }[] = [
  { value: "public", label: "Public", description: "Anyone on the platform", icon: Globe },
  { value: "tenant", label: "Tenant", description: "Everyone in your tenant", icon: Globe },
  { value: "facility", label: "Facility", description: "Linked facility members", icon: Lock },
  { value: "community", label: "Community", description: "Members of the selected community", icon: Lock },
  { value: "group", label: "Group", description: "Members of the selected group", icon: Lock },
  { value: "page_followers", label: "Page followers", description: "Followers of the selected page", icon: Lock },
  { value: "role_only", label: "Role only", description: "Only my role/peers", icon: Lock },
  { value: "private", label: "Only me", description: "Draft visibility", icon: Lock },
];

export function TimelineComposer({
  defaultScope,
  communities = [],
  groups = [],
  pages = [],
  canPublishAnnouncements = false,
  onCreated,
}: Props) {
  const [expanded, setExpanded] = useState(false);
  const [kind, setKind] = useState<SocialPostKind>("text");
  const [body, setBody] = useState("");
  const [title, setTitle] = useState("");
  const [visibility, setVisibility] = useState<SocialVisibility>("public");
  const [topics, setTopics] = useState<string[]>([]);
  const [topicDraft, setTopicDraft] = useState("");
  const [communityId, setCommunityId] = useState<string | "">(defaultScope?.communityId ?? "");
  const [groupId, setGroupId] = useState<string | "">(defaultScope?.groupId ?? "");
  const [pageId, setPageId] = useState<string | "">(defaultScope?.pageId ?? "");
  const [showNompiloPanel, setShowNompiloPanel] = useState(false);
  const [nompiloHint, setNompiloHint] = useState<string>("");

  const createPost = useCreatePost();
  const assist = useNompiloComposer();

  const visibilityOptions = useMemo(
    () => (canPublishAnnouncements ? VISIBILITY_OPTIONS : VISIBILITY_OPTIONS.filter((v) => v.value !== "tenant")),
    [canPublishAnnouncements],
  );

  const addTopic = () => {
    const t = topicDraft.trim().replace(/^#/, "");
    if (!t) return;
    if (topics.includes(t)) return;
    setTopics((prev) => [...prev, t].slice(0, 8));
    setTopicDraft("");
  };

  const removeTopic = (t: string) => setTopics((prev) => prev.filter((x) => x !== t));

  const reset = () => {
    setKind("text");
    setBody("");
    setTitle("");
    setVisibility("public");
    setTopics([]);
    setTopicDraft("");
    setCommunityId(defaultScope?.communityId ?? "");
    setGroupId(defaultScope?.groupId ?? "");
    setPageId(defaultScope?.pageId ?? "");
    setExpanded(false);
    setShowNompiloPanel(false);
    setNompiloHint("");
  };

  const handleSubmit = async (asDraft = false) => {
    if (!body.trim()) return;
    const input: CreatePostInput = {
      kind,
      body: body.trim(),
      title: title.trim() || undefined,
      visibility,
      topics,
      scope: {
        communityId: communityId || undefined,
        groupId: groupId || undefined,
        pageId: pageId || undefined,
      },
      saveAsDraft: asDraft,
      source: "experience-web",
    };
    try {
      await createPost.mutateAsync(input);
      onCreated?.();
      reset();
    } catch (err) {
      console.error("Failed to create post", err);
    }
  };

  const runAssist = async (op: ComposerOp, language?: string) => {
    if (!body.trim()) {
      setNompiloHint("Write something first, then ask Nompilo to help.");
      return;
    }
    setNompiloHint("Nompilo is thinking…");
    try {
      const res = await assist.mutateAsync({ op, content: body, language, audience: visibility });
      const resultText = extractText(res.result);
      if (op === "suggest_tags") {
        const suggested = Array.isArray(res.result) ? (res.result as string[]) : [];
        const next = Array.from(new Set([...topics, ...suggested.map((s) => s.replace(/^#/, ""))])).slice(0, 8);
        setTopics(next);
        setNompiloHint(`Added ${suggested.length} suggested tag${suggested.length === 1 ? "" : "s"}.`);
      } else if (op === "safety_check") {
        const flagged = (res.result as { flagged?: boolean })?.flagged;
        setNompiloHint(flagged ? "Nompilo flagged this content. Review before posting." : "Nompilo found no issues.");
      } else if (resultText) {
        setBody(resultText);
        setNompiloHint(`${labelForOp(op)} applied${res.fallbackUsed ? " (offline mode)" : ""}.`);
      } else {
        setNompiloHint("Nompilo couldn't update the draft.");
      }
    } catch (err) {
      console.error(err);
      setNompiloHint("Nompilo assist is unavailable right now.");
    }
  };

  return (
    <div className="impilo-surface-card p-4">
      {!expanded ? (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="flex w-full items-center gap-3 rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] px-4 py-3 text-left text-sm text-[color:var(--text-muted)] hover:border-[color:var(--primary-muted)] hover:bg-[color:var(--primary-soft)]/40"
        >
          <Sparkles className="h-4 w-4 text-[color:var(--primary)]" />
          What would you like to share with your community?
        </button>
      ) : (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center gap-2">
            {POST_KINDS.filter((k) => k.kind !== "announcement" || canPublishAnnouncements).map(({ kind: k, label, icon: Icon }) => (
              <button
                key={k}
                type="button"
                onClick={() => setKind(k)}
                className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs transition-colors ${
                  kind === k
                    ? "border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] text-[color:var(--primary-hover)]"
                    : "border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] text-[color:var(--text-secondary)] hover:border-[color:var(--primary-muted)]"
                }`}
              >
                <Icon className="h-3.5 w-3.5" />
                {label}
              </button>
            ))}
          </div>

          {(kind === "announcement" || kind === "event" || kind === "learning") && (
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Add a headline"
              className="w-full rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 text-sm focus:border-[color:var(--primary)] focus:outline-none"
            />
          )}

          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder={placeholderFor(kind)}
            rows={4}
            className="w-full resize-y rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 text-sm focus:border-[color:var(--primary)] focus:outline-none"
          />

          {/* Topics */}
          <div className="flex flex-wrap items-center gap-1.5">
            {topics.map((t) => (
              <span
                key={t}
                className="inline-flex items-center gap-1 rounded-full border border-[color:var(--primary-muted)] bg-[color:var(--primary-soft)] px-2 py-0.5 text-[11px] text-[color:var(--primary-hover)]"
              >
                #{t}
                <button onClick={() => removeTopic(t)} aria-label={`Remove ${t}`}>
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
            <div className="inline-flex items-center gap-1 rounded-full border border-dashed border-[color:var(--border-soft)] px-2 py-0.5">
              <Tag className="h-3 w-3 text-[color:var(--text-muted)]" />
              <input
                value={topicDraft}
                onChange={(e) => setTopicDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === ",") {
                    e.preventDefault();
                    addTopic();
                  }
                }}
                placeholder="Add topic"
                className="w-24 bg-transparent text-[11px] focus:outline-none"
              />
            </div>
          </div>

          {/* Audience + visibility */}
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value as SocialVisibility)}
              className="rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 text-sm focus:border-[color:var(--primary)] focus:outline-none"
            >
              {visibilityOptions.map((v) => (
                <option key={v.value} value={v.value}>
                  {v.label} — {v.description}
                </option>
              ))}
            </select>
            {communities.length > 0 && (
              <select
                value={communityId}
                onChange={(e) => setCommunityId(e.target.value)}
                className="rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 text-sm focus:border-[color:var(--primary)] focus:outline-none"
              >
                <option value="">No community</option>
                {communities.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            )}
            {groups.length > 0 && (
              <select
                value={groupId}
                onChange={(e) => setGroupId(e.target.value)}
                className="rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 text-sm focus:border-[color:var(--primary)] focus:outline-none"
              >
                <option value="">No group</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.name}</option>
                ))}
              </select>
            )}
            {pages.length > 0 && (
              <select
                value={pageId}
                onChange={(e) => setPageId(e.target.value)}
                className="rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 text-sm focus:border-[color:var(--primary)] focus:outline-none"
              >
                <option value="">No page</option>
                {pages.map((p) => (
                  <option key={p.id} value={p.id}>{p.name}</option>
                ))}
              </select>
            )}
          </div>

          {/* Nompilo */}
          <div className="rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface-soft)] p-3">
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => setShowNompiloPanel((s) => !s)}
                className="inline-flex items-center gap-1.5 rounded-full bg-[color:var(--primary)] px-3 py-1 text-xs font-medium text-white"
              >
                <Wand2 className="h-3.5 w-3.5" />
                Ask Nompilo
              </button>
              {showNompiloPanel && (
                <div className="flex flex-wrap gap-1.5">
                  <AssistChip onClick={() => runAssist("improve")} disabled={assist.isPending}>Improve</AssistChip>
                  <AssistChip onClick={() => runAssist("simplify")} disabled={assist.isPending}>Simplify</AssistChip>
                  <AssistChip onClick={() => runAssist("translate", "sn")} disabled={assist.isPending}>Translate · sn</AssistChip>
                  <AssistChip onClick={() => runAssist("translate", "nd")} disabled={assist.isPending}>Translate · nd</AssistChip>
                  <AssistChip onClick={() => runAssist("suggest_tags")} disabled={assist.isPending}>Suggest tags</AssistChip>
                  <AssistChip onClick={() => runAssist("safety_check")} disabled={assist.isPending}>Safety check</AssistChip>
                  {canPublishAnnouncements && (
                    <AssistChip onClick={() => runAssist("draft_announcement")} disabled={assist.isPending}>
                      Draft announcement
                    </AssistChip>
                  )}
                </div>
              )}
            </div>
            {nompiloHint && <p className="mt-2 text-[11px] text-[color:var(--text-muted)]">{nompiloHint}</p>}
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={reset}
              className="rounded-lg px-3 py-2 text-xs font-medium text-[color:var(--text-muted)] hover:text-[color:var(--text-primary)]"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={() => void handleSubmit(true)}
              disabled={!body.trim() || createPost.isPending}
              className="rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2 text-xs font-medium text-[color:var(--text-secondary)] hover:border-[color:var(--primary-muted)] disabled:opacity-50"
            >
              Save draft
            </button>
            <button
              type="button"
              onClick={() => void handleSubmit(false)}
              disabled={!body.trim() || createPost.isPending}
              className="rounded-lg bg-[color:var(--primary)] px-4 py-2 text-xs font-semibold text-white hover:bg-[color:var(--primary-hover)] disabled:opacity-50"
            >
              {createPost.isPending ? "Posting…" : "Post"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function AssistChip({ onClick, disabled, children }: { onClick: () => void; disabled?: boolean; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-2.5 py-1 text-[11px] text-[color:var(--text-secondary)] hover:border-[color:var(--primary-muted)] hover:text-[color:var(--primary-hover)] disabled:opacity-50"
    >
      {children}
    </button>
  );
}

function placeholderFor(kind: SocialPostKind): string {
  switch (kind) {
    case "question":
      return "Ask the community a question…";
    case "event":
      return "Describe the event: when, where, who is invited…";
    case "learning":
      return "Share a resource, link, or summary…";
    case "health_promotion":
      return "Write a clear, friendly health message…";
    case "announcement":
      return "Write your official announcement…";
    default:
      return "Share an update, story, or insight…";
  }
}

function labelForOp(op: ComposerOp): string {
  switch (op) {
    case "improve": return "Improved wording";
    case "simplify": return "Simplified";
    case "translate": return "Translated";
    case "draft_announcement": return "Drafted announcement";
    case "summarise_thread": return "Summary";
    default: return "Updated";
  }
}

function extractText(result: unknown): string | null {
  if (typeof result === "string") return result;
  if (result && typeof result === "object") {
    const r = result as Record<string, unknown>;
    if (typeof r.text === "string") return r.text;
    if (typeof r.content === "string") return r.content;
    if (typeof r.result === "string") return r.result;
  }
  return null;
}
