"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { Loader2, Megaphone, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useApproveMember,
  useJoinGroup,
  useLeaveGroup,
  usePublishAnnouncement,
  useSetMemberRole,
  useSocialGroup,
  type SocialGroupMember,
} from "@/hooks/queries/useSimbaSocial";

const OFFICIAL_ROLES = ["OWNER", "ADMIN", "MODERATOR", "VERIFIED_PROVIDER", "PROGRAMME_OFFICER"];
const ASSIGNABLE_ROLES = ["MEMBER", "MODERATOR", "VERIFIED_PROVIDER", "PROGRAMME_OFFICER", "VIEWER", "ADMIN"];

/** Group detail — members, join/leave, and (for officials) approve + role + announcements. */
export default function SocialGroupDetailPage() {
  const params = useParams<{ id: string }>();
  const groupId = params?.id;
  const cpid = useAuthStore((s) => s.user?.id ?? null);
  const groupQ = useSocialGroup(groupId);
  const join = useJoinGroup();
  const leave = useLeaveGroup();
  const approve = useApproveMember();
  const setRole = useSetMemberRole();
  const announce = usePublishAnnouncement();
  const [announcement, setAnnouncement] = useState("");
  const [error, setError] = useState<string | null>(null);

  const group = groupQ.data?.data;
  const members: SocialGroupMember[] = groupQ.data?.members ?? [];
  const me = members.find((m) => m.personCpid === cpid);
  const isMember = me?.status === "ACTIVE";
  const isOfficial = !!me && OFFICIAL_ROLES.includes(me.role);

  function publish() {
    if (!groupId || !announcement.trim()) return;
    setError(null);
    announce.mutate(
      { groupId, body: announcement.trim() },
      { onSuccess: () => setAnnouncement(""), onError: (e) => setError(e.message) },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title={group?.name ?? "Group"}
        subtitle={group?.description ?? "Wellness-social group"}
        icon={<Users className="h-6 w-6" />}
      >
        {groupQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading…
          </div>
        ) : !group ? (
          <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
            Group not found.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center gap-3">
              <span className="text-sm text-muted-foreground">
                {group.memberCount} members · {group.visibility.toLowerCase()}
              </span>
              {isMember ? (
                <button
                  onClick={() => leave.mutate({ groupId: groupId! })}
                  className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent"
                >
                  Leave
                </button>
              ) : (
                <button
                  onClick={() => join.mutate({ groupId: groupId! })}
                  className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground"
                >
                  {group.joinPolicy === "REQUEST" ? "Request to join" : "Join"}
                </button>
              )}
            </div>

            {isOfficial && (
              <section className="rounded-lg border border-border bg-card p-4">
                <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold">
                  <Megaphone className="h-4 w-4" /> Publish official announcement
                </h3>
                <textarea
                  value={announcement}
                  onChange={(e) => setAnnouncement(e.target.value)}
                  rows={2}
                  placeholder="Share news with the group (no clinical values)…"
                  className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                />
                {error && <p className="mt-1 text-sm text-destructive">{error}</p>}
                <button
                  onClick={publish}
                  disabled={announce.isPending || !announcement.trim()}
                  className="mt-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
                >
                  Publish
                </button>
              </section>
            )}

            <section>
              <h3 className="mb-2 text-sm font-semibold">Members</h3>
              <ul className="space-y-1">
                {members.map((m) => (
                  <li
                    key={m.membershipId}
                    className="flex items-center justify-between rounded-lg border border-border bg-card px-3 py-2 text-sm"
                  >
                    <div className="min-w-0">
                      <span className="font-mono text-xs text-muted-foreground">{m.personCpid}</span>
                      <span className="ml-2 rounded bg-accent px-1.5 py-0.5 text-xs">{m.role}</span>
                      {m.status !== "ACTIVE" && (
                        <span className="ml-2 text-xs text-amber-600">{m.status}</span>
                      )}
                    </div>
                    {isOfficial && (me?.role === "OWNER" || me?.role === "ADMIN") && (
                      <div className="flex items-center gap-2">
                        {m.status === "REQUESTED" && (
                          <button
                            onClick={() => approve.mutate({ groupId: groupId!, memberCpid: m.personCpid })}
                            className="rounded border border-border px-2 py-1 text-xs hover:bg-accent"
                          >
                            Approve
                          </button>
                        )}
                        <select
                          value={m.role}
                          onChange={(e) =>
                            setRole.mutate({
                              groupId: groupId!,
                              memberCpid: m.personCpid,
                              role: e.target.value,
                            })
                          }
                          className="rounded border border-border bg-background px-1 py-0.5 text-xs"
                        >
                          {ASSIGNABLE_ROLES.map((r) => (
                            <option key={r} value={r}>
                              {r}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
