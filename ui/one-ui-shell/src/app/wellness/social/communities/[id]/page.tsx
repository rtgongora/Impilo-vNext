"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { Globe2, Loader2, Megaphone } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useJoinCommunity,
  usePublishCommunityAnnouncement,
  useSocialCommunity,
  type SocialGroupMember,
} from "@/hooks/queries/useSimbaSocial";

const OFFICIAL_ROLES = ["OWNER", "ADMIN", "MODERATOR", "VERIFIED_PROVIDER", "PROGRAMME_OFFICER"];

/** Community detail — members, join, and (for officials) community announcements. */
export default function SocialCommunityDetailPage() {
  const params = useParams<{ id: string }>();
  const communityId = params?.id;
  const cpid = useAuthStore((s) => s.user?.id ?? null);
  const communityQ = useSocialCommunity(communityId);
  const join = useJoinCommunity();
  const announce = usePublishCommunityAnnouncement();
  const [body, setBody] = useState("");
  const [error, setError] = useState<string | null>(null);

  const community = communityQ.data?.data;
  const members: SocialGroupMember[] = communityQ.data?.members ?? [];
  const me = members.find((m) => m.personCpid === cpid);
  const isMember = me?.status === "ACTIVE";
  const isOfficial = !!me && OFFICIAL_ROLES.includes(me.role);

  function publish() {
    if (!communityId || !body.trim()) return;
    setError(null);
    announce.mutate(
      { communityId, body: body.trim() },
      { onSuccess: () => setBody(""), onError: (e) => setError(e.message) },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title={community?.name ?? "Community"}
        subtitle={community?.description ?? "Wellness-social community"}
        icon={<Globe2 className="h-6 w-6" />}
      >
        {communityQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading…
          </div>
        ) : !community ? (
          <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
            Community not found.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center gap-3">
              <span className="text-sm text-muted-foreground">
                {community.memberCount} members · {community.communityType.toLowerCase()}
              </span>
              {!isMember && (
                <button
                  onClick={() => join.mutate({ communityId: communityId! })}
                  className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground"
                >
                  Join
                </button>
              )}
            </div>

            {isOfficial && (
              <section className="rounded-lg border border-border bg-card p-4">
                <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold">
                  <Megaphone className="h-4 w-4" /> Community announcement
                </h3>
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  rows={2}
                  className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                  placeholder="Post an official community announcement…"
                />
                {error && <p className="mt-1 text-sm text-destructive">{error}</p>}
                <button
                  onClick={publish}
                  disabled={announce.isPending || !body.trim()}
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
                    <span className="font-mono text-xs text-muted-foreground">{m.personCpid}</span>
                    <span className="rounded bg-accent px-1.5 py-0.5 text-xs">{m.role}</span>
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
