"use client";

/**
 * Health Intelligence hub — fused retrieval, summaries, helpdesk triage, learning nudges.
 * Route: /intelligence | Guard: auth
 */

import Link from "next/link";
import { useCallback, useState } from "react";
import { Brain, BookOpen, LifeBuoy, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { IntelligenceResultPanel } from "@/components/intelligence/IntelligenceResultPanel";
import { useIntelligenceQuery } from "@/hooks/useIntelligence";
import type { IntelligenceQueryType } from "@/lib/intelligence/types";

const PRESETS: { label: string; queryType: IntelligenceQueryType; hint: string }[] = [
  { label: "Fused search", queryType: "SEARCH_FUSED", hint: "Platform index + guidance knowledge" },
  { label: "Helpdesk assist", queryType: "HELP_DESK_ASSIST", hint: "Knowledge + learning routes (describe issue below)" },
  { label: "Learning nudge", queryType: "LEARNING_NUDGE", hint: "Fundo-aligned education index" },
  { label: "Workflow copilot", queryType: "WORKFLOW_ASSIST", hint: "Non-automating checklist hints" },
  { label: "Data quality hints", queryType: "DATA_QUALITY_HINTS", hint: "Registry heuristics (no auto-merge)" },
];

export default function IntelligenceHubPage() {
  const { run, loading, error } = useIntelligenceQuery();
  const [queryType, setQueryType] = useState<IntelligenceQueryType>("SEARCH_FUSED");
  const [text, setText] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [rankMode, setRankMode] = useState<"recency" | "lexical" | "semantic" | "hybrid">("lexical");
  const [issueType, setIssueType] = useState("GENERAL");
  const [attachDags, setAttachDags] = useState(false);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);

  const execute = useCallback(async () => {
    const ctx: Record<string, unknown> = {};
    if (facilityId.trim()) ctx.facilityId = facilityId.trim();
    if (subjectId.trim()) ctx.subjectId = subjectId.trim();
    if (queryType === "DATA_QUALITY_HINTS" && !ctx.subjectType) ctx.subjectType = "CLIENT";
    if (queryType === "SEARCH_FUSED") ctx.rankMode = rankMode;

    const input: Record<string, unknown> = {};
    if (text.trim()) input.text = text.trim();
    if (queryType === "HELP_DESK_ASSIST") {
      input.issueType = issueType.trim() || "GENERAL";
      if (attachDags) input.attachDagsGovernanceSummary = true;
    }

    const data = await run({ queryType, context: ctx, input });
    setResult(data);
  }, [run, queryType, text, facilityId, subjectId, rankMode, issueType, attachDags]);

  return (
    <AppLayout>
      <PageShell
        title="Health Intelligence"
        subtitle="Cross-service retrieval, summarisation, and governed decision-support — not autonomous action"
        icon={<Brain className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap gap-3 text-sm">
          <Link href="/search" className="text-teal-700 hover:underline inline-flex items-center gap-1">
            <Search className="h-4 w-4" /> Search
          </Link>
          <Link href="/support/tickets" className="text-teal-700 hover:underline inline-flex items-center gap-1">
            <LifeBuoy className="h-4 w-4" /> Support
          </Link>
          <Link href="/guidance/education" className="text-teal-700 hover:underline inline-flex items-center gap-1">
            <BookOpen className="h-4 w-4" /> Health education
          </Link>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <div className="space-y-4 rounded-lg border border-gray-200 bg-white p-4">
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Query type</label>
              <select
                value={queryType}
                onChange={(e) => setQueryType(e.target.value as IntelligenceQueryType)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              >
                {PRESETS.map((p) => (
                  <option key={p.queryType} value={p.queryType}>
                    {p.label}
                  </option>
                ))}
                <option value="SUMMARY_QUEUE">Queue summary</option>
                <option value="SUMMARY_PROVIDER">Provider summary</option>
                <option value="SUMMARY_CLIENT">Client identity summary</option>
                <option value="ANOMALY_SCAN_OPERATIONS">Operational anomaly scan</option>
              </select>
              <p className="mt-1 text-xs text-gray-500">
                {PRESETS.find((p) => p.queryType === queryType)?.hint ??
                  "Outputs are suggestions; sensitive actions remain human-governed."}
              </p>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 mb-1">Text / issue</label>
              <textarea
                value={text}
                onChange={(e) => setText(e.target.value)}
                rows={3}
                placeholder="Search text, issue description, or triage notes…"
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              />
            </div>
            {queryType === "SEARCH_FUSED" ? (
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Index rank mode</label>
                <select
                  value={rankMode}
                  onChange={(e) => setRankMode(e.target.value as typeof rankMode)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                >
                  <option value="recency">Recency (indexedAt)</option>
                  <option value="lexical">Lexical relevance (search-service)</option>
                  <option value="semantic">Semantic (cosine when embeddings enabled)</option>
                  <option value="hybrid">Hybrid (cosine + lexical, tunable weight)</option>
                </select>
              </div>
            ) : null}
            {queryType === "HELP_DESK_ASSIST" ? (
              <div className="space-y-2">
                <div>
                  <label className="block text-xs font-medium text-gray-500 mb-1">Learning issue type</label>
                  <input
                    value={issueType}
                    onChange={(e) => setIssueType(e.target.value)}
                    className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                    placeholder="e.g. ACCESS, BILLING, GENERAL"
                  />
                </div>
                <label className="flex items-center gap-2 text-xs text-gray-700">
                  <input
                    type="checkbox"
                    checked={attachDags}
                    onChange={(e) => setAttachDags(e.target.checked)}
                  />
                  Attach DAGS pending access-request count (operator insight)
                </label>
              </div>
            ) : null}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Facility ID (optional)</label>
                <input
                  value={facilityId}
                  onChange={(e) => setFacilityId(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  placeholder="UUID for queue / anomaly modes"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Subject ID (optional)</label>
                <input
                  value={subjectId}
                  onChange={(e) => setSubjectId(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
                  placeholder="Provider id or Health ID"
                />
              </div>
            </div>
            {error ? <p className="text-sm text-red-600">{error}</p> : null}
            <button
              type="button"
              onClick={() => void execute()}
              disabled={loading}
              className="rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-60"
            >
              {loading ? "Running…" : "Run intelligence query"}
            </button>
          </div>

          <div className="rounded-lg border border-gray-200 bg-gray-50 p-4">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">Result</h2>
            <IntelligenceResultPanel data={result} />
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
