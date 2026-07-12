"use client";

import { useState } from "react";
import Link from "next/link";
import { BadgeCheck, BarChart3, Gavel, ListChecks, PlusCircle, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import {
  useSupplierDemand,
  useVerifyWholesalerLicence,
  type SupplierDemandCategory,
} from "@/hooks/queries/useRequirementSourcing";

function errMsg(e: unknown, fallback: string): string {
  return (
    (e as { body?: { message?: string; error?: { message?: string } | string } })?.body?.message ??
    (e as { body?: { error?: { message?: string } } })?.body?.error?.message ??
    (e as Error)?.message ??
    fallback
  );
}

function DemandCategoryRow({ category }: { category: SupplierDemandCategory }) {
  const [expanded, setExpanded] = useState(false);
  const provinces = Object.entries(category.byProvince ?? {});
  return (
    <li className="rounded-md border border-border/60 bg-background p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <span className="font-medium text-foreground">{category.categoryLabel}</span>
          {category.restricted && (
            <span className="ml-2 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-medium text-amber-700">
              licensed suppliers only
            </span>
          )}
        </div>
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span>
            <span className="font-semibold text-foreground">{category.openFindings}</span> open findings
          </span>
          <span>
            <span className="font-semibold text-foreground">{category.openComplianceActions}</span> compliance
            actions
          </span>
          {provinces.length > 0 && (
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              className="text-primary hover:underline"
            >
              {expanded ? "Hide provinces" : "By province"}
            </button>
          )}
        </div>
      </div>
      {expanded && provinces.length > 0 && (
        <div className="mt-2 overflow-x-auto">
          <div className="flex flex-wrap gap-2">
            {provinces.map(([province, count]) => (
              <span
                key={province}
                className="rounded-full border border-border bg-muted px-2 py-0.5 text-[11px] text-muted-foreground"
              >
                {province}: <span className="font-medium text-foreground">{count}</span>
              </span>
            ))}
          </div>
        </div>
      )}
    </li>
  );
}

function MarketDemandSection() {
  const demand = useSupplierDemand();
  const data = demand.data?.data;

  return (
    <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground">
          <BarChart3 className="h-5 w-5 text-primary" /> Market demand
        </h2>
        {data?.generatedAt && (
          <span className="text-xs text-muted-foreground">as of {data.generatedAt.slice(0, 10)}</span>
        )}
      </div>
      <p className="mt-1 text-sm text-muted-foreground">
        Aggregated demand from open regulatory processes. Individual facilities are never disclosed.
      </p>

      {demand.isLoading && <p className="mt-4 text-sm text-muted-foreground">Loading demand signals…</p>}
      {demand.isError && <p className="mt-4 text-sm text-red-600">Could not load market demand right now.</p>}

      {data && (
        <div className="mt-4 space-y-4">
          {data.categories.length === 0 ? (
            <p className="text-sm text-muted-foreground">No demand signals above the reporting floor yet.</p>
          ) : (
            <ul className="space-y-2">
              {data.categories.map((c) => (
                <DemandCategoryRow key={c.categoryCode} category={c} />
              ))}
            </ul>
          )}

          {data.openApplicationsByFacilityType.length > 0 && (
            <div>
              <h3 className="text-sm font-medium text-foreground">Open establishment applications</h3>
              <p className="text-xs text-muted-foreground">
                New facilities working towards registration — future buyers, by facility type.
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                {data.openApplicationsByFacilityType.map((a) => (
                  <span
                    key={a.facilityType}
                    className="rounded-full border border-border bg-muted px-2 py-0.5 text-[11px] text-muted-foreground"
                  >
                    {a.facilityType.replace(/_/g, " ")}:{" "}
                    <span className="font-medium text-foreground">{a.count}</span>
                  </span>
                ))}
              </div>
            </div>
          )}

          {data.smallCellFloor > 0 && (
            <p className="text-[11px] text-muted-foreground">
              Counts below {data.smallCellFloor} are suppressed to protect facility privacy.
            </p>
          )}
        </div>
      )}
    </section>
  );
}

function VerifyWholesalerLicenceCard() {
  const verify = useVerifyWholesalerLicence();
  const [storefrontId, setStorefrontId] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [message, setMessage] = useState<{ kind: "success" | "error"; text: string } | null>(null);

  return (
    <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
      <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground">
        <BadgeCheck className="h-5 w-5 text-primary" /> Verify wholesaler licence
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Wholesalers selling into restricted categories verify their licence against their storefront. Enter your
        storefront ID and the verification code from your licence document.
      </p>
      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        <input
          value={storefrontId}
          onChange={(e) => setStorefrontId(e.target.value)}
          placeholder="Storefront ID"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm"
        />
        <input
          value={verificationCode}
          onChange={(e) => setVerificationCode(e.target.value)}
          placeholder="Verification code"
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm"
        />
      </div>
      <button
        type="button"
        disabled={verify.isPending || !storefrontId.trim() || !verificationCode.trim()}
        onClick={() => {
          setMessage(null);
          verify.mutate(
            { storefrontId: storefrontId.trim(), verificationCode: verificationCode.trim() },
            {
              onSuccess: () =>
                setMessage({ kind: "success", text: "Licence verified — your storefront has been updated." }),
              onError: (e) =>
                setMessage({ kind: "error", text: errMsg(e, "Licence could not be verified.") }),
            },
          );
        }}
        className="mt-3 rounded-md bg-primary px-4 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
      >
        {verify.isPending ? "Verifying…" : "Verify licence"}
      </button>
      {message && (
        <p className={`mt-2 text-sm ${message.kind === "success" ? "text-emerald-700" : "text-red-600"}`}>
          {message.text}
        </p>
      )}
    </section>
  );
}

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

          <MarketDemandSection />

          <VerifyWholesalerLicenceCard />
        </div>
      </PageShell>
    </AppLayout>
  );
}
