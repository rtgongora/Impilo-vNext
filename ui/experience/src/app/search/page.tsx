"use client";

/**
 * Search — Health Knowledge Search (Health OS §16a)
 *
 * "Searchable, so that users can actively look for health, wellness, lifestyle,
 *  diet, sleep, service, and marketplace information."
 */

import { useState } from "react";
import { Search as SearchIcon, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

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

export default function SearchPage() {
  const [query, setQuery] = useState("");
  const [domain, setDomain] = useState("all");
  const [searching, setSearching] = useState(false);

  function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!query.trim()) return;
    setSearching(true);
    // Placeholder — will connect to BFF /internal/v1/search endpoint
    setTimeout(() => setSearching(false), 1000);
  }

  return (
    <AppLayout>
      <PageShell title="Search" subtitle="Search health, wellness, lifestyle, and service knowledge" icon={<SearchIcon className="h-6 w-6" />}>
        <div className="max-w-2xl mx-auto space-y-6">
          <form onSubmit={handleSearch} className="relative">
            <SearchIcon className="absolute left-4 top-3.5 h-5 w-5 text-gray-400" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search for health topics, services, providers, conditions..."
              className="w-full rounded-xl border border-gray-300 pl-12 pr-4 py-3 text-sm focus:outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            />
          </form>

          <div className="flex gap-2 flex-wrap">
            {DOMAINS.map((d) => (
              <button
                key={d.id}
                onClick={() => setDomain(d.id)}
                className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                  domain === d.id
                    ? "bg-blue-600 text-white"
                    : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                }`}
              >
                {d.label}
              </button>
            ))}
          </div>

          {searching && (
            <div className="flex items-center justify-center py-12 text-gray-500">
              <Loader2 className="h-5 w-5 animate-spin mr-2" /> Searching governed knowledge repositories...
            </div>
          )}

          {!searching && !query && (
            <div className="text-center py-12 text-sm text-gray-400">
              <SearchIcon className="h-8 w-8 mx-auto mb-2 text-gray-300" />
              <p>Search across health, wellness, diet, sleep, services, and marketplace knowledge.</p>
              <p className="text-xs mt-1">Results are governed by your role, context, and consent settings.</p>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
