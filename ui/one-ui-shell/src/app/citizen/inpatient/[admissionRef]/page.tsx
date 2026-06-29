"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/** Patient-facing inpatient status — composed by the BFF patient lane (SYS-3 / G-CT-01). */
interface InpatientStatus {
  admissionRef: string;
  kind: string;
  available: boolean;
  status?: string;
  stage?: string;
  statusLabel?: string;
  message?: string;
  messageKey?: string;
  ward?: string;
  bed?: string;
}

export default function CitizenInpatientStatusPage() {
  const params = useParams<{ admissionRef: string }>();
  const admissionRef = params?.admissionRef ?? "";

  const { data, isLoading, isError, refetch } = useQuery<{ data: InpatientStatus }>({
    queryKey: ["citizen-inpatient-status", admissionRef],
    queryFn: () => apiClient.get(`/internal/v1/mobile/citizen/inpatient/${admissionRef}/status`),
    enabled: admissionRef.length > 0,
    refetchInterval: 30000,
  });

  const status = data?.data;

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-lg font-semibold text-foreground">My inpatient stay</h1>
        <p className="text-xs text-muted-foreground">Where you are and how your stay is going.</p>
      </header>

      {isLoading && (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Checking your stay…
        </div>
      )}

      {isError && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm">
          <p className="font-medium text-foreground">We couldn&apos;t load your stay status.</p>
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
        <section className="rounded-lg border border-border bg-card p-5 space-y-3" aria-live="polite">
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
          {(status.ward || status.bed) && (
            <dl className="grid grid-cols-2 gap-2 text-xs text-foreground">
              {status.ward && (
                <div>
                  <dt className="text-muted-foreground">Ward</dt>
                  <dd className="font-medium">{status.ward}</dd>
                </div>
              )}
              {status.bed && (
                <div>
                  <dt className="text-muted-foreground">Bed</dt>
                  <dd className="font-medium">{status.bed}</dd>
                </div>
              )}
            </dl>
          )}
        </section>
      )}
    </div>
  );
}
