"use client";

import Link from "next/link";
import { ArrowLeft, Loader2, AlertCircle, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCitizenHealthSummary } from "@/hooks/queries/useCitizenHealthSummary";

function allergenFor(row: Record<string, unknown>): string {
  return String(row.substance ?? row.allergen ?? "Allergen");
}

function severityFor(row: Record<string, unknown>): string {
  return String(row.criticality ?? row.severity ?? "MODERATE");
}

const SEVERITY_STYLES: Record<string, string> = {
  SEVERE: "bg-red-100 text-red-800",
  LIFE_THREATENING: "bg-red-200 text-red-900",
  MODERATE: "bg-amber-100 text-amber-800",
  MILD: "bg-gray-100 text-gray-700",
};

export default function MyAllergiesPage() {
  const { allergies, isLoading, error } = useCitizenHealthSummary();

  return (
    <AppLayout>
      <PageShell title="My Allergies" subtitle="Known allergies from your citizen health summary">
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
            <p className="text-red-600 text-sm">Failed to load allergies</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading allergies...</span>
          </div>
        ) : allergies.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <AlertTriangle className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-600 text-sm">No known allergies on file.</p>
          </div>
        ) : (
          <ul className="divide-y divide-gray-100 rounded-xl border border-gray-200 bg-white">
            {allergies.map((row, index) => {
              const severity = severityFor(row);
              const style = SEVERITY_STYLES[severity] ?? SEVERITY_STYLES.MODERATE;
              return (
                <li key={String(row.id ?? index)} className="p-4 flex justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-gray-900">{allergenFor(row)}</p>
                    <p className="text-xs text-gray-500 mt-1">
                      Reaction: {String(row.reaction ?? "Not specified")}
                    </p>
                  </div>
                  <span className={`self-start rounded-full px-2 py-0.5 text-[10px] font-semibold ${style}`}>
                    {severity}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
