"use client";

import { useState } from "react";
import { CreditCard, Loader2, CheckCircle } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import type { IssuanceRequest } from "@/hooks/queries/useCardIssuance";
import { useIssuanceStatus } from "@/hooks/queries/useCardIssuance";

/**
 * B3: citizen self-service request for a FRESH physical SMART card. Distinct from
 * lost/stolen replacement — a fresh card is submitted on the governed PORTAL
 * issuance channel, which auto-routes to PROOFING (an in-person identity check)
 * before the card is produced. Shows the request state so the citizen can track it.
 */
export function RequestPhysicalCard() {
  const [request, setRequest] = useState<IssuanceRequest | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const status = useIssuanceStatus(request?.id ?? null);
  const live = status.data?.data ?? request;

  async function submit() {
    setError(null);
    setBusy(true);
    try {
      const res = await apiClient.post<{ data: IssuanceRequest }>(
        "/internal/v1/wallet/cards/request-physical",
        {},
      );
      setRequest(res?.data ?? null);
    } catch {
      setError("Could not submit your request right now. Please try again shortly.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rounded-xl border border-border p-4 space-y-3" data-testid="request-physical-card">
      <div className="flex items-center gap-2">
        <CreditCard className="h-4 w-4 text-muted-foreground" />
        <h3 className="text-sm font-semibold text-foreground">Request a physical card</h3>
      </div>
      <p className="text-xs text-muted-foreground">
        Get a printed SMART card in addition to your digital one. You&apos;ll be asked to verify your
        identity in person (proofing) before it&apos;s produced.
      </p>

      {!live ? (
        <button
          type="button"
          data-testid="request-physical-btn"
          onClick={submit}
          disabled={busy}
          className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
        >
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}
          Request a physical card
        </button>
      ) : (
        <div className="rounded-lg border bg-muted/40 px-3 py-2 text-sm" data-testid="issuance-status">
          <p className="flex items-center gap-1.5 font-medium text-foreground">
            <CheckCircle className="h-4 w-4 text-green-600" /> Request submitted
          </p>
          <p className="text-xs text-muted-foreground mt-0.5">
            Reference #{live.id} · status <span className="font-medium">{live.state}</span>
            {live.state === "PROOFING" && " — please attend your identity-proofing appointment."}
          </p>
        </div>
      )}
      {error && <p className="text-sm text-red-600" data-testid="request-physical-error">{error}</p>}
    </div>
  );
}
