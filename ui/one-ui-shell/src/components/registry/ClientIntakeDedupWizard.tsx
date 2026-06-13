"use client";

import Link from "next/link";
import { useState } from "react";
import { GitMerge, Loader2, Search, UserCheck } from "lucide-react";
import { useClientRegistryClients } from "@/hooks/queries/useClientRegistry";
import { useDedupCases, useMergeDedup, useScoreDedup } from "@/hooks/queries/useVitoDedup";

const inputClass =
  "w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-500";

type WizardStep = "search" | "score" | "merge";

export function ClientIntakeDedupWizard({ compact = false }: { compact?: boolean }) {
  const [step, setStep] = useState<WizardStep>("search");
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [sourceHealthId, setSourceHealthId] = useState("");
  const [targetHealthId, setTargetHealthId] = useState("");
  const [mergeNotes, setMergeNotes] = useState("");

  const clientsQ = useClientRegistryClients({
    query: appliedQuery || undefined,
    page: 0,
    size: 10,
  });
  const score = useScoreDedup();
  const merge = useMergeDedup();
  const pendingCases = useDedupCases("PENDING", 0, 50);

  const candidates = clientsQ.data?.data?.items ?? [];
  const scoreResult = score.data?.data;
  const matchingCase = (pendingCases.data?.data?.items ?? []).find(
    (c) =>
      (c.sourceHealthId === sourceHealthId && c.targetHealthId === targetHealthId) ||
      (c.sourceHealthId === targetHealthId && c.targetHealthId === sourceHealthId),
  );

  function runSearch() {
    setAppliedQuery(query.trim());
    setStep("search");
  }

  function selectCandidate(healthId: string) {
    if (!sourceHealthId.trim()) {
      setSourceHealthId(healthId);
      return;
    }
    setTargetHealthId(healthId);
    setStep("score");
    score.mutate({
      sourceHealthId: sourceHealthId.trim(),
      targetHealthId: healthId,
    });
  }

  return (
    <section
      className={`rounded-2xl border border-violet-200 bg-violet-50/40 ${compact ? "p-4" : "p-5"}`}
      data-testid="client-intake-dedup-wizard"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <GitMerge className="h-4 w-4 text-violet-600" />
            Client intake deduplication
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            Search the client registry, score similarity via VITO dedup, then merge when stewardship confirms a duplicate.
          </p>
        </div>
        <Link href="/operations/vito/dedup" className="text-xs font-medium text-violet-700 hover:underline">
          Full dedup queue →
        </Link>
      </div>

      <div className="mt-4 flex flex-wrap gap-2 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
        {(["search", "score", "merge"] as WizardStep[]).map((s) => (
          <span
            key={s}
            className={`rounded-full px-2.5 py-0.5 ${
              step === s ? "bg-violet-600 text-white" : "bg-card text-muted-foreground border border-border"
            }`}
          >
            {s}
          </span>
        ))}
      </div>

      {step === "search" ? (
        <div className="mt-4 space-y-3">
          <div className="flex flex-wrap gap-2">
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Name, phone, national ID reference…"
              className={`${inputClass} min-w-[200px] flex-1`}
            />
            <button
              type="button"
              disabled={clientsQ.isFetching}
              onClick={runSearch}
              className="inline-flex items-center gap-1.5 rounded-lg bg-violet-700 px-3 py-2 text-xs font-medium text-white hover:bg-violet-800 disabled:opacity-50"
            >
              {clientsQ.isFetching ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Search className="h-3.5 w-3.5" />}
              Search registry
            </button>
          </div>

          {sourceHealthId ? (
            <p className="text-xs text-violet-800">
              Source record: <span className="font-mono font-semibold">{sourceHealthId}</span> — select a candidate below.
            </p>
          ) : (
            <p className="text-xs text-muted-foreground">Select the intake record first, then pick a possible duplicate.</p>
          )}

          {clientsQ.isError ? (
            <p className="text-xs text-red-600">Client registry search failed. Verify BFF connectivity.</p>
          ) : null}

          {appliedQuery && !clientsQ.isLoading && candidates.length === 0 ? (
            <p className="rounded-lg border border-dashed border-border bg-card p-4 text-xs text-muted-foreground">
              No registry matches for &ldquo;{appliedQuery}&rdquo;. Proceed with new registration if no duplicate exists.
            </p>
          ) : null}

          <ul className="space-y-2">
            {candidates.map((client) => (
              <li key={client.healthId}>
                <button
                  type="button"
                  onClick={() => selectCandidate(client.healthId)}
                  className="w-full rounded-xl border border-white bg-card p-3 text-left text-sm hover:border-violet-300"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium text-foreground">{client.displayName}</span>
                    <span className="font-mono text-xs text-muted-foreground">{client.healthId}</span>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {client.lifecycleStatus} · {client.verificationStatus} · matches open: {client.openMatches}
                  </p>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {step === "score" || step === "merge" ? (
        <div className="mt-4 space-y-3 rounded-xl border border-white bg-card p-4">
          <p className="text-xs text-muted-foreground">
            Comparing <span className="font-mono">{sourceHealthId}</span> ↔{" "}
            <span className="font-mono">{targetHealthId}</span>
          </p>

          {score.isPending ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Scoring…
            </div>
          ) : score.isError ? (
            <p className="text-xs text-red-600">Score request failed.</p>
          ) : scoreResult ? (
            <div className="space-y-2">
              <p className="text-2xl font-bold text-foreground">{scoreResult.score}</p>
              {scoreResult.conflictingFields && scoreResult.conflictingFields.length > 0 ? (
                <ul className="text-xs text-red-600">
                  {scoreResult.conflictingFields.map((field) => (
                    <li key={field}>Conflict: {field}</li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-primary-hover">No conflicting demographic fields reported.</p>
              )}
            </div>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => {
                setStep("search");
                setTargetHealthId("");
              }}
              className="rounded-lg border border-border px-3 py-1.5 text-xs text-foreground hover:bg-background"
            >
              Back to search
            </button>
            {scoreResult && scoreResult.score >= 0.85 && matchingCase ? (
              <button
                type="button"
                disabled={merge.isPending}
                onClick={() => {
                  setStep("merge");
                  merge.mutate({
                    caseId: matchingCase.id,
                    body: {
                      sourceHealthId: sourceHealthId.trim(),
                      targetHealthId: targetHealthId.trim(),
                      notes: mergeNotes || "Merged from client intake dedup wizard",
                      mergeReason: "INTAKE_DEDUP_WIZARD",
                    },
                  });
                }}
                className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                <UserCheck className="h-3.5 w-3.5" />
                {merge.isPending ? "Merging…" : "Merge pending case"}
              </button>
            ) : scoreResult && scoreResult.score >= 0.85 ? (
              <span className="text-xs text-warning-foreground">
                High score but no pending VITO case — escalate via{" "}
                <Link href="/operations/vito/dedup" className="font-medium underline">
                  dedup management
                </Link>
                .
              </span>
            ) : (
              <span className="text-xs text-warning-foreground">Score below merge threshold — continue as new registration or escalate.</span>
            )}
          </div>

          <input
            type="text"
            value={mergeNotes}
            onChange={(e) => setMergeNotes(e.target.value)}
            placeholder="Merge notes (optional)"
            className={inputClass}
          />

          {merge.isSuccess ? (
            <p className="text-xs text-primary-hover">Merge accepted. Refresh client profile to confirm survivorship.</p>
          ) : null}
          {merge.isError ? <p className="text-xs text-red-600">Merge failed — use the full dedup queue for governed merge cases.</p> : null}
        </div>
      ) : null}
    </section>
  );
}
