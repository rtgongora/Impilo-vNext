"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, Building2, Info, Loader2, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { matchesRequiredRole } from "@/lib/auth/role-groups";
import { useVendorIdentity } from "@/hooks/queries/useCommerceVendor";
import { clearCommerceVendorId, readCommerceVendorId, writeCommerceVendorId } from "@/lib/commerce-vendor-session";

export default function MarketplaceVendorHomePage() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isOperator = matchesRequiredRole(hasRole, "MARKETPLACE_OPS");

  const identity = useVendorIdentity();
  const boundVendorId = identity.data?.vendorId;

  const [overrideId, setOverrideId] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const stored = readCommerceVendorId();
    if (stored) setOverrideId(stored);
  }, []);

  // The scope actually used: the bound vendor identity wins; operators may override.
  const effectiveVendorId = (isOperator && overrideId.trim()) || boundVendorId || overrideId.trim() || "";

  return (
    <AppLayout>
      <PageShell
        title="Vendor workspace"
        subtitle="Your marketplace fulfilment queue. Your vendor identity is resolved from your sign-in; operators may act on a specific vendor by ID."
      >
        <div className="mb-4">
          <Link
            href="/marketplace"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="max-w-3xl space-y-5">
          {/* Auto-bound vendor identity */}
          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <ShieldCheck className="h-4 w-4 text-emerald-600" /> Your vendor identity
            </h2>
            {identity.isLoading ? (
              <p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Resolving your vendor profile…
              </p>
            ) : boundVendorId ? (
              <p className="mt-2 text-sm text-foreground">
                Signed in as vendor <span className="font-mono text-xs break-all">{boundVendorId}</span>.
                Orders below use this scope automatically.
              </p>
            ) : (
              <p className="mt-2 text-sm text-muted-foreground">
                No vendor profile is bound to your account.{" "}
                {isOperator
                  ? "As an operator you can act on a specific vendor by entering its ID below."
                  : "Contact a marketplace operator to bind your vendor account."}
              </p>
            )}
          </div>

          {isOperator ? (
            <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <div className="mb-3 flex items-start gap-3">
                <Info className="mt-0.5 h-5 w-5 shrink-0 text-primary-hover" />
                <div className="text-sm text-muted-foreground">
                  <p className="font-medium text-foreground">Operator override</p>
                  <p className="mt-1">
                    Act on a specific vendor by ID (stored in this browser session only). Leave blank to use your own
                    bound identity.
                  </p>
                </div>
              </div>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <input
                  type="text"
                  value={overrideId}
                  onChange={(e) => {
                    setSaved(false);
                    setOverrideId(e.target.value);
                  }}
                  placeholder="e.g. vendor-facility-001"
                  className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  aria-label="Vendor ID override"
                />
                <button
                  type="button"
                  className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
                  disabled={!overrideId.trim()}
                  onClick={() => {
                    writeCommerceVendorId(overrideId);
                    setSaved(true);
                  }}
                >
                  Save for session
                </button>
                <button
                  type="button"
                  className="rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-background"
                  onClick={() => {
                    clearCommerceVendorId();
                    setOverrideId("");
                    setSaved(false);
                  }}
                >
                  Clear
                </button>
              </div>
              {saved ? <p className="mt-2 text-xs text-primary-hover">Saved. Open vendor orders to load the queue.</p> : null}
            </div>
          ) : null}

          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <Link
              href={effectiveVendorId ? `/marketplace/vendor/orders?vendorId=${encodeURIComponent(effectiveVendorId)}` : "/marketplace/vendor/orders"}
              className="inline-flex items-center gap-2 text-sm font-medium text-primary-hover hover:underline"
            >
              <Building2 className="h-4 w-4" /> Open vendor orders
            </Link>
          </div>

          <p className="text-xs text-muted-foreground">
            <Link href="/finance/commerce-integrations" className="text-primary-hover hover:underline">
              Commerce and payer integration map
            </Link>{" "}
            lists other MSIKA/MusheX gaps outside this vendor slice.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
