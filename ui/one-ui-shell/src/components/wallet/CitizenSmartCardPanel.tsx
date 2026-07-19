"use client";

/**
 * Citizen unified SMART-card panel — PHR-carry, bill-share, and browser device enrollment.
 * Uses the citizen BFF under /internal/v1/wallet/** (not the legacy /internal/v1/cards/** routes).
 */

import { useMemo, useState } from "react";
import { HandHeart, Loader2, Shield } from "lucide-react";
import {
  useCreateBillContribution,
  useEnrollDeviceCard,
  useMySmartCards,
} from "@/hooks/queries/useMusheWallet";
import { DigitalCard } from "@/components/wallet/DigitalCard";
import { generateAndStoreKey } from "@/lib/digitalCardKey";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

function unwrapCards(v: unknown): Record<string, unknown>[] {
  const root = asRecord(v);
  const data = asRecord(root.data).data ?? root.data ?? v;
  const node = asRecord(data);
  const list = Array.isArray(data)
    ? data
    : Array.isArray(node.cards)
      ? (node.cards as unknown[])
      : [];
  return list.map(asRecord);
}

export function CitizenSmartCardPanel({ enabled }: { enabled: boolean }) {
  const cardsQ = useMySmartCards(enabled);
  const cards = useMemo(() => unwrapCards(cardsQ.data), [cardsQ.data]);
  const card = cards[0];
  const cardId = card ? readStr(card, "cardId", "id") : "";

  const createBill = useCreateBillContribution();
  const enroll = useEnrollDeviceCard();

  const [billTitle, setBillTitle] = useState("Help with my bill");
  const [billTarget, setBillTarget] = useState("");
  const [shareUrl, setShareUrl] = useState<string | null>(null);
  const [enrollMsg, setEnrollMsg] = useState<string | null>(null);
  const [enrollErr, setEnrollErr] = useState<string | null>(null);

  if (!enabled) return null;

  return (
    <section
      className="rounded-xl border border-teal-200 bg-teal-50/40 p-4 space-y-4"
      data-testid="citizen-smart-card-panel"
    >
      <div>
        <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
          <Shield className="h-4 w-4 text-teal-700" /> Unified SMART card
        </h2>
        <p className="text-xs text-muted-foreground mt-1">
          PHR-carry, bill-share, and device enrollment for your Impilo card functions.
        </p>
      </div>

      {cardsQ.isLoading && (
        <p className="text-sm text-muted-foreground flex items-center gap-2">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading your card…
        </p>
      )}
      {cardsQ.isError && (
        <p className="text-sm text-danger">Could not load your SMART card.</p>
      )}
      {!cardsQ.isLoading && !cardsQ.isError && !cardId && (
        <p className="text-sm text-muted-foreground">
          No wallet card yet. Request a card below, then enroll this browser/device.
        </p>
      )}

      {/* The three card functions (identity / pay / health) as one digital-card surface. */}
      {enabled && <DigitalCard enabled={enabled} />}

      {cardId && (
        <div className="space-y-4">
          <div className="space-y-2">
            <p className="text-sm font-medium text-foreground">Help pay my bill</p>
            <div className="flex flex-wrap gap-2">
              <input
                value={billTitle}
                onChange={(e) => setBillTitle(e.target.value)}
                placeholder="Title"
                aria-label="Bill contribution title"
                className="rounded-lg border border-border bg-card px-3 py-2 text-sm min-w-[12rem] flex-1"
              />
              <input
                value={billTarget}
                onChange={(e) => setBillTarget(e.target.value)}
                placeholder="Target amount"
                aria-label="Bill contribution target"
                className="w-32 rounded-lg border border-border bg-card px-3 py-2 text-sm"
              />
              <button
                type="button"
                disabled={createBill.isPending || !billTitle.trim()}
                onClick={() => {
                  createBill.mutate(
                    {
                      title: billTitle.trim(),
                      targetAmount: billTarget.trim() || undefined,
                      currency: "USD",
                    },
                    {
                      onSuccess: (res) => {
                        const data = asRecord(asRecord(res).data);
                        const token = readStr(data, "shareToken");
                        if (token) {
                          const url = `${window.location.origin}/share/fund/${token}`;
                          setShareUrl(url);
                          void navigator.clipboard?.writeText(url).catch(() => undefined);
                        }
                      },
                    },
                  );
                }}
                className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-60"
              >
                {createBill.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <HandHeart className="h-4 w-4" />
                )}
                Create share link
              </button>
            </div>
            {shareUrl && (
              <p className="text-xs text-emerald-800 break-all" data-testid="bill-share-url">
                Share: {shareUrl}
              </p>
            )}
            {createBill.isError && (
              <p className="text-xs text-danger" role="alert">
                Could not create bill-contribution link.
              </p>
            )}
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium text-foreground">Enroll this browser as a device key</p>
            <p className="text-xs text-muted-foreground">
              Generates a non-extractable P-256 key in this browser, keeps it in this device&apos;s
              secure storage, and enrolls the public key with VITO (VIRTUAL card). The key stays on
              this device so it can sign your card payments.
            </p>
            <button
              type="button"
              disabled={enroll.isPending}
              onClick={() => {
                setEnrollErr(null);
                setEnrollMsg(null);
                void (async () => {
                  try {
                    // Persist the key pair (non-extractable) so it survives for later pay signing,
                    // instead of discarding the private key after enrollment.
                    const publicKey = await generateAndStoreKey();
                    await enroll.mutateAsync({ cardId, publicKey });
                    setEnrollMsg("Device key enrolled and linked to your card.");
                  } catch (e) {
                    setEnrollErr(
                      e instanceof Error ? e.message : "Enrollment failed. Try again shortly.",
                    );
                  }
                })();
              }}
              className="rounded-lg border border-teal-600 px-3 py-2 text-sm font-medium text-teal-800 hover:bg-teal-100 disabled:opacity-60"
            >
              {enroll.isPending ? "Enrolling…" : "Enroll this browser"}
            </button>
            {enrollMsg && (
              <p className="text-xs text-emerald-700" role="status">
                {enrollMsg}
              </p>
            )}
            {enrollErr && (
              <p className="text-xs text-danger" role="alert">
                {enrollErr}
              </p>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
