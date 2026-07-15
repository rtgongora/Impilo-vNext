"use client";

import { useState } from "react";
import {
  AlertTriangle,
  Loader2,
  Megaphone,
  MessageSquare,
  Send,
  ShieldAlert,
  TrendingUp,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreatePost,
  useModerationAction,
  useModerationInbox,
  useProgrammeEngagement,
  usePublishAnnouncement,
  useReportContent,
  useSocialGroups,
} from "@/hooks/queries/useSimbaSocial";

const TABS = [
  ["official", "Official post", <Send key="i" className="h-4 w-4" />],
  ["announcement", "Announcement", <Megaphone key="i" className="h-4 w-4" />],
  ["moderation", "Moderation", <ShieldAlert key="i" className="h-4 w-4" />],
  ["engagement", "Engagement", <TrendingUp key="i" className="h-4 w-4" />],
  ["flag", "Flag to PCT", <AlertTriangle key="i" className="h-4 w-4" />],
] as const;

type TabKey = (typeof TABS)[number][0];

/** Provider wellness-social workbench — official posts, announcements, moderation, engagement, safety flags. */
export default function ProviderWellnessSocialWorkbenchPage() {
  const [tab, setTab] = useState<TabKey>("official");

  return (
    <AppLayout>
      <PageShell
        title="Wellness-social workbench"
        subtitle="Publish official guidance, moderate community content and monitor programme engagement."
        icon={<MessageSquare className="h-6 w-6" />}
      >
        <div className="mx-auto max-w-3xl space-y-6">
          <div className="flex flex-wrap gap-2">
            {TABS.map(([key, label, icon]) => (
              <button
                key={key}
                type="button"
                onClick={() => setTab(key)}
                className={`inline-flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium ${
                  tab === key ? "bg-emerald-600 text-white" : "bg-slate-100 text-slate-600"
                }`}
              >
                {icon}
                {label}
              </button>
            ))}
          </div>

          {tab === "official" && <OfficialPostPanel />}
          {tab === "announcement" && <AnnouncementPanel />}
          {tab === "moderation" && <ModerationPanel />}
          {tab === "engagement" && <EngagementPanel />}
          {tab === "flag" && <FlagToPctPanel />}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function GroupSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const groupsQ = useSocialGroups(true);
  const groups = groupsQ.data?.data ?? [];
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
    >
      <option value="">Select a group…</option>
      {groups.map((g) => (
        <option key={g.groupId} value={g.groupId}>
          {g.name}
        </option>
      ))}
    </select>
  );
}

function OfficialPostPanel() {
  const createPost = useCreatePost();
  const [groupId, setGroupId] = useState("");
  const [body, setBody] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-slate-700">Publish an official post to a group</h2>
      <GroupSelect value={groupId} onChange={setGroupId} />
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        rows={4}
        placeholder="Share evidence-based wellness guidance…"
        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
      />
      <button
        type="button"
        disabled={!groupId || !body.trim() || createPost.isPending}
        onClick={() =>
          createPost.mutate(
            { body, group_id: groupId, visibility: "GROUP" },
            {
              onSuccess: () => {
                setBody("");
                setMsg("Official post published.");
              },
              onError: (e) => setMsg(e.message),
            },
          )
        }
        className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {createPost.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
        Publish
      </button>
      {msg && <p className="text-xs text-slate-500">{msg}</p>}
    </section>
  );
}

function AnnouncementPanel() {
  const publish = usePublishAnnouncement();
  const [groupId, setGroupId] = useState("");
  const [body, setBody] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-slate-700">Publish a pinned group announcement</h2>
      <GroupSelect value={groupId} onChange={setGroupId} />
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        rows={3}
        placeholder="Announcement to all group members…"
        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
      />
      <button
        type="button"
        disabled={!groupId || !body.trim() || publish.isPending}
        onClick={() =>
          publish.mutate(
            { groupId, body },
            {
              onSuccess: () => {
                setBody("");
                setMsg("Announcement published.");
              },
              onError: (e) => setMsg(e.message),
            },
          )
        }
        className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {publish.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Megaphone className="h-4 w-4" />}
        Announce
      </button>
      {msg && <p className="text-xs text-slate-500">{msg}</p>}
    </section>
  );
}

function ModerationPanel() {
  const inboxQ = useModerationInbox("OPEN");
  const action = useModerationAction();
  const reports = inboxQ.data?.data ?? [];

  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-slate-700">Moderation inbox</h2>
        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
          {inboxQ.data?.backlog ?? reports.length} open
        </span>
      </div>

      {inboxQ.isLoading && (
        <div className="flex items-center justify-center py-8 text-slate-400">
          <Loader2 className="h-5 w-5 animate-spin" />
        </div>
      )}

      {!inboxQ.isLoading && reports.length === 0 && (
        <p className="py-6 text-center text-sm text-slate-400">Nothing awaiting moderation.</p>
      )}

      <ul className="space-y-3">
        {reports.map((r) => (
          <li key={r.reportId} className="rounded-lg border border-slate-200 p-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-600">
                {r.subjectType} · {r.reason}
              </span>
              {r.careLinkageId && (
                <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700">
                  Safety escalated
                </span>
              )}
            </div>
            {r.detail && <p className="mt-1 text-sm text-slate-600">{r.detail}</p>}
            <div className="mt-2 flex flex-wrap gap-2">
              {["HIDDEN", "REMOVED", "REJECTED", "ESCALATED"].map((state) => (
                <button
                  key={state}
                  type="button"
                  disabled={action.isPending}
                  onClick={() => action.mutate({ reportId: r.reportId, action_state: state })}
                  className="rounded-md border border-slate-200 px-2.5 py-1 text-xs text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                >
                  {state}
                </button>
              ))}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

function EngagementPanel() {
  const q = useProgrammeEngagement();
  const d = q.data?.data;

  const tiles: [string, number | undefined, string?][] = [
    ["Groups", d?.groups],
    ["Communities", d?.communities],
    ["Active members", d?.active_members],
    ["Posts", d?.posts],
    ["Official posts", d?.official_posts],
    ["Challenge enrolment", d?.challenge_enrolment],
    ["Challenge completion", d?.challenge_completion_pct, "%"],
    ["Reported content", d?.reported_content],
    ["Moderation backlog", d?.moderation_backlog],
    ["Safety escalations", d?.safety_escalations],
  ];

  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-slate-700">Programme engagement (aggregate)</h2>
      {q.isLoading && (
        <div className="flex items-center justify-center py-8 text-slate-400">
          <Loader2 className="h-5 w-5 animate-spin" />
        </div>
      )}
      {!q.isLoading && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {tiles.map(([label, value, suffix]) => (
            <div key={label} className="rounded-lg border border-slate-100 bg-slate-50 p-3">
              <div className="text-xs text-slate-500">{label}</div>
              <div className="text-lg font-semibold text-slate-800">
                {Number(value ?? 0).toLocaleString()}
                {suffix ?? ""}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function FlagToPctPanel() {
  const report = useReportContent();
  const [subjectType, setSubjectType] = useState("POST");
  const [subjectId, setSubjectId] = useState("");
  const [reason, setReason] = useState("SELF_HARM");
  const [detail, setDetail] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  return (
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-slate-700">Flag content for clinical safety review</h2>
      <p className="text-xs text-slate-500">
        Self-harm, abuse and safeguarding reports build a persisted care linkage and route to PCT.
      </p>
      <div className="grid grid-cols-2 gap-2">
        <select
          value={subjectType}
          onChange={(e) => setSubjectType(e.target.value)}
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="POST">Post</option>
          <option value="COMMENT">Comment</option>
          <option value="REEL">Reel</option>
        </select>
        <select
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
        >
          <option value="SELF_HARM">Self-harm</option>
          <option value="ABUSE">Abuse</option>
          <option value="HARMFUL_ADVICE">Harmful advice</option>
          <option value="MEDICAL_MISINFO">Medical misinformation</option>
        </select>
      </div>
      <input
        value={subjectId}
        onChange={(e) => setSubjectId(e.target.value)}
        placeholder="Subject ID (post/comment/reel UUID)"
        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
      />
      <textarea
        value={detail}
        onChange={(e) => setDetail(e.target.value)}
        rows={2}
        placeholder="Context for the safety team…"
        className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
      />
      <button
        type="button"
        disabled={!subjectId.trim() || report.isPending}
        onClick={() =>
          report.mutate(
            { subject_type: subjectType, subject_id: subjectId, reason, detail },
            {
              onSuccess: (r) => {
                setSubjectId("");
                setDetail("");
                setMsg(r?.escalated ? "Flagged and routed to PCT for clinical review." : "Flagged for moderation.");
              },
              onError: (e) => setMsg(e.message),
            },
          )
        }
        className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
      >
        {report.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <AlertTriangle className="h-4 w-4" />}
        Flag for review
      </button>
      {msg && <p className="text-xs text-slate-500">{msg}</p>}
    </section>
  );
}
