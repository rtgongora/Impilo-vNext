"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertCircle, ArrowLeft, CreditCard, Loader2, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  extractRecord,
  useMushexPlatformCardProfileById,
} from "@/hooks/queries/useMushexPlatformAdmin";

function asString(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return "—";
  }
}

export default function CardProfileDetailPage() {
  const params = useParams();
  const cardId = typeof params.cardId === "string" ? params.cardId : "";
  const cardQ = useMushexPlatformCardProfileById(cardId);
  const card = extractRecord(cardQ.data);

  return (
    <AppLayout>
      <PageShell
        title="Card profile"
        subtitle={cardId ? `Read-only detail for card ${cardId}` : "Read-only detail for a card profile"}
      >
        <div className="mb-4">
          <Link
            href="/finance/mushex-platform"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to MusheX platform
          </Link>
        </div>

        <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
              <CreditCard className="h-5 w-5" />
            </div>
            <div className="flex-1">
              <h2 className="text-sm font-semibold text-slate-900">Card profile detail</h2>
              <p className="mt-1 text-xs text-slate-500">
                Read-only card profile detail from MusheX platform admin.
              </p>

              {cardQ.isLoading && (
                <p className="mt-3 flex items-center gap-2 text-xs text-slate-500">
                  <Loader2 className="h-3.5 w-3.5 animate-spin" /> Loading card profile…
                </p>
              )}
              {cardQ.isError && (
                <p className="mt-3 flex items-center gap-2 text-xs text-red-700">
                  <AlertCircle className="h-3.5 w-3.5" /> Could not load card profile from MusheX.
                </p>
              )}
              {!cardQ.isLoading && !cardQ.isError && !card && (
                <p className="mt-3 text-xs text-slate-500">
                  No card profile returned for this ID.
                </p>
              )}
              {!cardQ.isLoading && !cardQ.isError && card && (
                <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-slate-500">Card profile ID</dt>
                    <dd className="font-mono text-[11px] text-slate-900">
                      {asString(card.cardProfileId ?? card.id)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Wallet ID</dt>
                    <dd className="font-mono text-[11px] text-slate-700">{asString(card.walletId)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Owner ref</dt>
                    <dd className="text-slate-700">{asString(card.ownerRef)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Card type</dt>
                    <dd className="text-slate-700">{asString(card.cardType)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Status</dt>
                    <dd className="text-slate-700">{asString(card.cardStatus ?? card.status)}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-500">Issued at</dt>
                    <dd className="text-slate-700">{asString(card.issuedAt ?? card.createdAt)}</dd>
                  </div>
                </dl>
              )}

              <p className="mt-3 text-[11px] text-slate-400">
                BFF route:{" "}
                <code className="text-[10px]">
                  /internal/v1/finance/mushex-platform/card-profiles/{cardId || "{cardId}"}
                </code>
              </p>
              <p className="mt-2 inline-flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] text-slate-600">
                <Shield className="h-3.5 w-3.5" />
                Read-only view — no issue / suspend / retire actions are surfaced on this page.
              </p>
            </div>
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}

