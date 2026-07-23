"use client";

/**
 * OF-B7 — /my/orders/[requestId]/offers — the citizen's offer-comparison and
 * selection page for a marketplace (request-for-offer) order.
 *
 * The whole §8.6/§11.6 contract lives in OfferComparisonSurface; this page
 * only anchors the route, layout and header. PII floor: the surface renders
 * the §11.8-minimised request snapshot only — coded items and quantities,
 * never patient identity, diagnosis or prescriber identity.
 */

import { useParams } from "next/navigation";
import { Scale } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { OfferComparisonSurface } from "@/components/marketplace/offer-comparison/OfferComparisonSurface";

export default function MyOrderOffersPage() {
  const params = useParams();
  const requestId = String(params?.requestId ?? "");

  return (
    <AppLayout>
      <PageShell
        title="Compare offers"
        subtitle="See every offer for your order, understand why each is ranked where it is, and choose — nothing is hidden and estimates are never final."
        icon={<Scale className="h-5 w-5" />}
        density="compact"
      >
        <OfferComparisonSurface requestId={requestId} />
      </PageShell>
    </AppLayout>
  );
}
