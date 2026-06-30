"use client";

import Link from "next/link";
import { PlusCircle, ListChecks, ShieldCheck, Gavel } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";

export default function SellerCentrePage() {
  return (
    <AppLayout>
      <PageShell
        title="Seller Centre"
        subtitle="List and manage what you offer on the marketplace. Your provider, facility or programme identity is verified before regulated listings can go live."
        serviceSlug="msika"
      >
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/seller" />

          <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Link href="/marketplace/seller/listings/new" className="rounded-2xl border border-border bg-card p-4 shadow-sm hover:border-primary">
              <PlusCircle className="h-5 w-5 text-primary" />
              <div className="mt-2 font-semibold text-foreground">New listing</div>
              <p className="text-sm text-muted-foreground">Create a listing over a catalogue item.</p>
            </Link>
            <Link href="/marketplace/seller/listings" className="rounded-2xl border border-border bg-card p-4 shadow-sm hover:border-primary">
              <ListChecks className="h-5 w-5 text-primary" />
              <div className="mt-2 font-semibold text-foreground">My listings & orders</div>
              <p className="text-sm text-muted-foreground">Track status and incoming orders.</p>
            </Link>
            <Link href="/marketplace/seller/moderation" className="rounded-2xl border border-border bg-card p-4 shadow-sm hover:border-primary">
              <Gavel className="h-5 w-5 text-primary" />
              <div className="mt-2 font-semibold text-foreground">Moderation queue</div>
              <p className="text-sm text-muted-foreground">Operators: approve, reject or suspend.</p>
            </Link>
            <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
              <ShieldCheck className="h-5 w-5 text-emerald-600" />
              <div className="mt-2 font-semibold text-foreground">Verified selling</div>
              <p className="text-sm text-muted-foreground">Varapi/Tuso/Indawo verify your identity; Dura owns stock.</p>
            </div>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
