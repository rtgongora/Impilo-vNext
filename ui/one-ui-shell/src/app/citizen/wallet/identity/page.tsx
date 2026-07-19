"use client";

/**
 * Wallet · Identity & trust. Reuses the canonical IdentityAssuranceBanner (assurance state, reason,
 * available permissions, upgrade requirements, pathways, next best step) plus the Health-ID / profile
 * summary from the wallet overview (VITO). No new identity record is created here.
 */

import Link from "next/link";
import { IdentityAssuranceBanner } from "@/components/citizen/IdentityAssuranceBanner";
import { ReportLostCard } from "@/components/citizen/ReportLostCard";
import { useWalletOverview } from "@/hooks/queries/useWallet";

export default function WalletIdentityPage() {
  const { data, isLoading } = useWalletOverview();
  const identity = (data?.data?.identity ?? {}) as Record<string, unknown>;
  const profile = identity.profile as Record<string, unknown> | undefined;

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-2">
      <div>
        <h1 className="text-xl font-semibold text-foreground">Identity & trust</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Your Health ID, current trust level, and what you can do now — with the next steps to raise
          your assurance.
        </p>
      </div>

      <IdentityAssuranceBanner />

      <section className="rounded-lg border border-border bg-card p-4">
        <h2 className="mb-2 text-sm font-medium text-foreground">Health ID</h2>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : (
          <dl className="space-y-1 text-sm">
            <div className="flex justify-between">
              <dt className="text-muted-foreground">Health ID</dt>
              <dd className="font-mono text-foreground">{String(identity.healthId ?? "—")}</dd>
            </div>
            {profile ? (
              <>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Name</dt>
                  <dd className="text-foreground">
                    {String(profile.givenName ?? "")} {String(profile.familyName ?? "")}
                  </dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-muted-foreground">Date of birth</dt>
                  <dd className="text-foreground">{String(profile.dateOfBirth ?? "—")}</dd>
                </div>
              </>
            ) : null}
          </dl>
        )}
        <p className="mt-2 text-[10px] uppercase tracking-wide text-muted-foreground/70">
          Source: VITO · Identity assurance
        </p>
      </section>

      <ReportLostCard />

      <div className="flex flex-wrap gap-3 text-sm">
        <Link href="/citizen/health-id/qr" className="text-primary-hover underline">
          Show my Health ID QR
        </Link>
        <Link href="/citizen/id-recovery" className="text-primary-hover underline">
          Recover or correct my ID
        </Link>
      </div>
    </div>
  );
}
