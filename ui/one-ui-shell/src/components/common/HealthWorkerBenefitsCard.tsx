"use client";

/**
 * HealthWorkerBenefitsCard — profile surface for recognised health workers.
 *
 * Shows the dual-source recognition status (VARAPI licensed provider /
 * Vashandi workforce) and offers an EXPLICIT opt-in to the governed
 * HEALTH_WORKER_RECOGNITION subsidy programme. The recognition check for
 * the opt-in is performed server-side in coverage (fail-closed); this card
 * never asserts recognition itself. Advantages are administrative/financial
 * only — never clinical priority.
 */

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { BadgeCheck, HandCoins, Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { recognitionLabel, useRecognition } from "@/hooks/queries/useRecognition";

export function HealthWorkerBenefitsCard({ healthId }: { healthId: string | null | undefined }) {
  const { data: recognition, isLoading } = useRecognition(healthId);
  const [message, setMessage] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const optIn = useMutation({
    mutationFn: () =>
      apiClient.post<Record<string, unknown>>("/internal/v1/experience/recognition/subsidy-opt-in", {
        healthId,
      }),
    onSuccess: () =>
      setMessage({ kind: "ok", text: "Enrolled in the health worker subsidy programme." }),
    onError: (err: unknown) => {
      const status = (err as { status?: number })?.status;
      setMessage({
        kind: "err",
        text:
          status === 409
            ? "The health worker programme is not active for your region yet."
            : "Opt-in was not accepted. Your recognition or eligibility could not be confirmed.",
      });
    },
  });

  if (!healthId || isLoading) return null;

  const recognised = recognition?.recognised === true;

  return (
    <section
      data-testid="health-worker-benefits-card"
      className="bg-card rounded-lg border border-border p-5 max-w-xl"
    >
      <div className="flex items-center gap-2 mb-2">
        <HandCoins className="w-4 h-4 text-teal-600" aria-hidden />
        <h3 className="text-sm font-semibold text-foreground">Health worker benefits</h3>
      </div>

      {recognised ? (
        <>
          <p
            data-testid="hw-benefits-status"
            className="flex items-center gap-1.5 text-sm text-foreground mb-1"
          >
            <BadgeCheck className="w-4 h-4 text-teal-600" aria-hidden />
            {recognitionLabel(recognition?.recognitionClass)}
            {recognition?.profession ? ` · ${recognition.profession}` : ""}
          </p>
          <p className="text-xs text-muted-foreground mb-3">
            As a recognised health worker you may opt in to governed fee support when you seek
            care yourself. This never affects queues or clinical priority.
          </p>
          <button
            type="button"
            data-testid="hw-benefits-opt-in"
            onClick={() => optIn.mutate()}
            disabled={optIn.isPending}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-teal-600 text-white hover:bg-teal-700 disabled:opacity-50"
          >
            {optIn.isPending && <Loader2 className="w-4 h-4 animate-spin" aria-hidden />}
            Opt in to health worker subsidy
          </button>
        </>
      ) : (
        <p data-testid="hw-benefits-status" className="text-sm text-muted-foreground">
          No recognised professional registration is currently linked to your Health ID.
        </p>
      )}

      {message && (
        <p
          className={`mt-3 text-sm ${message.kind === "ok" ? "text-green-700" : "text-danger"}`}
          role="status"
        >
          {message.text}
        </p>
      )}
    </section>
  );
}
