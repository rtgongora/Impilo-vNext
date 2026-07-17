"use client";

/**
 * Public idea board (gateway ADR W4). Lists moderated, approved ideas with their support
 * counts and lets anyone back an idea WITHOUT an account. Support is de-duplicated downstream
 * by an opaque per-connection key (no identity captured). Fail-soft: an outage shows an honest
 * empty/error state, never a crash.
 *
 * Backend:
 *   GET  /internal/v1/public/gateway/get-involved/board -> [{ id, reference, type, needArea,
 *          title, problemOrOpportunity, betterExperience, beneficiary, status, supportCount, createdAt }]
 *   POST /internal/v1/public/gateway/get-involved/board/{id}/support -> { reference, supportCount, message }
 */

import { useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";

interface BoardIdea {
  id: string;
  reference?: string;
  type?: string;
  needArea?: string;
  title?: string;
  problemOrOpportunity?: string;
  betterExperience?: string;
  beneficiary?: string;
  status?: string;
  supportCount?: number;
  createdAt?: string;
}

interface SupportResult {
  supportCount?: number;
}

function normalizeBoard(res: unknown): BoardIdea[] {
  const list = Array.isArray(res)
    ? res
    : res && typeof res === "object" && Array.isArray((res as { data?: unknown[] }).data)
      ? (res as { data: unknown[] }).data
      : [];
  return list.filter((x): x is BoardIdea => Boolean(x) && typeof (x as BoardIdea).id === "string");
}

export function IdeaBoard() {
  const [ideas, setIdeas] = useState<BoardIdea[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [supporting, setSupporting] = useState<string | null>(null);
  const [supported, setSupported] = useState<Set<string>>(new Set());

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await apiClient.get<unknown>("/internal/v1/public/gateway/get-involved/board");
        if (!cancelled) setIdeas(normalizeBoard(res));
      } catch {
        if (!cancelled) setError("We could not load the idea board just now. Please try again later.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const support = async (id: string) => {
    setSupporting(id);
    try {
      const res = await apiClient.post<SupportResult>(
        `/internal/v1/public/gateway/get-involved/board/${encodeURIComponent(id)}/support`,
      );
      setIdeas((prev) =>
        prev.map((idea) =>
          idea.id === id
            ? { ...idea, supportCount: res?.supportCount ?? (idea.supportCount ?? 0) + 1 }
            : idea,
        ),
      );
      setSupported((prev) => new Set(prev).add(id));
    } catch {
      // Non-blocking: leave the count as-is; the citizen can retry.
    } finally {
      setSupporting(null);
    }
  };

  if (loading) {
    return <p className="text-sm text-slate-500" data-testid="board-loading">Loading ideas…</p>;
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
    );
  }

  if (ideas.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-600" data-testid="board-empty">
        No ideas have been published to the board yet. Be the first to{" "}
        <a href="/get-involved/idea" className="font-medium text-emerald-700 hover:text-emerald-800">
          share one
        </a>
        .
      </div>
    );
  }

  return (
    <ul className="grid gap-4 sm:grid-cols-2" data-testid="idea-board">
      {ideas.map((idea) => {
        const alreadySupported = supported.has(idea.id);
        return (
          <li key={idea.id} className="flex flex-col rounded-xl border border-slate-200 bg-white p-5">
            {idea.needArea && (
              <span className="w-fit rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-medium text-emerald-700">
                {idea.needArea}
              </span>
            )}
            <h3 className="mt-2 font-semibold text-slate-900">
              {idea.title || idea.problemOrOpportunity || "Untitled idea"}
            </h3>
            {idea.problemOrOpportunity && idea.title && (
              <p className="mt-1 text-sm text-slate-600">{idea.problemOrOpportunity}</p>
            )}
            {idea.betterExperience && (
              <p className="mt-2 text-sm text-slate-500">
                <span className="font-medium text-slate-700">A better way: </span>
                {idea.betterExperience}
              </p>
            )}
            <div className="mt-4 flex items-center justify-between">
              <span className="text-sm text-slate-600" data-testid="support-count">
                {idea.supportCount ?? 0} {idea.supportCount === 1 ? "supporter" : "supporters"}
              </span>
              <button
                type="button"
                onClick={() => support(idea.id)}
                disabled={supporting === idea.id || alreadySupported}
                className="rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-1.5 text-sm font-semibold text-emerald-800 hover:bg-emerald-100 disabled:opacity-60"
              >
                {alreadySupported ? "Supported" : supporting === idea.id ? "Supporting…" : "Support this idea"}
              </button>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
