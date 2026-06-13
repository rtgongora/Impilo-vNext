"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, Building2, Info } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { clearCommerceVendorId, readCommerceVendorId, writeCommerceVendorId } from "@/lib/commerce-vendor-session";

export default function MarketplaceVendorHomePage() {
  const [vendorId, setVendorId] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const stored = readCommerceVendorId();
    if (stored) setVendorId(stored);
  }, []);

  return (
    <AppLayout>
      <PageShell
        title="Vendor workspace"
        subtitle="Trusted Experience-operator view of vendor fulfilment (MSIKA Flow via BFF). This is not a separate vendor login or actor plane."
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
          <div className="rounded-xl border border-indigo-100 bg-info-soft/60 p-5">
            <div className="flex items-start gap-3">
              <Info className="h-5 w-5 text-primary-hover mt-0.5 shrink-0" />
              <div className="text-sm text-primary-hover">
                <p className="font-medium">How this surface works</p>
                <p className="mt-1 text-primary-hover/90">
                  Orders and actions call{" "}
                  <code className="text-xs">/internal/v1/commerce/vendor/…</code> on the Experience BFF. You choose the
                  vendor scope by ID (for example the partner or facility vendor identifier your tenant uses). The value is
                  stored in this browser session only.
                </p>
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Vendor ID for this session</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Required for <code className="text-[11px]">GET …/vendor/&#123;vendorId&#125;/orders</code> and accept-order.
            </p>
            <div className="mt-3 flex flex-col gap-2 sm:flex-row sm:items-center">
              <input
                type="text"
                value={vendorId}
                onChange={(e) => {
                  setSaved(false);
                  setVendorId(e.target.value);
                }}
                placeholder="e.g. vendor-facility-001"
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                aria-label="Vendor ID"
              />
              <button
                type="button"
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
                disabled={!vendorId.trim()}
                onClick={() => {
                  writeCommerceVendorId(vendorId);
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
                  setVendorId("");
                  setSaved(false);
                }}
              >
                Clear
              </button>
            </div>
            {saved ? <p className="mt-2 text-xs text-primary-hover">Saved. Open vendor orders to load the queue.</p> : null}
            <div className="mt-4">
              <Link
                href={vendorId.trim() ? `/marketplace/vendor/orders?vendorId=${encodeURIComponent(vendorId.trim())}` : "/marketplace/vendor/orders"}
                className="inline-flex items-center gap-2 text-sm font-medium text-primary-hover hover:underline"
              >
                <Building2 className="h-4 w-4" /> Vendor orders
              </Link>
            </div>
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
