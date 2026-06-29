"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** Patient-facing visit status — composed by the BFF patient lane (SYS-3 / G-CT-01). */
interface VisitStatus {
  transactionId: string;
  kind: string;
  available: boolean;
  state?: string;
  stage?: string;
  statusLabel?: string;
  message?: string;
  messageKey?: string;
  facilityId?: string;
  updatedAt?: string;
}

export default function CitizenVisitStatusPage() {
  const params = useParams<{ transactionId: string }>();
  const transactionId = params?.transactionId ?? "";

  const { data, isLoading, isError, refetch } = useQuery<{ data: VisitStatus }>({
    queryKey: ["citizen-visit-status", transactionId],
    queryFn: () => apiClient.get(`/internal/v1/mobile/citizen/visit/${transactionId}/status`),
    enabled: transactionId.length > 0,
    // Visit status changes as the journey advances; keep it fresh while the page is open.
    refetchInterval: 15000,
  });

  const status = data?.data;

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-lg font-semibold text-foreground">My visit</h1>
        <p className="text-xs text-muted-foreground">
          Follow your visit in real time. This updates on its own.
        </p>
      </header>

      {isLoading && (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Checking your visit status…
        </div>
      )}

      {isError && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm">
          <p className="font-medium text-foreground">We couldn&apos;t load your visit status.</p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-2 rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:border-primary/30"
          >
            Try again
          </button>
        </div>
      )}

      {status && !isLoading && (
        <section
          className="rounded-lg border border-border bg-card p-5 space-y-3"
          aria-live="polite"
        >
          <div className="flex items-center justify-between gap-3">
            <span className="text-base font-semibold text-foreground">
              {status.statusLabel ?? "Status unavailable"}
            </span>
            {status.available === false && (
              <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                Unavailable
              </span>
            )}
          </div>
          <p className="text-sm text-foreground">{status.message}</p>
          {status.updatedAt && (
            <p className="text-[11px] text-muted-foreground">
              Updated {new Date(status.updatedAt).toLocaleString()}
            </p>
          )}
        </section>
      )}

      <p className="text-[11px] text-muted-foreground">
        Need help? Speak to the front desk and quote your visit reference.
      </p>
    </div>
  );
}
