"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { PageShell } from "@/components/PageShell";

type ApiData<T = unknown> = { data: T };
type ProgrammeRow = { programme: string; courses?: number; enrolments?: number; completed?: number; completionRatePercent?: number };
type RegulatorRow = { providerKind: string; total?: number; accredited?: number };

function useProgrammeSummary() {
  return useQuery<ApiData<{ items?: ProgrammeRow[] }>>({
    queryKey: ["fundo", "reports", "programme-summary"],
    queryFn: () => apiClient.get("/internal/v1/learning/v11/reports/programme-summary"),
  });
}
function useRegulatorSummary() {
  return useQuery<ApiData<{ items?: RegulatorRow[] }>>({
    queryKey: ["fundo", "reports", "regulator-summary"],
    queryFn: () => apiClient.get("/internal/v1/learning/v11/reports/regulator-summary"),
  });
}

export default function DashboardsPage() {
  const { data: prog } = useProgrammeSummary();
  const { data: reg } = useRegulatorSummary();
  const programmes = (((prog?.data as { items?: ProgrammeRow[] } | undefined)?.items) ?? []);
  const regulators = (((reg?.data as { items?: RegulatorRow[] } | undefined)?.items) ?? []);

  return (
    <PageShell title="Programme & regulator dashboards" subtitle="Programme completion rollups and provider accreditation posture.">
      <section className="rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Programme summary</p>
        <ul className="mt-2 space-y-1 text-sm" data-testid="programme-summary">
          {programmes.map((p) => (
            <li key={p.programme} className="rounded border border-border px-3 py-1.5">
              {p.programme} · {p.courses ?? 0} courses · {p.enrolments ?? 0} enrolments · {p.completionRatePercent ?? 0}% complete
            </li>
          ))}
          {programmes.length === 0 && <li className="text-muted-foreground">No programme data yet.</li>}
        </ul>
      </section>
      <section className="mt-4 rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Regulator: provider accreditation</p>
        <ul className="mt-2 space-y-1 text-sm" data-testid="regulator-summary">
          {regulators.map((r) => (
            <li key={r.providerKind} className="rounded border border-border px-3 py-1.5">
              {r.providerKind} · {r.accredited ?? 0}/{r.total ?? 0} accredited
            </li>
          ))}
          {regulators.length === 0 && <li className="text-muted-foreground">No providers registered yet.</li>}
        </ul>
      </section>
    </PageShell>
  );
}
