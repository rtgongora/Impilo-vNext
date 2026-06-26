"use client";

/**
 * MCAZ pharmacovigilance workbench.
 * Route: /work/patient-safety/mcaz
 *
 * Regulator-facing queues: incoming (untriaged) and priority (serious/high). Headline counts come
 * straight from the safety-case system of record. Each row links to the case timeline + actions.
 */

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Loader2, Inbox, Flame } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface CaseSummary {
  id: string;
  caseReference: string;
  status: string;
  priority: string;
  reportType: string;
  serious: boolean;
  causalityAssessment: string;
  reportReference: string | null;
  subjectInitials: string | null;
  primaryProductName: string | null;
  createdAt: string;
}

interface Workbench {
  stats: Record<string, number>;
  incoming: CaseSummary[];
  priority: CaseSummary[];
}

const PRIORITY_TONE: Record<string, string> = {
  URGENT: "bg-red-100 text-red-800",
  HIGH: "bg-amber-100 text-amber-800",
  ROUTINE: "bg-neutral-100 text-neutral-700",
};

export default function McazWorkbenchPage() {
  const { data, isLoading } = useQuery<{ data: Workbench }>({
    queryKey: ["patient-safety-workbench"],
    queryFn: () => apiClient.get(`/internal/v1/patient-safety/mcaz/workbench`),
  });

  const wb = data?.data;

  return (
    <AppLayout>
      <PageShell title="MCAZ pharmacovigilance workbench" subtitle="Incoming and priority safety cases">
        {isLoading ? (
          <div className="flex items-center gap-2 py-12 text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading workbench…
          </div>
        ) : (
          <>
            <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              {wb &&
                Object.entries(wb.stats).map(([k, v]) => (
                  <div key={k} className="rounded-lg border border-border p-3 text-center">
                    <div className="text-2xl font-semibold">{v}</div>
                    <div className="text-xs text-muted-foreground">{k.replace(/_/g, " ")}</div>
                  </div>
                ))}
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
              <Queue title="Incoming (untriaged)" icon={<Inbox className="h-4 w-4" />} cases={wb?.incoming ?? []} />
              <Queue title="Priority (serious / high)" icon={<Flame className="h-4 w-4" />} cases={wb?.priority ?? []} />
            </div>
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Queue({ title, icon, cases }: { title: string; icon: React.ReactNode; cases: CaseSummary[] }) {
  return (
    <section>
      <h2 className="mb-3 flex items-center gap-1.5 text-sm font-semibold text-muted-foreground">
        {icon} {title} ({cases.length})
      </h2>
      {cases.length === 0 ? (
        <div className="rounded-lg border border-dashed border-border p-6 text-center text-sm text-muted-foreground">
          Empty
        </div>
      ) : (
        <ul className="space-y-2">
          {cases.map((c) => (
            <li key={c.id}>
              <Link
                href={`/work/patient-safety/cases/${c.id}`}
                className="block rounded-lg border border-border p-3 hover:bg-muted"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium">
                    {c.caseReference} · {c.reportType}
                  </span>
                  <span className={`rounded px-2 py-0.5 text-xs font-medium ${PRIORITY_TONE[c.priority] ?? ""}`}>
                    {c.priority}
                  </span>
                </div>
                <div className="mt-1 text-sm text-muted-foreground">
                  {c.primaryProductName ?? "—"}
                  {c.subjectInitials ? ` · ${c.subjectInitials}` : ""} · {c.status.replace(/_/g, " ")}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
