"use client";

import { useState } from "react";
import Link from "next/link";
import { ChevronRight, Loader2, Lock, Plus, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useJoinGroup, useSocialGroups, type SocialGroup } from "@/hooks/queries/useSimbaSocial";

/** Wellness-social groups — supportive, consent-governed communities of practice/interest. */
export default function SocialGroupsPage() {
  const [mine, setMine] = useState(false);
  const groupsQ = useSocialGroups(mine);
  const join = useJoinGroup();
  const groups: SocialGroup[] = groupsQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Groups"
        subtitle="Join a supportive wellness group — walking clubs, condition support, programme cohorts."
        icon={<Users className="h-6 w-6" />}
      >
        <div className="mb-4 flex items-center justify-between">
          <div className="inline-flex rounded-lg border border-border bg-card p-1 text-sm">
            <button
              className={`rounded-md px-3 py-1 ${!mine ? "bg-primary text-primary-foreground" : ""}`}
              onClick={() => setMine(false)}
            >
              All groups
            </button>
            <button
              className={`rounded-md px-3 py-1 ${mine ? "bg-primary text-primary-foreground" : ""}`}
              onClick={() => setMine(true)}
            >
              My groups
            </button>
          </div>
          <Link
            href="/wellness/social/groups/new"
            className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground"
          >
            <Plus className="h-4 w-4" /> New group
          </Link>
        </div>

        {groupsQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading groups…
          </div>
        ) : groupsQ.isError ? (
          <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
            Couldn&apos;t load groups. Please try again.
          </div>
        ) : groups.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-8 text-center text-muted-foreground">
            {mine ? "You haven't joined any groups yet." : "No groups yet — be the first to start one."}
          </div>
        ) : (
          <ul className="space-y-2">
            {groups.map((g) => (
              <li key={g.groupId} className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-center justify-between gap-3">
                  <Link href={`/wellness/social/groups/${g.groupId}`} className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{g.name}</span>
                      {g.sensitiveFlag && (
                        <Lock className="h-3.5 w-3.5 text-amber-600" aria-label="Sensitive group" />
                      )}
                    </div>
                    {g.description && (
                      <p className="truncate text-sm text-muted-foreground">{g.description}</p>
                    )}
                    <p className="mt-1 text-xs text-muted-foreground">
                      {g.memberCount} members · {g.visibility.toLowerCase()} · {g.joinPolicy.toLowerCase()}
                    </p>
                  </Link>
                  <div className="flex shrink-0 items-center gap-2">
                    {!mine && (
                      <button
                        onClick={() => join.mutate({ groupId: g.groupId })}
                        disabled={join.isPending}
                        className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent"
                      >
                        {g.joinPolicy === "REQUEST" ? "Request" : "Join"}
                      </button>
                    )}
                    <ChevronRight className="h-4 w-4 text-muted-foreground" />
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
