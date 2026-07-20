"use client";

import { useState } from "react";
import { CreditCard, QrCode, ShieldCheck, ShieldX, Loader2 } from "lucide-react";
import {
  useResolveCardPresentation,
  type CardPresentation,
  type CardResolution,
} from "@/hooks/queries/useCardResolve";

/**
 * Reusable SMART-card presentation field — the web-first "present a card" input
 * for check-in, POS, break-glass and sign-in. The operator enters the card number
 * or pastes a scanned vito QR token (a browser has no NFC/camera scanner, so
 * capture = enter/paste; a device reader is an EXTERNAL_INTEGRATION follow-on).
 *
 * It hands the raw {cardNumber|qrToken} presentation to the host via onPresented,
 * and — when {@code resolvePreview} — resolves it through the B0 seam to show who
 * the card belongs to and whether it is active. Fail-closed: an unresolvable card
 * shows a clean rejection, never a fabricated identity.
 */
export function CardPresentationField({
  onPresented,
  onResolved,
  resolvePreview = false,
  disabled,
}: {
  onPresented: (presentation: CardPresentation | null) => void;
  onResolved?: (resolution: CardResolution | null) => void;
  resolvePreview?: boolean;
  disabled?: boolean;
}) {
  const [mode, setMode] = useState<"number" | "qr">("number");
  const [cardNumber, setCardNumber] = useState("");
  const [qrToken, setQrToken] = useState("");
  const [resolution, setResolution] = useState<CardResolution | null>(null);
  const [error, setError] = useState<string | null>(null);
  const resolve = useResolveCardPresentation();

  function current(): CardPresentation | null {
    if (mode === "number" && cardNumber.trim()) return { cardNumber: cardNumber.trim() };
    if (mode === "qr" && qrToken.trim()) return { qrToken: qrToken.trim() };
    return null;
  }

  function emit(next: CardPresentation | null) {
    onPresented(next);
    setResolution(null);
    setError(null);
    onResolved?.(null);
  }

  async function doResolve() {
    const presentation = current();
    if (!presentation) return;
    setError(null);
    setResolution(null);
    try {
      const res = await resolve.mutateAsync(presentation);
      const data = res?.data ?? null;
      if (!data || !data.healthId) {
        setError("Card could not be resolved (unknown, unverifiable, or expired).");
        onResolved?.(null);
        return;
      }
      setResolution(data);
      onResolved?.(data);
    } catch {
      setError("Card could not be resolved (unknown, unverifiable, or expired).");
      onResolved?.(null);
    }
  }

  return (
    <div className="rounded-lg border p-3 space-y-2" data-testid="card-presentation-field">
      <div className="flex gap-2 text-xs">
        <button
          type="button"
          className={`rounded-md px-2 py-1 border ${mode === "number" ? "bg-primary text-primary-foreground" : "bg-background"}`}
          onClick={() => { setMode("number"); emit(cardNumber.trim() ? { cardNumber: cardNumber.trim() } : null); }}
          disabled={disabled}
        >
          <CreditCard className="h-3.5 w-3.5 inline mr-1" /> Card number
        </button>
        <button
          type="button"
          className={`rounded-md px-2 py-1 border ${mode === "qr" ? "bg-primary text-primary-foreground" : "bg-background"}`}
          onClick={() => { setMode("qr"); emit(qrToken.trim() ? { qrToken: qrToken.trim() } : null); }}
          disabled={disabled}
        >
          <QrCode className="h-3.5 w-3.5 inline mr-1" /> Scan / paste QR
        </button>
      </div>

      {mode === "number" ? (
        <input
          data-testid="card-number-input"
          className="w-full rounded-md border px-3 py-2 bg-background text-sm"
          placeholder="Enter or swipe the card number"
          value={cardNumber}
          disabled={disabled}
          onChange={(e) => { setCardNumber(e.target.value); emit(e.target.value.trim() ? { cardNumber: e.target.value.trim() } : null); }}
        />
      ) : (
        <textarea
          data-testid="card-qr-input"
          className="w-full rounded-md border px-3 py-2 bg-background text-sm font-mono"
          rows={2}
          placeholder="Paste the scanned QR token"
          value={qrToken}
          disabled={disabled}
          onChange={(e) => { setQrToken(e.target.value); emit(e.target.value.trim() ? { qrToken: e.target.value.trim() } : null); }}
        />
      )}

      {resolvePreview && (
        <button
          type="button"
          data-testid="card-resolve-btn"
          className="rounded-md bg-secondary px-3 py-1.5 text-sm disabled:opacity-50"
          onClick={doResolve}
          disabled={disabled || resolve.isPending || !current()}
        >
          {resolve.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : "Resolve card"}
        </button>
      )}

      {resolution && (
        <div className="text-sm" data-testid="card-resolution">
          <p className={`flex items-center gap-1.5 font-medium ${resolution.cardStatus === "ACTIVE" ? "text-green-600" : "text-amber-600"}`}>
            {resolution.cardStatus === "ACTIVE" ? <ShieldCheck className="h-4 w-4" /> : <ShieldX className="h-4 w-4" />}
            Card {resolution.cardStatus ?? "resolved"} — patient {resolution.cpid}
          </p>
        </div>
      )}
      {error && <p className="text-sm text-red-600" data-testid="card-resolve-error">{error}</p>}
    </div>
  );
}
