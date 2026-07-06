"use client";

/**
 * Person trust profile — the four-block doctrine view for the signed-in person.
 * Route: /citizen/wallet/trust
 *
 * Health OS §5–§6: one person anchor, many trust facets (identity,
 * professional, employment, operational), each citing its own system of record
 * and degrading independently. Surfaced inside the unified Experience shell as
 * part of the Person Health Wallet.
 */

import { ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { TrustProfilePanel } from "@/components/trust/TrustProfilePanel";

export default function WalletTrustProfilePage() {
  return (
    <AppLayout>
      <PageShell
        title="Trust Profile"
        subtitle="Your identity, professional, employment, and operational trust — each from its own system of record"
        icon={<ShieldCheck className="w-5 h-5" />}
      >
        <TrustProfilePanel />
      </PageShell>
    </AppLayout>
  );
}
