"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useCreateListing } from "@/hooks/queries/useMsikaStore";

export default function NewListingPage() {
  const router = useRouter();
  const create = useCreateListing();
  const [form, setForm] = useState({
    catalogItemId: "",
    sellerType: "PROVIDER",
    sellerId: "",
    title: "",
    summary: "",
    riskClassification: "LOW_RISK",
    clinicalRoute: "",
    priceDisplay: "",
    fulfilmentMode: "IN_PERSON",
  });

  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const submit = () => {
    const body: Record<string, unknown> = { ...form };
    if (!form.clinicalRoute) delete body.clinicalRoute;
    create.mutate(body, { onSuccess: () => router.push("/marketplace/seller/listings") });
  };

  return (
    <AppLayout>
      <PageShell title="New Listing" subtitle="Create a listing over a catalogue item. It starts as a draft, then is submitted for moderation." serviceSlug="msika">
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/seller/listings/new" />

          <section className="grid gap-4 rounded-2xl border border-border bg-card p-5 shadow-sm md:grid-cols-2">
            <label className="text-sm md:col-span-2">
              <span className="mb-1 block font-medium text-foreground">Catalogue item ID</span>
              <input value={form.catalogItemId} onChange={(e) => set("catalogItemId", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2" placeholder="msika catalog item ULID" />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Seller type</span>
              <select value={form.sellerType} onChange={(e) => set("sellerType", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2">
                {["PROVIDER", "FACILITY", "SITE", "PROGRAMME", "VENDOR"].map((t) => <option key={t}>{t}</option>)}
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Seller ID</span>
              <input value={form.sellerId} onChange={(e) => set("sellerId", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2" />
            </label>
            <label className="text-sm md:col-span-2">
              <span className="mb-1 block font-medium text-foreground">Title</span>
              <input value={form.title} onChange={(e) => set("title", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2" />
            </label>
            <label className="text-sm md:col-span-2">
              <span className="mb-1 block font-medium text-foreground">Summary</span>
              <textarea value={form.summary} onChange={(e) => set("summary", e.target.value)} rows={2} className="w-full rounded-lg border border-border bg-background px-3 py-2" />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Risk classification</span>
              <select value={form.riskClassification} onChange={(e) => set("riskClassification", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2">
                {["UNRESTRICTED", "LOW_RISK", "MODERATE_RISK", "HIGH_RISK", "REGULATED"].map((t) => <option key={t}>{t}</option>)}
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Clinical route (regulated)</span>
              <select value={form.clinicalRoute} onChange={(e) => set("clinicalRoute", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2">
                <option value="">None</option>
                <option value="PCT">PCT</option>
                <option value="OROS">OROS</option>
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Price display</span>
              <input value={form.priceDisplay} onChange={(e) => set("priceDisplay", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2" placeholder="display only — truth is Costa" />
            </label>
            <label className="text-sm">
              <span className="mb-1 block font-medium text-foreground">Fulfilment mode</span>
              <select value={form.fulfilmentMode} onChange={(e) => set("fulfilmentMode", e.target.value)} className="w-full rounded-lg border border-border bg-background px-3 py-2">
                {["IN_PERSON", "VIRTUAL", "PICKUP", "DELIVERY"].map((t) => <option key={t}>{t}</option>)}
              </select>
            </label>
          </section>

          <div className="flex items-center gap-3">
            <button
              onClick={submit}
              disabled={create.isPending || !form.catalogItemId || !form.title || !form.sellerId}
              className="rounded-lg bg-primary px-5 py-2 font-medium text-primary-foreground disabled:opacity-50"
            >
              {create.isPending ? "Creating…" : "Create draft"}
            </button>
            {create.isError ? <span className="text-sm text-red-600">Could not create listing.</span> : null}
          </div>
          <p className="rounded-lg border border-border bg-background px-4 py-2 text-xs text-muted-foreground">
            Stock stays with Dura/inventory, billing with Costa, payment with MusheX. A listing only references them.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
