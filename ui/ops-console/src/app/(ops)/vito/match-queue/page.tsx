"use client";

import React, { useCallback, useEffect, useState } from "react";

interface MatchResult {
  id: number;
  sourceHealthId: string;
  candidateHealthId: string;
  matchScore: number;
  matchAlgorithm: string;
  disposition: string;
  createdAt: string;
}

/**
 * Identity Resolution Queue — operator reviews pending matches.
 * Uses probabilistic matching engine scores to help decision-making.
 */
export default function MatchQueuePage() {
  const [matches, setMatches] = useState<MatchResult[]>([]);
  const [loading, setLoading] = useState(true);

  const loadMatches = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/v1/match/pending?page=0&size=50");
      const data = await res.json();
      if (data.success) {
        setMatches(data.data.items ?? []);
      }
    } catch {
      // Will show empty state
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMatches();
  }, [loadMatches]);

  const resolveMatch = async (matchId: number, disposition: string) => {
    try {
      await fetch(`/api/v1/match/${matchId}/resolve`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ disposition }),
      });
      await loadMatches();
    } catch {
      // Error handling via toast in production
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Identity Resolution Queue
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          Review pending identity matches from the probabilistic matching engine
        </p>
      </div>

      <div className="bg-white rounded-[12px] shadow-subtle border border-neutral-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-100 bg-neutral-50">
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Source</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Candidate</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Score</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Algorithm</th>
              <th className="px-4 py-3 text-left font-medium text-neutral-500">Date</th>
              <th className="px-4 py-3 text-right font-medium text-neutral-500">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-neutral-500">Loading...</td>
              </tr>
            ) : matches.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-neutral-500">
                  No pending matches. The queue is clear.
                </td>
              </tr>
            ) : (
              matches.map((match) => (
                <tr key={match.id} className="border-b border-neutral-50 hover:bg-neutral-50">
                  <td className="px-4 py-3 font-mono text-xs">{match.sourceHealthId.substring(0, 8)}...</td>
                  <td className="px-4 py-3 font-mono text-xs">{match.candidateHealthId.substring(0, 8)}...</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${
                      match.matchScore >= 0.9
                        ? "bg-success/10 text-success"
                        : match.matchScore >= 0.8
                        ? "bg-warning/10 text-warning"
                        : "bg-neutral-100 text-neutral-900"
                    }`}>
                      {(match.matchScore * 100).toFixed(1)}%
                    </span>
                  </td>
                  <td className="px-4 py-3 text-neutral-500">{match.matchAlgorithm}</td>
                  <td className="px-4 py-3 text-neutral-500">
                    {new Date(match.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => resolveMatch(match.id, "MANUAL_LINKED")}
                      className="px-3 py-1 text-xs font-medium text-white bg-brand-primary rounded-[8px] hover:bg-brand-primary/90 mr-2"
                    >
                      Link
                    </button>
                    <button
                      onClick={() => resolveMatch(match.id, "REJECTED")}
                      className="px-3 py-1 text-xs font-medium text-danger bg-danger/10 rounded-[8px] hover:bg-danger/20"
                    >
                      Reject
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
