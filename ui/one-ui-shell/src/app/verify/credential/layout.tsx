"use client";

import type { ReactNode } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Public credential verification (no auth). Canonical Experience replacement for ui/self-service verify. */
export default function VerifyCredentialLayout({ children }: { children: ReactNode }) {
  return (
    <AppLayout>
      <PageShell
        title="Verify a credential"
        subtitle="Public verifier — set NEXT_PUBLIC_CREDENTIAL_VERIFY_PUBLIC_URL to your credential-verification-service (or gateway) origin"
      >
        {children}
      </PageShell>
    </AppLayout>
  );
}
