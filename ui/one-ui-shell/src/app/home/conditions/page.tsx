"use client";

import Link from "next/link";
import { ArrowLeft, FlaskConical, Loader2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCitizenHealthSummary } from "@/hooks/queries/useCitizenHealthSummary";

function labelFor(row: Record<string, unknown>): string {
  return String(row.display ?? row.name ?? row.code ?? "Condition");
}

function statusFor(row: Record<string, unknown>): string {
  return String(row.clinicalStatus ?? row.status ?? "ACTIVE");
}

export default function MyConditionsPage() {
  const { conditions, isLoading, error } = useCitizenHealthSummary();

  return (
    <AppLayout>
      <PageShell title="My Conditions" subtitle="Active problems from your citizen health summary">
        <div className="mb-4">
          <Link
            href="/home"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Home
          </Link>
        </div>

        {error ? (
          <div className="bg-card rounded-lg border border-danger/28 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load conditions</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading conditions...</span>
          </div>
        ) : conditions.length === 0 ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <FlaskConical className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No conditions on file yet.</p>
            <p className="text-muted-foreground text-xs mt-1">
              Data is composed from PCT via <code className="text-[11px]">/internal/v1/citizen/health-summary</code>.
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-gray-100 rounded-xl border border-border bg-card">
            {conditions.map((row, index) => (
              <li key={String(row.id ?? index)} className="p-4">
                <p className="text-sm font-medium text-foreground">{labelFor(row)}</p>
                <p className="text-xs text-muted-foreground mt-1">Status: {statusFor(row)}</p>
                {row.onsetDate ? (
                  <p className="text-xs text-muted-foreground mt-0.5">Onset: {String(row.onsetDate)}</p>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
