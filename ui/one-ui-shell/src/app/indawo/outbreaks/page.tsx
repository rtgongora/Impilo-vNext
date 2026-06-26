"use client";

/**
 * Indawo outbreaks management.
 * Route: /indawo/outbreaks — declare and track public-health outbreaks (Indawo SoR).
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Plus, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useOutbreaks, useCreateOutbreak } from "@/components/facility-mode/usePlaceMode";

export default function IndawoOutbreaksPage() {
  const { data: outbreaks, isLoading } = useOutbreaks();
  const create = useCreateOutbreak();
  const [name, setName] = useState("");
  const [conditionCode, setConditionCode] = useState("");
  const [severity, setSeverity] = useState("MEDIUM");

  return (
    <AppLayout>
      <PageShell title="Outbreaks" subtitle="Declare and track public-health outbreaks">
        <div className="mb-4">
          <Link href="/indawo" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to place mode
          </Link>
        </div>

        <div className="max-w-3xl space-y-6">
          <div className="rounded-lg border border-border bg-card p-4">
            <h3 className="mb-3 text-sm font-medium text-foreground">Declare outbreak</h3>
            <div className="flex flex-wrap gap-2">
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Outbreak name"
                className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
              <input value={conditionCode} onChange={(e) => setConditionCode(e.target.value)} placeholder="Condition code"
                className="w-40 rounded-md border border-border bg-background px-2 py-1.5 text-sm" />
              <select value={severity} onChange={(e) => setSeverity(e.target.value)}
                className="w-28 rounded-md border border-border bg-background px-2 py-1.5 text-sm">
                {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              <button type="button" disabled={!name.trim() || !conditionCode.trim() || create.isPending}
                onClick={() =>
                  create.mutate(
                    { name: name.trim(), conditionCode: conditionCode.trim(), severity },
                    { onSuccess: () => { setName(""); setConditionCode(""); } },
                  )
                }
                className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50">
                <Plus className="h-4 w-4" /> Declare
              </button>
            </div>
          </div>

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : (
            <ul className="space-y-2">
              {(outbreaks ?? []).map((o) => (
                <li key={o.outbreakId} className="rounded-lg border border-border bg-card p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium text-foreground">{o.name}</span>
                    <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase text-muted-foreground">
                      {o.status}
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {o.conditionCode} · {o.severity} · {o.caseCount} cases · {o.deathCount} deaths
                  </p>
                </li>
              ))}
              {(outbreaks ?? []).length === 0 && (
                <li className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                  No outbreaks recorded.
                </li>
              )}
            </ul>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
