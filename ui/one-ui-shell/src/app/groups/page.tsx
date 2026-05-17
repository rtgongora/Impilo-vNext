"use client";

/** /groups — Browse groups. */

import { useMemo, useState } from "react";
import { Layers, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSocialGroups } from "@/hooks/queries/useSocial";
import { GroupCard } from "@/components/social/EntityCards";

export default function GroupsPage() {
  const [q, setQ] = useState("");
  const groupsQ = useSocialGroups();
  const items = useMemo(
    () => (groupsQ.data ?? []).filter((g) => !q || g.name.toLowerCase().includes(q.toLowerCase())),
    [groupsQ.data, q],
  );

  return (
    <AppLayout>
      <PageShell title="Groups" subtitle="Operational teams, mentorship circles, training cohorts, and outreach groups." icon={<Layers className="h-6 w-6" />}>
        <div className="relative mb-4 max-w-md">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--text-muted)]" />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search groups…"
            className="w-full rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface)] pl-10 pr-4 py-2.5 text-sm focus:border-[color:var(--primary)] focus:outline-none"
          />
        </div>
        {groupsQ.isLoading && <p className="text-sm text-[color:var(--text-muted)]">Loading groups…</p>}
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {items.map((g) => (
            <GroupCard key={g.id} group={g} />
          ))}
        </div>
        {!groupsQ.isLoading && items.length === 0 && (
          <div className="impilo-surface-card p-8 text-center">
            <p className="text-sm font-semibold text-[color:var(--text-primary)]">No groups yet</p>
            <p className="mt-1 text-xs text-[color:var(--text-muted)]">Groups created in your tenant will appear here.</p>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
