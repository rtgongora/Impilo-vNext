"use client";

import type { ReactNode } from "react";

function asRecord(v: unknown): Record<string, unknown> | null {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

function Card({
  title,
  children,
  tone = "slate",
}: {
  title: string;
  children: ReactNode;
  tone?: "slate" | "teal" | "violet" | "amber";
}) {
  const border =
    tone === "teal"
      ? "border-teal-100"
      : tone === "violet"
        ? "border-violet-100"
        : tone === "amber"
          ? "border-amber-100"
          : "border-border";
  const bg =
    tone === "teal"
      ? "bg-teal-50/40"
      : tone === "violet"
        ? "bg-violet-50/40"
        : tone === "amber"
          ? "bg-warning-soft/40"
          : "bg-card";
  return (
    <section className={`rounded-3xl border ${border} ${bg} p-4 shadow-impilo-card`}>
      <h3 className="text-xs font-semibold uppercase tracking-wide text-[color:var(--text-muted)] mb-2">{title}</h3>
      <div className="text-sm text-[color:var(--text-primary)]">{children}</div>
    </section>
  );
}

/**
 * Structured rendering of Health Intelligence plane payloads (avoids raw JSON dumps in primary UX).
 */
export function IntelligenceResultPanel({ data }: { data: Record<string, unknown> | null }) {
  if (!data) {
    return <p className="text-sm text-[color:var(--text-secondary)]">Run a query to see results.</p>;
  }

  const status = typeof data.status === "string" ? data.status : "COMPLETED";
  const policy = typeof data.policyApplied === "string" ? data.policyApplied : "";
  const summary = typeof data.summary === "string" ? data.summary : null;
  const ev = asRecord(data.effectiveVisibility);
  const recs = Array.isArray(data.recommendations) ? (data.recommendations as unknown[]) : [];
  const ranked = Array.isArray(data.rankedHits) ? (data.rankedHits as unknown[]) : [];
  const gov = asRecord(data.governanceSummary);
  const learningHd = data.learningServiceHelpdesk;
  const learningHits = data.learningServiceSearchHits;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2 text-xs text-[color:var(--text-secondary)]">
        <span className="impilo-chip">Status: {status}</span>
        {policy ? (
          <span className="impilo-chip" title="Policy envelope">
            Policy: {policy}
          </span>
        ) : null}
        {typeof data.queryType === "string" ? (
          <span className="impilo-chip">Mode: {data.queryType}</span>
        ) : null}
      </div>

      {ev ? (
        <Card title="Effective visibility" tone="violet">
          <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
            {Object.entries(ev).map(([k, v]) => (
              <div key={k} className="contents">
                <dt className="text-muted-foreground">{k}</dt>
                <dd className="font-medium">{String(v)}</dd>
              </div>
            ))}
          </dl>
        </Card>
      ) : null}

      {summary ? (
        <Card title="Summary" tone="teal">
          <p>{summary}</p>
        </Card>
      ) : null}

      {recs.length > 0 ? (
        <Card title="Recommendations (suggestions only)" tone="amber">
          <ul className="space-y-2">
            {recs.map((r, i) => {
              const o = asRecord(r);
              if (!o) return null;
              return (
                <li key={i} className="rounded border border-amber-100 bg-card/80 px-2 py-1.5 text-xs">
                  <span className="font-medium text-foreground">{String(o.summary ?? o.recommendationType ?? "Item")}</span>
                  {o.rationale ? <p className="text-muted-foreground mt-0.5">{String(o.rationale)}</p> : null}
                </li>
              );
            })}
          </ul>
        </Card>
      ) : null}

      {ranked.length > 0 ? (
        <Card title="Retrieval hits" tone="slate">
          <p className="text-xs text-muted-foreground mb-1">{ranked.length} fused row(s) — open records only via governed routes.</p>
          <ul className="max-h-40 overflow-auto space-y-1 text-xs">
            {ranked.slice(0, 12).map((row, i) => {
              const m = asRecord(row);
              const src = m ? String(m.source ?? "") : "";
              const hit = m ? asRecord(m.hit) : null;
              const title =
                hit && typeof hit.title === "string"
                  ? hit.title
                  : hit && typeof hit.entityType === "string"
                    ? hit.entityType
                    : "Hit";
              return (
                <li key={i} className="truncate text-foreground">
                  <span className="text-muted-foreground">{src}</span> · {title}
                </li>
              );
            })}
          </ul>
        </Card>
      ) : null}

      {gov && !gov.error ? (
        <Card title="Governance (DAGS)" tone="slate">
          <p className="text-xs">
            Pending access request rows (sample): <strong>{String(gov.pendingAccessRequestRows ?? "—")}</strong>
          </p>
        </Card>
      ) : null}

      {learningHd || learningHits ? (
        <Card title="Learning service" tone="teal">
          <p className="text-xs text-muted-foreground">Structured payloads from Impilo learning plane when configured.</p>
        </Card>
      ) : null}

      <details className="text-xs text-muted-foreground">
        <summary className="cursor-pointer select-none">Raw JSON</summary>
        <pre className="mt-2 max-h-48 overflow-auto rounded bg-background p-2 text-[11px]">{JSON.stringify(data, null, 2)}</pre>
      </details>
    </div>
  );
}
