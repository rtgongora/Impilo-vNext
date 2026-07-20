"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { GENERIC_RECORD_COLUMNS } from "@/lib/json-api/generic-table-columns";
import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Loader2, Ticket } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCommerceClaimPickup, useCommerceIssuePickup } from "@/hooks/queries/useCommercePickup";
import { FacilitiesGeoMapPanel } from "@/components/maps/FacilitiesGeoMapPanel";
import { BiometricVerifyField } from "@/components/biometric/BiometricVerifyField";
import type { Modality } from "@/hooks/queries/useAbisBiometric";

const DEFAULT_CLAIM_JSON = "{\n  \"pickupToken\": \"\",\n  \"claimedBy\": \"\",\n  \"claimantId\": \"\"\n}\n";

export default function MarketplacePickupPage() {
  const [orderId, setOrderId] = useState("");
  const [claimJson, setClaimJson] = useState(DEFAULT_CLAIM_JSON);
  const [claimError, setClaimError] = useState<string | null>(null);
  const [probe, setProbe] = useState<{ modality: Modality; probeBase64: string } | null>(null);
  const issueM = useCommerceIssuePickup();
  const claimM = useCommerceClaimPickup();

  return (
    <AppLayout>
      <PageShell
        title="Pickup handoff"
        subtitle="Issue pickup tokens and claim them through the canonical commerce BFF, instead of using the MSIKA Flow sidecar directly."
        icon={<Ticket className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/marketplace" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="mb-6">
          <FacilitiesGeoMapPanel
            title="Pickup location map"
            subtitle="Pharmacy and fulfilment sites with governed coordinates"
            facilityType="PHARMACY"
            size={60}
          />
        </div>

        <div className="grid gap-6 xl:grid-cols-2">
          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Issue pickup token</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              POST <code className="text-[11px]">/internal/v1/commerce/orders/{"{orderId}"}/pickup/issue</code>
            </p>
            <label className="mt-3 block text-xs text-muted-foreground">
              Order id
              <input
                value={orderId}
                onChange={(event) => setOrderId(event.target.value)}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm font-mono"
                placeholder="order-123"
                aria-label="Pickup order id"
              />
            </label>
            <button
              type="button"
              disabled={issueM.isPending || !orderId.trim()}
              className="mt-3 rounded-lg bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
              onClick={() => issueM.mutate(orderId.trim())}
            >
              {issueM.isPending ? (
                <span className="inline-flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" /> Issuing...
                </span>
              ) : (
                "Issue token"
              )}
            </button>
            {issueM.data != null ? (
              <JsonApiDataTable data={issueM.data} columns={GENERIC_RECORD_COLUMNS} isLoading={issueM.isPending} error={issueM.error as Error | null} emptyTitle="Pickup issued" />
            ) : null}
          </section>

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Claim pickup</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              POST <code className="text-[11px]">/internal/v1/commerce/pickup/claim</code> with the upstream claim payload.
            </p>
            <textarea
              value={claimJson}
              onChange={(event) => setClaimJson(event.target.value)}
              rows={9}
              className="mt-3 block w-full rounded-lg border border-border p-3 font-mono text-xs"
              aria-label="Pickup claim JSON"
            />
            <div className="mt-3">
              <BiometricVerifyField label="Verify collector by biometric (optional)" onProbe={setProbe} />
            </div>
            {claimError ? <p className="mt-2 text-xs text-danger">{claimError}</p> : null}
            <button
              type="button"
              disabled={claimM.isPending}
              className="mt-3 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
              onClick={() => {
                let parsed: Record<string, unknown>;
                try {
                  parsed = (JSON.parse(claimJson) ?? {}) as Record<string, unknown>;
                } catch {
                  setClaimError("Invalid JSON.");
                  return;
                }
                setClaimError(null);
                // Thread the collector's live probe. Subject = the claimant identity
                // already entered on this claim; on the service a MATCH confirms, a
                // NO_MATCH blocks (4xx), UNAVAILABLE falls back to the token/OTP proof.
                const subjectRef = parsed.claimantId ?? parsed.claimedBy;
                const body =
                  probe && subjectRef
                    ? {
                        ...parsed,
                        biometricSubjectRef: subjectRef,
                        biometricModality: probe.modality,
                        biometricProbeBase64: probe.probeBase64,
                      }
                    : parsed;
                claimM.mutate(body);
              }}
            >
              {claimM.isPending ? "Posting..." : "Claim pickup"}
            </button>
            {claimM.data != null ? (
              <JsonApiDataTable data={claimM.data} columns={GENERIC_RECORD_COLUMNS} isLoading={claimM.isPending} error={claimM.error as Error | null} emptyTitle="Pickup claimed" />
            ) : null}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
