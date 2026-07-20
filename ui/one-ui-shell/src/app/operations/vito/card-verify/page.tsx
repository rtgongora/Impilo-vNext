"use client";

import { useState } from "react";
import { CreditCard } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CardPresentationField } from "@/components/card/CardPresentationField";
import type { CardResolution } from "@/hooks/queries/useCardResolve";

/**
 * Card present-to-verify (POS / desk sign-in). An operator enters a card number or
 * pastes a scanned QR; the card is resolved through the B0 seam to the patient and
 * card status. This is the "who holds this card" check behind card sign-in and a
 * point-of-service identity confirmation. Fail-closed: an unresolvable card is a
 * clean rejection, never a fabricated identity.
 */
export default function CardVerifyWorkspace() {
  const [resolution, setResolution] = useState<CardResolution | null>(null);

  return (
    <AppLayout>
      <PageShell
        title="Verify a presented card"
        subtitle="Resolve a SMART card (number or scanned QR) to confirm who holds it and whether it is active."
        icon={<CreditCard className="h-5 w-5" />}
      >
        <div className="max-w-xl space-y-4">
          <CardPresentationField
            onPresented={() => setResolution(null)}
            onResolved={setResolution}
            resolvePreview
          />

          {resolution && (
            <div className="rounded-lg border p-4 space-y-1 text-sm" data-testid="card-verify-result">
              <p><span className="text-muted-foreground">Patient (CPID):</span> <span className="font-mono">{resolution.cpid}</span></p>
              <p><span className="text-muted-foreground">Card:</span> {resolution.cardNumber ?? "—"} · {resolution.cardForm ?? "—"}</p>
              <p>
                <span className="text-muted-foreground">Status:</span>{" "}
                <span className={resolution.cardStatus === "ACTIVE" ? "text-green-600 font-medium" : "text-amber-600 font-medium"}>
                  {resolution.cardStatus}
                </span>
              </p>
              <p className="text-muted-foreground text-xs">Resolved via {resolution.resolvedVia}</p>
            </div>
          )}

          <p className="text-xs text-muted-foreground">
            A browser has no card reader — enter the number or paste a scanned QR token. A physical
            NFC/QR reader is an integration follow-on. Only an ACTIVE card should authorize a
            point-of-service action.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
