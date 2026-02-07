"use client";

import React, { useCallback, useState } from "react";
import { vitoApi, type MaskedClient } from "@/lib/vitoApi";

/**
 * Client Search — internal masked search for identity verification.
 * Operators can search by name, Impilo ID, or Health ID fragment.
 * Results are masked to protect PII per VITO privacy policy.
 */
export default function ClientSearchPage() {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<MaskedClient[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [expandedRow, setExpandedRow] = useState<string | null>(null);

  const handleSearch = useCallback(async () => {
    if (query.trim().length < 2) return;
    setLoading(true);
    setSearched(true);
    try {
      const data = await vitoApi.searchClients(query.trim(), 0, 20);
      setResults(data.items ?? []);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, [query]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      handleSearch();
    }
  };

  const statusBadge = (status: string) => {
    const styles: Record<string, string> = {
      ACTIVE: "bg-success/10 text-success",
      VERIFIED: "bg-success/10 text-success",
      PROVISIONAL: "bg-warning/10 text-warning",
      INACTIVE: "bg-neutral-100 text-neutral-500",
      DECEASED: "bg-danger/10 text-danger",
      MERGED: "bg-info/10 text-info",
    };
    return styles[status] ?? "bg-neutral-100 text-neutral-900";
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Client Search
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Search the identity registry by name, Impilo ID, or Health ID fragment. Results are masked.
        </p>
      </div>

      {/* Search bar */}
      <div className="flex gap-3 mb-6">
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Enter name, Impilo ID, or Health ID (min 2 characters)..."
          className="flex-1 px-4 py-2.5 text-sm border border-neutral-200 rounded-[8px] bg-white focus:outline-none focus:ring-2 focus:ring-brand-primary/20 focus:border-brand-primary placeholder:text-neutral-400"
        />
        <button
          onClick={handleSearch}
          disabled={query.trim().length < 2 || loading}
          className="px-5 py-2.5 text-sm font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {loading ? "Searching..." : "Search"}
        </button>
      </div>

      {/* Results table */}
      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-100 bg-neutral-50">
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Impilo ID</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Name (Masked)</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Year of Birth</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Sex</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Status</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-neutral-500">Searching...</td>
              </tr>
            ) : !searched ? (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-neutral-500">
                  Enter a search query above to find clients
                </td>
              </tr>
            ) : results.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-8 text-center text-neutral-500">
                  No clients found matching your search criteria
                </td>
              </tr>
            ) : (
              results.map((client) => (
                <React.Fragment key={client.healthId}>
                  <tr
                    onClick={() => setExpandedRow(expandedRow === client.healthId ? null : client.healthId)}
                    className="border-b border-neutral-50 hover:bg-neutral-50 cursor-pointer"
                  >
                    <td className="px-4 py-3 font-mono text-xs">
                      {client.impiloId ?? "—"}
                    </td>
                    <td className="px-4 py-3 text-neutral-900">
                      {client.givenName} {client.familyName}
                    </td>
                    <td className="px-4 py-3 text-neutral-500">
                      {client.yearOfBirth ?? "—"}
                    </td>
                    <td className="px-4 py-3 text-neutral-500">
                      {client.sex ?? "—"}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${statusBadge(client.status)}`}>
                        {client.status}
                      </span>
                    </td>
                  </tr>
                  {expandedRow === client.healthId && (
                    <tr className="bg-neutral-50">
                      <td colSpan={5} className="px-4 py-3">
                        <div className="flex items-center gap-2 text-xs">
                          <span className="text-neutral-500">Health ID:</span>
                          <span className="font-mono text-neutral-900 bg-white px-2 py-1 rounded border border-neutral-200">
                            {client.healthId}
                          </span>
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
