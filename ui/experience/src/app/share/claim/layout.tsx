"use client";

import type { ReactNode } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Public-ish claim flow (OTP); not gated to CITIZEN actor type. */
export default function ShareClaimLayout({ children }: { children: ReactNode }) {
  return (
    <AppLayout>
      <PageShell title="Claim shared documents" subtitle="Share-slip service (configure NEXT_PUBLIC_SHARE_SLIP_PUBLIC_URL)">
        {children}
      </PageShell>
    </AppLayout>
  );
}
