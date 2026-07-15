"use client";

import { useState } from "react";
import Link from "next/link";
import { ChevronRight, Globe2, Loader2, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateCommunity,
  useJoinCommunity,
  useSocialCommunities,
  type SocialCommunity,
} from "@/hooks/queries/useSimbaSocial";

/** Wellness-social communities — larger umbrellas (condition / programme / regional) hosting groups. */
export default function SocialCommunitiesPage() {
  const communitiesQ = useSocialCommunities();
  const join = useJoinCommunity();
  const create = useCreateCommunity();
  const [showNew, setShowNew] = useState(false);
  const [name, setName] = useState("");
  const [type, setType] = useState("WELLNESS");
  const [error, setError] = useState<string | null>(null);
  const communities: SocialCommunity[] = communitiesQ.data?.data ?? [];

  function submit() {
    if (!name.trim()) {
      setError("Name required");
      return;
    }
    setError(null);
    create.mutate(
      { name: name.trim(), community_type: type },
      {
        onSuccess: () => {
          setName("");
          setShowNew(false);
        },
        onError: (e) => setError(e.message),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Communities"
        subtitle="Umbrella wellness communities — condition support, programmes, and regional networks."
        icon={<Globe2 className="h-6 w-6" />}
      >
        <div className="mb-4 flex justify-end">
          <button
            onClick={() => setShowNew((v) => !v)}
            className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground"
          >
            <Plus className="h-4 w-4" /> New community
          </button>
        </div>

        {showNew && (
          <div className="mb-4 space-y-2 rounded-lg border border-border bg-card p-4">
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Community name"
              className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            />
            <select
              value={type}
              onChange={(e) => setType(e.target.value)}
              className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            >
              <option value="WELLNESS">Wellness</option>
              <option value="CONDITION">Condition</option>
              <option value="PROGRAMME">Programme</option>
              <option value="REGIONAL">Regional</option>
            </select>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <button
              onClick={submit}
              disabled={create.isPending}
              className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground"
            >
              Create
            </button>
          </div>
        )}

        {communitiesQ.isLoading ? (
          <div className="flex items-center gap-2 p-8 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading…
          </div>
        ) : communities.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-8 text-center text-muted-foreground">
            No communities yet.
          </div>
        ) : (
          <ul className="space-y-2">
            {communities.map((c) => (
              <li key={c.communityId} className="rounded-lg border border-border bg-card p-4">
                <div className="flex items-center justify-between gap-3">
                  <Link href={`/wellness/social/communities/${c.communityId}`} className="min-w-0 flex-1">
                    <span className="font-medium">{c.name}</span>
                    <p className="text-xs text-muted-foreground">
                      {c.communityType.toLowerCase()} · {c.memberCount} members
                    </p>
                  </Link>
                  <div className="flex shrink-0 items-center gap-2">
                    <button
                      onClick={() => join.mutate({ communityId: c.communityId })}
                      className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent"
                    >
                      Join
                    </button>
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
