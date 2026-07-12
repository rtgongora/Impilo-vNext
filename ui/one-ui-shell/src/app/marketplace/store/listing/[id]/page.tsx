"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Heart, Flag, Lock, Stethoscope } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { AddToCartButton } from "@/components/marketplace/AddToCartButton";
import { useListing, useFavouriteListing, useReportListing } from "@/hooks/queries/useMsikaStore";

const REGULATED = new Set(["HIGH_RISK", "REGULATED"]);

export default function ListingDetailPage() {
  const params = useParams();
  const listingId = String(params?.id ?? "");
  const listingQuery = useListing(listingId);
  const favourite = useFavouriteListing();
  const report = useReportListing();
  const [reporting, setReporting] = useState(false);
  const [reportText, setReportText] = useState("");

  const listing = listingQuery.data;
  const regulated = listing ? REGULATED.has(listing.riskClassification) : false;
  const clinical = !!listing?.clinicalRoute;
  const bookable = listing?.fulfilmentMode === "IN_PERSON" || listing?.fulfilmentMode === "VIRTUAL";

  return (
    <AppLayout>
      <PageShell title={listing?.title ?? "Listing"} subtitle="Marketplace listing" serviceSlug="msika">
        <div className="space-y-6">
          <Link href="/marketplace/store/search" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to search
          </Link>

          <NompiloContextualGuidance routePath="/marketplace/store/listing" />

          {listingQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">Loading listing…</p>
          ) : listingQuery.isError || !listing ? (
            <p className="rounded-lg border border-border bg-card p-4 text-sm text-red-600">
              This listing is not available. It may be unpublished or you may not have access.
            </p>
          ) : (
            <>
              <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className="rounded-full border border-border px-2 py-0.5">{listing.riskClassification.replace("_", " ")}</span>
                  <span className="rounded-full border border-border px-2 py-0.5">{listing.fulfilmentMode ?? "Fulfilment via owner"}</span>
                  <span className="rounded-full border border-border px-2 py-0.5">Seller: {listing.sellerType}</span>
                </div>
                {listing.summary ? <p className="mt-3 text-sm text-foreground">{listing.summary}</p> : null}
                <div className="mt-4 text-sm text-muted-foreground">
                  Price: <span className="font-medium text-foreground">{listing.priceDisplay ?? "Set by the seller — confirmed at checkout"}</span>
                </div>
              </section>

              {regulated ? (
                <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-warning-soft px-4 py-3 text-sm text-amber-800">
                  <Lock className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>
                    This is a regulated item. An eligibility or licence check runs before checkout, and the order may route
                    to a provider or clinical order ({listing.clinicalRoute ?? "PCT/OROS"}). Msika never bypasses the clinical owner.
                  </span>
                </div>
              ) : null}

              <section className="flex flex-wrap gap-3">
                {clinical ? (
                  <Link
                    href="/care/referrals"
                    className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground"
                  >
                    <Stethoscope className="h-4 w-4" /> Request via care
                  </Link>
                ) : bookable ? (
                  <Link
                    href={`/marketplace/bookings?listing=${listing.listingId}`}
                    className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground"
                  >
                    Book
                  </Link>
                ) : (
                  <AddToCartButton listingId={listing.listingId} label="Add to cart" />
                )}
                <button
                  onClick={() => favourite.mutate(listing.listingId)}
                  disabled={favourite.isPending}
                  className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2 font-medium text-foreground hover:bg-background"
                >
                  <Heart className="h-4 w-4" /> {favourite.isSuccess ? "Saved" : "Save"}
                </button>
                <button
                  onClick={() => setReporting((v) => !v)}
                  className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2 font-medium text-foreground hover:bg-background"
                >
                  <Flag className="h-4 w-4" /> Report
                </button>
              </section>

              {reporting ? (
                <section className="rounded-2xl border border-border bg-card p-4 shadow-sm">
                  <p className="text-sm text-muted-foreground">
                    Your report goes to Rito, the quality & safety service. Msika links it but does not own the case.
                  </p>
                  <textarea
                    value={reportText}
                    onChange={(e) => setReportText(e.target.value)}
                    placeholder="Describe the concern"
                    className="mt-2 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
                    rows={3}
                  />
                  <button
                    onClick={() =>
                      report.mutate(
                        { title: `Listing report: ${listing.title}`, description: reportText, listingId: listing.listingId },
                        { onSuccess: () => setReporting(false) },
                      )
                    }
                    disabled={report.isPending || !reportText.trim()}
                    className="mt-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
                  >
                    {report.isPending ? "Sending…" : "Send to Rito"}
                  </button>
                  {report.isSuccess ? <p className="mt-2 text-xs text-emerald-700">Reported. Rito has the case.</p> : null}
                </section>
              ) : null}
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
