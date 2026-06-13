"use client";

/**
 * Search — Health Knowledge Search (Health OS §16a)
 *
 * Wired to BFF {@code GET /internal/v1/search} → search-service index (Phase D slice).
 */

import { useState } from "react";
import { Search as SearchIcon, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useKnowledgeSearch } from "@/hooks/queries/useKnowledgeSearch";

const DOMAINS = [
  { id: "all", label: "All" },
  { id: "health", label: "Health" },
  { id: "wellness", label: "Wellness" },
  { id: "diet", label: "Diet & Nutrition" },
  { id: "sleep", label: "Sleep" },
  { id: "services", label: "Services" },
  { id: "marketplace", label: "Marketplace" },
  { id: "providers", label: "Providers" },
  { id: "facilities", label: "Facilities" },
];

function hitTitle(hit: { contentJson?: Record<string, unknown>; searchableText?: string; entityType: string }) {
  const cj = hit.contentJson;
  if (cj && typeof cj.title === "string") return cj.title;
  if (cj && typeof cj.name === "string") return cj.name;
  const text = hit.searchableText?.trim();
  if (text) return text.length > 120 ? `${text.slice(0, 117)}…` : text;
  return hit.entityType;
}

export default function SearchPage() {
  const [draftQuery, setDraftQuery] = useState("");
  const [draftDomain, setDraftDomain] = useState("all");
  const [committedQuery, setCommittedQuery] = useState("");
  const [committedEntityType, setCommittedEntityType] = useState<string | undefined>(undefined);

  const { data, isFetching, isError } = useKnowledgeSearch({
    q: committedQuery,
    entityType: committedEntityType,
    page: 0,
    size: 20,
  });

  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    const q = draftQuery.trim();
    if (!q) return;
    setCommittedQuery(q);
    setCommittedEntityType(draftDomain === "all" ? undefined : draftDomain);
  }

  const payload = data?.data;
  const results = payload?.results ?? [];
  const totalCount = payload?.totalElements ?? results.length;
  const resultLabel = totalCount === 1 ? "result" : "results";

  return (
    <AppLayout>
      <PageShell title="Search" subtitle="Search health, wellness, lifestyle, and service knowledge" icon={<SearchIcon className="h-6 w-6" />}>
        <div className="max-w-2xl mx-auto space-y-6">
          <form onSubmit={handleSearch} className="relative">
            <SearchIcon className="absolute left-4 top-3.5 h-5 w-5 text-muted-foreground" />
            <input
              value={draftQuery}
              onChange={(e) => setDraftQuery(e.target.value)}
              placeholder="Search for health topics, services, providers, conditions..."
              className="w-full rounded-xl border border-border pl-12 pr-4 py-3 text-sm focus:outline-none focus:border-impilo-400 focus:ring-2 focus:ring-impilo-100"
            />
          </form>

          <div className="flex gap-2 flex-wrap">
            {DOMAINS.map((d) => (
              <button
                key={d.id}
                type="button"
                onClick={() => setDraftDomain(d.id)}
                className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                  draftDomain === d.id
                    ? "bg-primary text-white"
                    : "bg-neutral-100 text-foreground hover:bg-neutral-100"
                }`}
              >
                {d.label}
              </button>
            ))}
          </div>

          {isFetching && (
            <div className="flex items-center justify-center py-12 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin mr-2" /> Searching governed knowledge repositories…
            </div>
          )}

          {isError && (
            <div className="rounded-lg border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-red-800">
              Search failed. Confirm the Experience BFF and search-service are running, then try again.
            </div>
          )}

          {!isFetching && !isError && committedQuery && results.length === 0 && (
            <div className="text-center py-10 text-sm text-muted-foreground">
              No indexed matches yet for this query
              {committedEntityType ? ` (type: ${committedEntityType})` : ""}. Results appear as platforms publish to the
              search index.
            </div>
          )}

          {!isFetching && !isError && results.length > 0 && (
            <div className="space-y-3">
              <p className="text-xs text-muted-foreground">
                {totalCount} {resultLabel}
              </p>
              <ul className="space-y-2">
                {results.map((hit) => (
                  <li
                    key={hit.id}
                    className="rounded-lg border border-border bg-card px-4 py-3 text-sm shadow-sm"
                  >
                    <div className="font-medium text-foreground">{hitTitle(hit)}</div>
                    <div className="mt-1 text-xs text-muted-foreground">
                      {hit.entityType} · {hit.entityId}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {!isFetching && !committedQuery && (
            <div className="text-center py-12 text-sm text-muted-foreground">
              <SearchIcon className="h-8 w-8 mx-auto mb-2 text-muted-foreground" />
              <p>Search across health, wellness, diet, sleep, services, and marketplace knowledge.</p>
              <p className="text-xs mt-1">Results are governed by your role, context, and consent settings.</p>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
