"use client";

/**
 * KhulumaHomeBody (Phase F12) — everything KhulumaHome renders, minus its own AppLayout/
 * PageShell wrapper, so the panel can be embedded elsewhere (a Work Home section, a compact
 * dashboard card) without double chrome. KhulumaHome itself is now a thin wrapper: AppLayout +
 * PageShell + this component — see that file for the full-page route.
 */

import { useMemo } from "react";
import Link from "next/link";
import {
  MessageSquarePlus,
  Phone,
  Video,
  CalendarPlus,
  Radio,
  Megaphone,
  LifeBuoy,
  Bell,
  Inbox,
  Users,
  Hash,
  AlertTriangle,
  Send,
  MessagesSquare,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { matchesRequiredRole } from "@/lib/auth/role-groups";
import { useCommsSummary, useCommsInbox, useIncomingCalls, useUpdatePresence } from "@/hooks/queries/useComms";
import { useNotifications, useMarkNotificationRead } from "@/hooks/queries/useNotifications";
import { useDiscoverChannels } from "@/hooks/queries/useKhulumaChannels";
import { useMyCaregivingLinkages, useMyCaregivers } from "@/hooks/queries/useCaregiverLinkage";
import { useEscalationQueue, useOnCall, useDeliveryAdapters, useKhulumaSessions, useEscalationAction } from "@/hooks/queries/useKhulumaOps";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { KhulumaSubNav } from "./KhulumaSubNav";
import { KhulumaAttentionStrip, type AttentionChip } from "./KhulumaAttentionStrip";
import { KhulumaActionGrid, type KhulumaActionItem } from "./KhulumaActionGrid";
import { KhulumaSection } from "./KhulumaSection";
import { KhulumaEmptyState } from "./KhulumaEmptyState";
import { rows, asRecord, readStr, readNum, formatWhen, resolvePersona } from "./util";

const PRESENCE = ["ONLINE", "AWAY", "BUSY", "DND", "OFFLINE"];

function isUnreadNotif(n: Record<string, unknown>): boolean {
  const s = readStr(n, "status", "state").toUpperCase();
  const read = n.read === true || n.readAt || s === "READ" || s === "ACKNOWLEDGED";
  return !read;
}

export function KhulumaHomeBody() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const persona = resolvePersona((k) => matchesRequiredRole(hasRole, k));
  const isWork = persona === "work";

  const summaryQ = useCommsSummary();
  const inboxQ = useCommsInbox();
  const notifQ = useNotifications();
  const incomingQ = useIncomingCalls(true);
  const channelsQ = useDiscoverChannels();
  const presence = useUpdatePresence();
  const markRead = useMarkNotificationRead();

  // Relationship + ops sources are persona-scoped so we never call work endpoints as a citizen.
  const caregiversQ = useMyCaregivers();
  const linkagesQ = useMyCaregivingLinkages();
  const escalationsQ = useEscalationQueue(isWork);
  const onCallQ = useOnCall(isWork);
  const adaptersQ = useDeliveryAdapters(isWork);
  const sessionsQ = useKhulumaSessions();
  const escalationAction = useEscalationAction();

  const conversations = rows(inboxQ.data);
  const notifications = rows(notifQ.data);
  const incoming = rows(incomingQ.data);
  const channels = rows(channelsQ.data);
  const caregivers = rows(caregiversQ.data);
  const linkages = rows(linkagesQ.data);
  const escalations = rows(escalationsQ.data);
  const onCall = rows(onCallQ.data);
  const adapters = rows(adaptersQ.data);

  const unreadMessages = readNum(asRecord(asRecord(summaryQ.data).messages), "unreadCount");
  const actionableUpdates = useMemo(() => notifications.filter(isUnreadNotif), [notifications]);
  const sessionsEnv = asRecord(asRecord(sessionsQ.data).data);
  const sessions = Array.isArray(sessionsEnv.items) ? (sessionsEnv.items as unknown[]).map(asRecord) : [];

  const chips: AttentionChip[] = [
    { label: "unread messages", value: unreadMessages, href: "/khuluma/inbox", icon: <Inbox className="h-4 w-4" />, tone: "warning" },
    { label: "updates need action", value: actionableUpdates.length, href: "/khuluma/updates", icon: <Bell className="h-4 w-4" />, tone: "warning" },
    { label: "calls waiting", value: incoming.length, href: "/khuluma/calls", icon: <Phone className="h-4 w-4" />, tone: "danger" },
    { label: "upcoming sessions", value: sessions.length, href: "/khuluma/meetings", icon: <CalendarPlus className="h-4 w-4" />, tone: "neutral" },
    ...(isWork ? [{ label: "escalations open", value: escalations.length, href: "/khuluma/updates", icon: <AlertTriangle className="h-4 w-4" />, tone: "danger" as const }] : []),
  ];

  const actions: KhulumaActionItem[] = [
    { title: "Start conversation", href: "/khuluma/inbox?compose=1", icon: <MessageSquarePlus className="h-4 w-4" />, access: "open" },
    { title: "Audio call", href: "/khuluma/calls", icon: <Phone className="h-4 w-4" />, access: "open" },
    { title: "Video call", href: "/khuluma/calls", icon: <Video className="h-4 w-4" />, access: "open" },
    { title: "Schedule meeting", href: isWork ? "/khuluma/meetings" : "/live", icon: <CalendarPlus className="h-4 w-4" />, access: "open" },
    { title: "Join Impilo Live", href: "/live", icon: <Radio className="h-4 w-4" />, access: "open" },
    ...(isWork ? [{ title: "Send an update", href: "/khuluma/channels", icon: <Megaphone className="h-4 w-4" />, access: "open" as const }] : []),
    { title: "Give feedback", href: "/khuluma/feedback", icon: <MessagesSquare className="h-4 w-4" />, access: "open" },
    { title: "Contact support", href: "/support", icon: <LifeBuoy className="h-4 w-4" />, access: "open" },
  ];

  return (
    <>
      {/* Presence + attention */}
      <div className="flex flex-wrap items-center justify-between gap-2" data-testid="khuluma-home" data-persona={persona}>
        <div className="text-xs text-muted-foreground">
          {isWork ? "Professional context" : "Personal context"}
        </div>
        <label className="flex items-center gap-2 text-xs text-muted-foreground">
          Presence
          <select
            defaultValue="ONLINE"
            onChange={(e) => presence.mutate({ status: e.target.value })}
            className="rounded-md border border-border bg-background px-2 py-1 text-xs text-foreground"
          >
            {PRESENCE.map((p) => <option key={p} value={p}>{p}</option>)}
          </select>
        </label>
      </div>

      <KhulumaAttentionStrip chips={chips} />
      <KhulumaActionGrid actions={actions} />
      <NompiloContextualGuidance routePath="/khuluma" />
      <KhulumaSubNav />

      <div className="grid gap-4 lg:grid-cols-2">
        {/* Upcoming sessions (BFF composite: booking + telehealth) */}
        <KhulumaSection
          title="What's next"
          icon={<CalendarPlus className="h-4 w-4 text-emerald-700" />}
          isLoading={sessionsQ.isLoading}
          isError={sessionsQ.isError}
          onRetry={() => sessionsQ.refetch()}
          isEmpty={!sessions.length}
          emptyState={
            <KhulumaEmptyState
              title="No upcoming sessions"
              explanation="Your appointments and teleconsultations appear here so you know what's next and can join on time."
              when="Sessions appear once you have a booking or scheduled teleconsultation."
              actions={[{ label: "Find care", href: "/welcome/find-care", variant: "secondary" }]}
            />
          }
        >
          <ul className="divide-y divide-border">
            {sessions.slice(0, 5).map((s, i) => (
              <li key={readStr(s, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                <span className="min-w-0">
                  <span className="block truncate text-foreground">{readStr(s, "title") || "Session"}</span>
                  <span className="block truncate text-xs text-muted-foreground">{readStr(s, "kind")} · {formatWhen(readStr(s, "startAt"))}</span>
                </span>
                {readStr(s, "joinHref") ? (
                  <Link href={readStr(s, "joinHref")} className="shrink-0 rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700">Open</Link>
                ) : null}
              </li>
            ))}
          </ul>
        </KhulumaSection>

        {/* Recent conversations */}
        <KhulumaSection
          title="Recent conversations"
          icon={<Inbox className="h-4 w-4 text-emerald-700" />}
          actions={<Link href="/khuluma/inbox" className="text-xs font-medium text-emerald-700 hover:text-emerald-900">Open inbox →</Link>}
          isLoading={inboxQ.isLoading}
          isError={inboxQ.isError}
          onRetry={() => inboxQ.refetch()}
          isEmpty={!conversations.length}
          emptyState={
            <KhulumaEmptyState
              title="No conversations yet"
              explanation="Conversations appear when you or your care contacts start one — direct messages, group threads, or care coordination."
              when="Start one now, or they arrive when someone reaches you."
              actions={[{ label: "Start a conversation", href: "/khuluma/inbox?compose=1" }]}
              guidance="Ask Nompilo who you can reach and how."
            />
          }
        >
          <ul className="divide-y divide-border">
            {conversations.slice(0, 5).map((c, i) => (
              <li key={readStr(c, "conversationId", "id") || i}>
                <Link href="/khuluma/inbox" className="flex items-center justify-between gap-2 py-2 text-sm hover:text-emerald-800">
                  <span className="min-w-0">
                    <span className="block truncate text-foreground">{readStr(c, "title") || "Conversation"}</span>
                    <span className="block truncate text-xs text-muted-foreground">{readStr(c, "lastMessagePreview") || "—"}</span>
                  </span>
                  {readNum(c, "unreadCount") > 0 ? (
                    <span className="shrink-0 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">{readNum(c, "unreadCount")}</span>
                  ) : null}
                </Link>
              </li>
            ))}
          </ul>
        </KhulumaSection>

        {/* Updates & actions preview */}
        <KhulumaSection
          title="Updates & actions"
          icon={<Bell className="h-4 w-4 text-emerald-700" />}
          actions={<Link href="/khuluma/updates" className="text-xs font-medium text-emerald-700 hover:text-emerald-900">See all →</Link>}
          isLoading={notifQ.isLoading}
          isError={notifQ.isError}
          onRetry={() => notifQ.refetch()}
          isEmpty={!notifications.length}
          emptyState={
            <KhulumaEmptyState
              title="Nothing needs your attention"
              explanation="Referral outcomes, appointment changes, reminders, results and service updates surface here with a next action."
              when="Updates arrive as your care and services progress."
              actions={[{ label: "Explore Khuluma", href: "/khuluma/inbox", variant: "secondary" }]}
            />
          }
        >
          <ul className="divide-y divide-border">
            {notifications.slice(0, 5).map((n, i) => {
              const id = readStr(n, "id", "notificationId");
              return (
                <li key={id || i} className="flex items-start justify-between gap-2 py-2 text-sm">
                  <span className="min-w-0">
                    <span className="block truncate text-foreground">{readStr(n, "title", "subject", "summary") || "Update"}</span>
                    <span className="block truncate text-xs text-muted-foreground">{readStr(n, "body", "message", "preview") || formatWhen(readStr(n, "createdAt", "sentAt"))}</span>
                  </span>
                  {isUnreadNotif(n) && id ? (
                    <button type="button" onClick={() => markRead.mutate(id)} className="shrink-0 rounded-md border border-border px-2 py-0.5 text-[11px] font-medium hover:bg-muted">Mark read</button>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </KhulumaSection>

        {/* Your people (life) OR ops rail (work) */}
        {isWork ? (
          <KhulumaSection
            title="Escalations & on-call"
            icon={<AlertTriangle className="h-4 w-4 text-emerald-700" />}
            isLoading={escalationsQ.isLoading}
            isError={escalationsQ.isError}
            onRetry={() => escalationsQ.refetch()}
            isEmpty={!escalations.length && !onCall.length}
            emptyState={
              <KhulumaEmptyState
                title="No open escalations"
                explanation="Clinical and coordination escalations that need a response appear here, alongside who is on call."
                when="Escalations are raised from care workflows when a response is required."
              />
            }
          >
            <ul className="divide-y divide-border">
              {escalations.slice(0, 4).map((e, i) => {
                const id = readStr(e, "id");
                const st = readStr(e, "status").toUpperCase();
                return (
                  <li key={id || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <span className="min-w-0 flex-1 truncate text-foreground">{readStr(e, "subject", "reason", "title") || "Escalation"}</span>
                    <span className="text-xs text-muted-foreground">{st}</span>
                    {id ? (
                      <span className="flex gap-1">
                        {/OPEN|ASSIGNED/.test(st) ? (
                          <button type="button" disabled={escalationAction.isPending} onClick={() => escalationAction.mutate({ id, action: "accept" })} className="rounded-md border border-border px-2 py-0.5 text-[11px] font-medium hover:bg-muted disabled:opacity-50">Accept</button>
                        ) : null}
                        {st !== "RESOLVED" ? (
                          <button type="button" disabled={escalationAction.isPending} onClick={() => escalationAction.mutate({ id, action: "resolve" })} className="rounded-md bg-emerald-600 px-2 py-0.5 text-[11px] font-medium text-white hover:bg-emerald-700 disabled:opacity-50">Resolve</button>
                        ) : null}
                      </span>
                    ) : null}
                  </li>
                );
              })}
              {onCall.slice(0, 3).map((o, i) => (
                <li key={"oc" + i} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="truncate text-muted-foreground">On call: {readStr(o, "displayName", "actorId") || "—"}</span>
                  <span className="text-xs text-emerald-700">{readStr(o, "dutyStatus", "status") || "ON_CALL"}</span>
                </li>
              ))}
            </ul>
          </KhulumaSection>
        ) : (
          <KhulumaSection
            title="Your people"
            icon={<Users className="h-4 w-4 text-emerald-700" />}
            isLoading={caregiversQ.isLoading || linkagesQ.isLoading}
            isEmpty={!caregivers.length && !linkages.length}
            emptyState={
              <KhulumaEmptyState
                title="No linked people yet"
                explanation="Your caregivers and the people you support appear here so you can reach them. A single 'care team' directory is not yet available in the platform."
                when="Links appear once a caregiver relationship is established."
                actions={[{ label: "Manage caregivers", href: "/citizen/wallet/comms", variant: "secondary" }]}
                guidance="Nompilo can explain who can act on your behalf and how consent works."
              />
            }
          >
            <ul className="divide-y divide-border">
              {[...caregivers, ...linkages].slice(0, 6).map((p, i) => (
                <li key={readStr(p, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="truncate text-foreground">{readStr(p, "displayName", "name", "caregiverName", "subjectName") || "Contact"}</span>
                  <span className="text-xs text-muted-foreground">{readStr(p, "relationship", "role", "status") || "—"}</span>
                </li>
              ))}
            </ul>
          </KhulumaSection>
        )}

        {/* Channels to join */}
        <KhulumaSection
          title="Teams & channels"
          icon={<Hash className="h-4 w-4 text-emerald-700" />}
          actions={<Link href="/khuluma/channels" className="text-xs font-medium text-emerald-700 hover:text-emerald-900">Browse →</Link>}
          isLoading={channelsQ.isLoading}
          isError={channelsQ.isError}
          onRetry={() => channelsQ.refetch()}
          isEmpty={!channels.length}
          emptyState={
            <KhulumaEmptyState
              title="No channels to join"
              explanation="Facility teams, programme channels and communities you can join appear here."
              when="Channels appear as your facility and programmes create them."
              actions={isWork ? [{ label: "Create a channel", href: "/khuluma/channels" }] : undefined}
            />
          }
        >
          <ul className="divide-y divide-border">
            {channels.slice(0, 5).map((c, i) => (
              <li key={readStr(c, "conversationId", "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                <span className="truncate text-foreground">{readStr(c, "title", "name") || "Channel"}</span>
                <span className="text-xs text-muted-foreground">{readStr(c, "scopeType", "type") || "—"}</span>
              </li>
            ))}
          </ul>
        </KhulumaSection>

        {/* Delivery adapters (work, honest configured/pending) */}
        {isWork ? (
          <KhulumaSection
            title="Omnichannel delivery"
            icon={<Send className="h-4 w-4 text-emerald-700" />}
            isLoading={adaptersQ.isLoading}
            isError={adaptersQ.isError}
            onRetry={() => adaptersQ.refetch()}
            isEmpty={!adapters.length}
            emptyState={
              <KhulumaEmptyState
                title="No delivery adapters reported"
                explanation="Native in-app and realtime delivery are always available. External channels (SMS, WhatsApp, email, USSD, IVR) appear here with their real configured/not-configured state."
                when="Adapters appear as the platform enables external channels."
              />
            }
          >
            <ul className="divide-y divide-border">
              {adapters.map((a, i) => (
                <li key={readStr(a, "channel", "name") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="text-foreground">{readStr(a, "channel", "name") || "Adapter"}</span>
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${a.enabled || readStr(a, "status") === "CONFIGURED" ? "bg-green-100 text-green-800" : "bg-amber-50 text-amber-700"}`}>
                    {a.enabled || readStr(a, "status") === "CONFIGURED" ? "Configured" : "Not configured"}
                  </span>
                </li>
              ))}
            </ul>
          </KhulumaSection>
        ) : null}
      </div>
    </>
  );
}
