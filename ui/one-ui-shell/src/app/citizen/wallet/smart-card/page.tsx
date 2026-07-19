"use client";

/**
 * My Digital SMART Card — the citizen's card at parity with the physical card, in the browser.
 * Route: /citizen/wallet/smart-card
 *
 * Surfaces all three card functions (identity, pay, health) via CitizenSmartCardPanel, which
 * composes the DigitalCard tabs plus device enrollment and "help pay my bill" sharing.
 */

import { CreditCard } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { CitizenSmartCardPanel } from "@/components/wallet/CitizenSmartCardPanel";

export default function DigitalSmartCardPage() {
  const actorId = useAuthStore((s) => s.user?.id) ?? "";

  return (
    <AppLayout>
      <PageShell
        title="My Digital SMART Card"
        subtitle="Your identity, pay, and health record — one card, in your browser"
        icon={<CreditCard className="h-6 w-6" />}
      >
        {!actorId ? (
          <p className="rounded-lg border border-amber-100 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
            Sign in to open your digital card.
          </p>
        ) : (
          <CitizenSmartCardPanel enabled={Boolean(actorId)} />
        )}
      </PageShell>
    </AppLayout>
  );
}
