"use client";

import { useState } from "react";
import { Sparkles } from "lucide-react";
import Link from "next/link";
import { IntelligenceResultPanel } from "@/components/intelligence/IntelligenceResultPanel";
import { useIntelligenceQuery } from "@/hooks/useIntelligence";

/**
 * Helpdesk triage panel — routes issue text through HELP_DESK_ASSIST (knowledge + learning).
 * Suggestions are non-authoritative; humans confirm escalations and account actions.
 */
export function HelpdeskIntelligenceAssist() {
  const { run, loading, error } = useIntelligenceQuery();
  const [issue, setIssue] = useState("");
  const [issueType, setIssueType] = useState("GENERAL");
  const [attachDags, setAttachDags] = useState(false);
  const [pack, setPack] = useState<Record<string, unknown> | null>(null);

  return (
    <section className="rounded-lg border border-teal-100 bg-teal-50/40 p-4">
      <div className="flex items-center gap-2 mb-2">
        <Sparkles className="h-5 w-5 text-teal-600" />
        <h2 className="text-sm font-semibold text-foreground">Helpdesk intelligence</h2>
      </div>
      <p className="text-xs text-muted-foreground mb-3">
        Grounded knowledge, learning-service routes when configured, and optional DAGS queue insight. Review matches
        before acting — this is decision support, not authorization.
      </p>
      <textarea
        value={issue}
        onChange={(e) => setIssue(e.target.value)}
        rows={3}
        placeholder="Describe the issue (symptoms, error text, user role)…"
        className="w-full rounded-lg border border-teal-200 bg-card px-3 py-2 text-sm mb-2"
      />
      <div className="mb-2 flex flex-wrap gap-3 text-xs">
        <label className="flex flex-col gap-0.5 text-foreground">
          <span className="text-muted-foreground">Issue type (learning plane)</span>
          <input
            value={issueType}
            onChange={(e) => setIssueType(e.target.value)}
            className="rounded border border-teal-200 bg-card px-2 py-1 text-xs w-40"
            placeholder="GENERAL"
          />
        </label>
        <label className="flex items-center gap-2 self-end pb-1 text-foreground">
          <input type="checkbox" checked={attachDags} onChange={(e) => setAttachDags(e.target.checked)} />
          Include DAGS pending count
        </label>
      </div>
      {error ? <p className="text-xs text-red-600 mb-2">{error}</p> : null}
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={loading || !issue.trim()}
          onClick={async () => {
            const input: Record<string, unknown> = { text: issue.trim(), issueType: issueType.trim() || "GENERAL" };
            if (attachDags) input.attachDagsGovernanceSummary = true;
            const data = await run({
              queryType: "HELP_DESK_ASSIST",
              context: {},
              input,
            });
            setPack(data);
          }}
          className="rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-primary disabled:opacity-50"
        >
          {loading ? "Analyzing…" : "Suggest KB & learning routes"}
        </button>
        <Link href="/intelligence" className="text-xs text-teal-800 underline self-center">
          Open Intelligence hub
        </Link>
        <Link href="/guidance/education" className="text-xs text-teal-800 underline self-center">
          Health education
        </Link>
      </div>
      {pack ? (
        <div className="mt-3 rounded border border-teal-100 bg-card/80 p-2">
          <IntelligenceResultPanel data={pack} />
        </div>
      ) : null}
    </section>
  );
}
