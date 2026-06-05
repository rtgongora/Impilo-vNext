"use client";

import Link from "next/link";
import { ArrowLeft, FlaskConical, Loader2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCitizenHealthSummary } from "@/hooks/queries/useCitizenHealthSummary";

function labelFor(row: Record<string, unknown>): string {
  return String(row.display ?? row.name ?? row.code ?? row.testName ?? "Result");
}

function valueFor(row: Record<string, unknown>): string {
  const value = row.value ?? row.resultValue ?? row.quantity;
  const unit = row.unit ?? row.unitCode;
  if (value == null) return "Pending";
  return unit ? `${String(value)} ${String(unit)}` : String(value);
}

function statusFor(row: Record<string, unknown>): string {
  return String(row.status ?? row.interpretation ?? row.clinicalStatus ?? "FINAL");
}

function dateFor(row: Record<string, unknown>): string {
  const raw = row.effectiveDateTime ?? row.issuedAt ?? row.collectedAt ?? row.date;
  if (!raw) return "—";
  const parsed = new Date(String(raw));
  return Number.isNaN(parsed.getTime()) ? String(raw) : parsed.toLocaleDateString();
}

export default function MyResultsPage() {
  const { results, isLoading, error } = useCitizenHealthSummary();

  return (
    <AppLayout>
      <PageShell title="My Results" subtitle="Laboratory and diagnostic results from your citizen health summary">
        <div className="mb-4">
          <Link
            href="/home"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Home
          </Link>
        </div>

        {error ? (
          <div className="bg-white rounded-lg border border-red-200 p-12 text-center">
            <AlertCircle className="w-10 h-10 text-red-300 mx-auto mb-3" />
            <p className="text-red-600 text-sm">Failed to load results</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading results...</span>
          </div>
        ) : results.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <FlaskConical className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-600 text-sm">No results on file yet.</p>
            <p className="text-gray-400 text-xs mt-1">
              Data is composed via <code className="text-[11px]">/internal/v1/citizen/health-summary</code>.
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-gray-100 rounded-xl border border-gray-200 bg-white">
            {results.map((row, index) => (
              <li key={String(row.id ?? index)} className="p-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{labelFor(row)}</p>
                    <p className="text-xs text-gray-500 mt-1">Status: {statusFor(row)}</p>
                    <p className="text-xs text-gray-400 mt-0.5">Collected: {dateFor(row)}</p>
                  </div>
                  <p className="text-sm font-semibold text-impilo-700 shrink-0">{valueFor(row)}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
