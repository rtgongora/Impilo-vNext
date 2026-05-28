"use client";

/** /communities — Browse and join health communities. */

import { useMemo, useState } from "react";
import { Search, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useJoinCommunity, useSocialCommunities } from "@/hooks/queries/useSocial";
import { CommunityCard } from "@/components/social/EntityCards";

const CATEGORIES = [
  "All",
  "maternal_health",
  "diabetes",
  "hypertension",
  "hiv_support",
  "youth_wellness",
  "mental_wellness",
  "fitness",
  "nutrition",
  "community_of_practice",
  "training_cohort",
];

export default function CommunitiesPage() {
  const [category, setCategory] = useState("All");
  const [q, setQ] = useState("");
  const communitiesQ = useSocialCommunities(category === "All" ? undefined : category, q || undefined);
  const join = useJoinCommunity();

  const items = useMemo(() => communitiesQ.data ?? [], [communitiesQ.data]);

  return (
    <AppLayout>
      <PageShell
        title="Communities"
        subtitle="Find your people. Wellness circles, support communities, communities of practice, and training cohorts."
        icon={<Users className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--text-muted)]" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search communities…"
              className="w-full rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface)] pl-10 pr-4 py-2.5 text-sm focus:border-[color:var(--primary)] focus:outline-none"
            />
          </div>
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2.5 text-sm focus:border-[color:var(--primary)] focus:outline-none"
          >
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {c === "All" ? "All categories" : c.replace(/_/g, " ")}
              </option>
            ))}
          </select>
        </div>

        {communitiesQ.isLoading && <p className="text-sm text-[color:var(--text-muted)]">Loading communities…</p>}

        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {items.map((c) => (
            <CommunityCard
              key={c.id}
              community={c}
              onJoin={() => join.mutate(c.id)}
              joining={join.isPending && join.variables === c.id}
            />
          ))}
        </div>

        {!communitiesQ.isLoading && items.length === 0 && (
          <div className="impilo-surface-card p-8 text-center">
            <p className="text-sm font-semibold text-[color:var(--text-primary)]">No communities found</p>
            <p className="mt-1 text-xs text-[color:var(--text-muted)]">Try a different category or clear the search.</p>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
