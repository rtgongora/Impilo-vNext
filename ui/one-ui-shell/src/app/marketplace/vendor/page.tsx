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
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="max-w-3xl space-y-5">
          <div className="rounded-xl border border-indigo-100 bg-indigo-50/60 p-5">
            <div className="flex items-start gap-3">
              <Info className="h-5 w-5 text-indigo-700 mt-0.5 shrink-0" />
              <div className="text-sm text-indigo-950">
                <p className="font-medium">How this surface works</p>
                <p className="mt-1 text-indigo-900/90">
                  Orders and actions call{" "}
                  <code className="text-xs">/internal/v1/commerce/vendor/…</code> on the Experience BFF. You choose the
                  vendor scope by ID (for example the partner or facility vendor identifier your tenant uses). The value is
                  stored in this browser session only.
                </p>
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Vendor ID for this session</h2>
            <p className="mt-1 text-xs text-slate-500">
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
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                aria-label="Vendor ID"
              />
              <button
                type="button"
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
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
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
                onClick={() => {
                  clearCommerceVendorId();
                  setVendorId("");
                  setSaved(false);
                }}
              >
                Clear
              </button>
            </div>
            {saved ? <p className="mt-2 text-xs text-emerald-700">Saved. Open vendor orders to load the queue.</p> : null}
            <div className="mt-4">
              <Link
                href={vendorId.trim() ? `/marketplace/vendor/orders?vendorId=${encodeURIComponent(vendorId.trim())}` : "/marketplace/vendor/orders"}
                className="inline-flex items-center gap-2 text-sm font-medium text-indigo-700 hover:underline"
              >
                <Building2 className="h-4 w-4" /> Vendor orders
              </Link>
            </div>
          </div>

          <p className="text-xs text-slate-500">
            <Link href="/finance/commerce-integrations" className="text-indigo-700 hover:underline">
              Commerce and payer integration map
            </Link>{" "}
            lists other MSIKA/MusheX gaps outside this vendor slice.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
