"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Printer } from "lucide-react";
import { ActivationLetterPrint } from "@/components/governance/ActivationLetterPrint";

/**
 * Printable activation letter generator — the paper onboarding channel.
 * Reached from governance surfaces with prefilled query params
 * (?name=…&providerId=…&code=…&expiresAt=…&facility=…) or filled in manually.
 * Credentials never appear here; the letter carries the activation action only.
 */
function ActivationLetterPageInner() {
  const params = useSearchParams();
  const [displayName, setDisplayName] = useState(params.get("name") ?? "");
  const [providerId, setProviderId] = useState(params.get("providerId") ?? "");
  const [activationCode, setActivationCode] = useState(params.get("code") ?? "");
  const [expiresAt, setExpiresAt] = useState(params.get("expiresAt") ?? "");
  const [facilityName, setFacilityName] = useState(params.get("facility") ?? "");
  const activationUrl =
    params.get("activationUrl") ??
    (typeof window !== "undefined" ? `${window.location.origin}/auth/login` : "https://impilo.mohcc.gov.zw/auth/login");

  return (
    <div className="space-y-6">
      <header className="flex items-start justify-between gap-4 print:hidden">
        <div>
          <h1 className="text-lg font-semibold text-foreground">Activation letter</h1>
          <p className="text-xs text-muted-foreground">
            Printable onboarding letter for providers without reliable email/SMS. No passwords, no clinical content.
          </p>
        </div>
        <button
          type="button"
          onClick={() => window.print()}
          className="inline-flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-white hover:bg-primary-hover"
        >
          <Printer className="h-4 w-4" /> Print letter
        </button>
      </header>

      <section className="grid gap-3 rounded-lg border border-border bg-card p-4 sm:grid-cols-2 print:hidden">
        <label className="block text-sm text-foreground">
          Recipient name
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-sm text-foreground">
          Provider ID
          <input value={providerId} onChange={(e) => setProviderId(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-sm text-foreground">
          One-time activation code
          <input value={activationCode} onChange={(e) => setActivationCode(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-sm text-foreground">
          Expiry date
          <input value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm" />
        </label>
        <label className="block text-sm text-foreground sm:col-span-2">
          Facility / workplace
          <input value={facilityName} onChange={(e) => setFacilityName(e.target.value)} className="mt-1 w-full rounded border border-border px-3 py-2 text-sm" />
        </label>
      </section>

      <section className="rounded-lg border border-border bg-white">
        <ActivationLetterPrint
          displayName={displayName || "Colleague"}
          providerId={providerId || undefined}
          activationUrl={activationUrl}
          activationCode={activationCode || undefined}
          expiresAt={expiresAt || undefined}
          facilityName={facilityName || undefined}
        />
      </section>
    </div>
  );
}

export default function ActivationLetterPage() {
  return (
    <Suspense fallback={null}>
      <ActivationLetterPageInner />
    </Suspense>
  );
}
