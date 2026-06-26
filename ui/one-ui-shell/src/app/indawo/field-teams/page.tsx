"use client";

/**
 * Indawo field teams.
 * Route: /indawo/field-teams — rapid-response / inspection team coordination (Indawo SoR).
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Send } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFieldTeams,
  useOutbreaks,
  useDeployTeam,
} from "@/components/facility-mode/usePlaceMode";

export default function IndawoFieldTeamsPage() {
  const { data: teams, isLoading } = useFieldTeams();
  const { data: outbreaks } = useOutbreaks();
  const deploy = useDeployTeam();
  const [target, setTarget] = useState<Record<string, string>>({});

  return (
    <AppLayout>
      <PageShell title="Field teams" subtitle="Rapid-response & inspection coordination">
        <div className="mb-4">
          <Link href="/indawo" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to place mode
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="max-w-3xl">
            <ul className="space-y-2">
              {(teams ?? []).map((t) => (
                <li key={t.teamId} className="rounded-lg border border-border bg-card p-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-sm font-medium text-foreground">{t.name}</span>
                      <p className="text-xs text-muted-foreground">
                        {t.teamType} · {t.memberCount} members
                      </p>
                    </div>
                    <span className={`rounded-full px-2 py-0.5 text-[10px] uppercase ${
                      t.status === "DEPLOYED" ? "bg-amber-500/15 text-amber-600" : "bg-emerald-500/15 text-emerald-600"
                    }`}>
                      {t.status}
                    </span>
                  </div>
                  {t.status !== "DEPLOYED" && (
                    <div className="mt-2 flex items-center gap-2">
                      <select
                        value={target[t.teamId] ?? ""}
                        onChange={(e) => setTarget((p) => ({ ...p, [t.teamId]: e.target.value }))}
                        className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-xs"
                      >
                        <option value="">Select outbreak…</option>
                        {(outbreaks ?? [])
                          .filter((o) => o.status !== "CLOSED")
                          .map((o) => (
                            <option key={o.outbreakId} value={o.outbreakId}>
                              {o.name}
                            </option>
                          ))}
                      </select>
                      <button
                        type="button"
                        disabled={!target[t.teamId] || deploy.isPending}
                        onClick={() => deploy.mutate({ teamId: t.teamId, outbreakId: target[t.teamId] })}
                        className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
                      >
                        <Send className="h-3.5 w-3.5" /> Deploy
                      </button>
                    </div>
                  )}
                </li>
              ))}
              {(teams ?? []).length === 0 && (
                <li className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                  No field teams configured.
                </li>
              )}
            </ul>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
