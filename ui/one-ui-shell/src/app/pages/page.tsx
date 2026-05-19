"use client";

/** /pages — Browse formal pages (facilities, programmes, campaigns). */

import { useMemo, useState } from "react";
import { Newspaper, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFollowPage, useSocialPages } from "@/hooks/queries/useSocial";
import { PageCard } from "@/components/social/EntityCards";

const KINDS = ["All", "facility", "organisation", "programme", "campaign", "service", "district", "province"];

export default function PagesPage() {
  const [kind, setKind] = useState("All");
  const [q, setQ] = useState("");
  const pagesQ = useSocialPages(kind === "All" ? undefined : kind);
  const follow = useFollowPage();

  const items = useMemo(
    () => (pagesQ.data ?? []).filter((p) => !q || p.name.toLowerCase().includes(q.toLowerCase())),
    [pagesQ.data, q],
  );

  return (
    <AppLayout>
      <PageShell title="Pages" subtitle="Facilities, organisations, programmes, and public health campaigns you can follow." icon={<Newspaper className="h-6 w-6" />}>
        <div className="mb-4 flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--text-muted)]" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search pages…"
              className="w-full rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface)] pl-10 pr-4 py-2.5 text-sm focus:border-[color:var(--primary)] focus:outline-none"
            />
          </div>
          <select
            value={kind}
            onChange={(e) => setKind(e.target.value)}
            className="rounded-xl border border-[color:var(--border-soft)] bg-[color:var(--surface)] px-3 py-2.5 text-sm focus:border-[color:var(--primary)] focus:outline-none"
          >
            {KINDS.map((k) => (
              <option key={k} value={k}>{k === "All" ? "All kinds" : k}</option>
            ))}
          </select>
        </div>

        {pagesQ.isLoading && <p className="text-sm text-[color:var(--text-muted)]">Loading pages…</p>}

        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {items.map((p) => (
            <PageCard
              key={p.id}
              page={p}
              onFollow={() => follow.mutate({ id: p.id })}
              following={follow.isPending && follow.variables?.id === p.id}
            />
          ))}
        </div>

        {!pagesQ.isLoading && items.length === 0 && (
          <div className="impilo-surface-card p-8 text-center">
            <p className="text-sm font-semibold text-[color:var(--text-primary)]">No pages found</p>
            <p className="mt-1 text-xs text-[color:var(--text-muted)]">Try a different kind or clear the search.</p>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
