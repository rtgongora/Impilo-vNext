"use client";

/**
 * Digital SMART card — the citizen's card at parity with the physical card, usable in the
 * browser. Surfaces all THREE card functions behind one surface:
 *   • Identity  — the signed Health-ID QR (present-to-verify) + an emergency QR.
 *   • Pay       — a card-signed offline payment (tap/scan) and a biometric variant.
 *   • Health    — the live PHR (allergies / conditions / medications) + the PHR-carry toggle.
 *
 * Pay is real: the browser device key (see @/lib/digitalCardKey) signs the canonical payload
 * mushe verifies. Every function fails clean on upstream trouble and never fabricates card,
 * money, or health state.
 */

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  CreditCard,
  Fingerprint,
  HeartPulse,
  Loader2,
  QrCode,
  ShieldCheck,
} from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { citizenPortalApi } from "@/lib/citizenPortalClient";
import { randomUUID } from "@/lib/uuid";
import {
  useMySmartCards,
  useSetPhrCarry,
  useCardPay,
  useCardPayBiometric,
  type CardPayBody,
} from "@/hooks/queries/useMusheWallet";
import { useCitizenHealthSummary } from "@/hooks/queries/useCitizenHealthSummary";
import {
  buildCanonicalPayload,
  nextOfflineCounter,
  signCardPayload,
} from "@/lib/digitalCardKey";

type Tab = "identity" | "pay" | "health";

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

function readNum(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

function unwrapCards(v: unknown): Record<string, unknown>[] {
  const root = asRecord(v);
  const data = asRecord(root.data).data ?? root.data ?? v;
  const node = asRecord(data);
  const list = Array.isArray(data)
    ? data
    : Array.isArray(node.cards)
      ? (node.cards as unknown[])
      : Array.isArray(node.items)
        ? (node.items as unknown[])
        : [];
  return list.map(asRecord);
}

// ── Function 1 — Identity ──────────────────────────────────────────────────

function IdentityFunction({ active }: { active: boolean }) {
  const qrQ = useQuery({
    queryKey: ["citizen", "health-id-qr"],
    queryFn: () => citizenPortalApi.getHealthIdQr(),
    enabled: active,
  });
  const token = qrQ.data?.qr;

  return (
    <div className="space-y-4" data-testid="digital-card-identity">
      <p className="text-sm text-muted-foreground">
        Present this signed QR at any participating facility to identify yourself. It carries a
        signed pointer only — no personal details — that a verifier resolves through the card
        resolve seam.
      </p>

      {qrQ.isLoading && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Preparing your identity QR…
        </p>
      )}
      {qrQ.isError && (
        <p className="text-sm text-danger" role="alert">
          Identity service is temporarily unavailable. Please try again shortly.
        </p>
      )}

      {token && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="flex flex-col items-center gap-2 rounded-xl border border-border bg-card p-4">
            <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              <QrCode className="h-3.5 w-3.5" /> Identity QR
            </span>
            <div className="rounded-lg bg-white p-3" data-testid="identity-qr">
              <QRCodeSVG value={token} size={168} level="M" includeMargin={false} />
            </div>
            <p className="text-[11px] text-muted-foreground">Signed pointer · valid ~1 hour</p>
          </div>

          <div className="flex flex-col items-center gap-2 rounded-xl border border-rose-200 bg-rose-50/50 p-4">
            <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-rose-700">
              <AlertTriangle className="h-3.5 w-3.5" /> Emergency QR
            </span>
            <div className="rounded-lg bg-white p-3" data-testid="emergency-qr">
              <QRCodeSVG value={token} size={168} level="M" includeMargin={false} />
            </div>
            <p className="text-[11px] text-rose-700/80 text-center">
              First responders scan to resolve your Health ID via the verify seam.
            </p>
          </div>
        </div>
      )}

      {token && (
        <details className="rounded-lg border border-border bg-background p-3">
          <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
            Show the signed token (same value encoded in the QR)
          </summary>
          <div
            className="mt-2 max-h-40 overflow-y-auto break-all rounded-md border border-border bg-card p-2 font-mono text-[11px] text-foreground"
            data-testid="identity-token"
          >
            {token}
          </div>
        </details>
      )}
    </div>
  );
}

// ── Function 3 — Pay ───────────────────────────────────────────────────────

interface PayFunctionProps {
  vitoCardNumber: string;
  serverLastCounter: number;
}

function PayFunction({ vitoCardNumber, serverLastCounter }: PayFunctionProps) {
  const [amount, setAmount] = useState("1.00");
  const [currency] = useState("USD");
  const [mushexIntentId, setMushexIntentId] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null);

  const pay = useCardPay();
  const payBio = useCardPayBiometric();

  const enrolled = vitoCardNumber.length > 0;

  async function submit(biometric: boolean) {
    setResult(null);
    const numeric = Number(amount);
    if (!Number.isFinite(numeric) || numeric <= 0) {
      setResult({ ok: false, message: "Enter an amount greater than zero." });
      return;
    }
    setBusy(true);
    try {
      const counter = await nextOfflineCounter(serverLastCounter);
      const nonce = randomUUID();
      const authMethod = biometric ? "BIOMETRIC" : "PIN";
      const payload = buildCanonicalPayload({
        amount: numeric.toFixed(2),
        counter,
        nonce,
        currency,
        authMethod,
        // B5a: when settling a specific bill/order, the intent id rides inside the
        // signed payload so mushe drives it to PAID and Costa/Msika recognize it.
        mushexIntentId: mushexIntentId.trim() || undefined,
      });
      const signature = await signCardPayload(payload);
      const body: CardPayBody = { vitoCardNumber, payload, signature };
      await (biometric ? payBio : pay).mutateAsync(body);
      setResult({
        ok: true,
        message: mushexIntentId.trim()
          ? `Paid ${currency} ${numeric.toFixed(2)} — bill settled from your card purse.`
          : `Paid ${currency} ${numeric.toFixed(2)} from your card purse.`,
      });
    } catch (e) {
      // Honest failure — a rejected signature/replay/stale counter is a real 422 from mushe,
      // an outage is a 503; never a fabricated success.
      const msg =
        e instanceof Error && /No enrolled device key/.test(e.message)
          ? "This browser is not enrolled yet. Enroll this device to pay."
          : "Payment was declined or the service is unavailable. Nothing was charged.";
      setResult({ ok: false, message: msg });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-4" data-testid="digital-card-pay">
      <p className="text-sm text-muted-foreground">
        Pay from your card&apos;s offline purse. This browser signs the transaction with your
        device key; the ledger verifies the signature and debits your wallet.
      </p>

      {!enrolled && (
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          Enroll this browser as a device key first (below) to enable pay.
        </p>
      )}

      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
          Amount ({currency})
          <input
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            aria-label="Payment amount"
            className="w-36 rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs font-medium text-muted-foreground">
          Settle a bill (optional)
          <input
            value={mushexIntentId}
            onChange={(e) => setMushexIntentId(e.target.value)}
            aria-label="Payment intent to settle"
            placeholder="Bill / payment reference"
            data-testid="pay-intent-input"
            className="w-56 rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground"
          />
        </label>
        <button
          type="button"
          disabled={!enrolled || busy}
          onClick={() => void submit(false)}
          className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-60"
          data-testid="pay-button"
        >
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CreditCard className="h-4 w-4" />}
          Pay
        </button>
        <button
          type="button"
          disabled={!enrolled || busy}
          onClick={() => void submit(true)}
          className="inline-flex items-center gap-1.5 rounded-lg border border-teal-600 px-3 py-2 text-sm font-medium text-teal-800 hover:bg-teal-100 disabled:opacity-60"
          data-testid="pay-biometric-button"
        >
          <Fingerprint className="h-4 w-4" /> Biometric pay
        </button>
      </div>

      {result && (
        <p
          className={`text-sm ${result.ok ? "text-emerald-700" : "text-danger"}`}
          role={result.ok ? "status" : "alert"}
          data-testid="pay-result"
        >
          {result.message}
        </p>
      )}
    </div>
  );
}

// ── Function 2 — Health (PHR) ──────────────────────────────────────────────

function labelOf(item: Record<string, unknown>): string {
  return (
    readStr(
      item,
      "display",
      "name",
      "text",
      "title",
      "description",
      "substance",
      "code",
      "medication",
    ) || "—"
  );
}

function PhrSection({
  icon,
  title,
  items,
  emptyText,
}: {
  icon: React.ReactNode;
  title: string;
  items: Record<string, unknown>[];
  emptyText: string;
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <h3 className="mb-2 flex items-center gap-1.5 text-sm font-semibold text-foreground">
        {icon} {title}
      </h3>
      {items.length === 0 ? (
        <p className="text-xs text-muted-foreground">{emptyText}</p>
      ) : (
        <ul className="space-y-1 text-sm text-foreground">
          {items.map((item, i) => (
            <li key={i} className="rounded-md bg-background px-2 py-1">
              {labelOf(item)}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function HealthFunction({
  active,
  cardId,
  phrEnabled,
}: {
  active: boolean;
  cardId: string;
  phrEnabled: boolean;
}) {
  const summaryQ = useCitizenHealthSummary();
  const setPhr = useSetPhrCarry();

  return (
    <div className="space-y-4" data-testid="digital-card-health">
      <label className="flex items-center gap-3 rounded-lg border border-border bg-background px-3 py-2 text-sm">
        <input
          type="checkbox"
          checked={phrEnabled}
          disabled={!cardId || setPhr.isPending}
          onChange={(e) => setPhr.mutate({ cardId, enabled: e.target.checked })}
          aria-label="Carry my health record on this card"
        />
        <span>
          Carry my health record on this card (PHR)
          {setPhr.isPending && <Loader2 className="ml-1 inline h-3 w-3 animate-spin" />}
        </span>
      </label>
      {setPhr.isError && (
        <p className="text-xs text-danger" role="alert">
          Could not update PHR-carry.
        </p>
      )}

      {active && summaryQ.isLoading && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading your health summary…
        </p>
      )}
      {active && summaryQ.isError && (
        <p className="text-sm text-danger" role="alert">
          Your health summary is temporarily unavailable. Please try again shortly.
        </p>
      )}

      {active && !summaryQ.isLoading && !summaryQ.isError && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <PhrSection
            icon={<AlertTriangle className="h-4 w-4 text-rose-600" />}
            title="Allergies"
            items={summaryQ.allergies as Record<string, unknown>[]}
            emptyText="No allergies on record."
          />
          <PhrSection
            icon={<Activity className="h-4 w-4 text-amber-600" />}
            title="Conditions"
            items={summaryQ.conditions as Record<string, unknown>[]}
            emptyText="No conditions on record."
          />
          <PhrSection
            icon={<HeartPulse className="h-4 w-4 text-teal-600" />}
            title="Medications"
            items={summaryQ.medications as Record<string, unknown>[]}
            emptyText="No medications on record."
          />
        </div>
      )}
    </div>
  );
}

// ── Assembled surface ──────────────────────────────────────────────────────

const TABS: { id: Tab; label: string; icon: React.ReactNode }[] = [
  { id: "identity", label: "Identity", icon: <ShieldCheck className="h-4 w-4" /> },
  { id: "pay", label: "Pay", icon: <CreditCard className="h-4 w-4" /> },
  { id: "health", label: "Health", icon: <HeartPulse className="h-4 w-4" /> },
];

export function DigitalCard({ enabled = true }: { enabled?: boolean }) {
  const [tab, setTab] = useState<Tab>("identity");
  const cardsQ = useMySmartCards(enabled);
  const cards = useMemo(() => unwrapCards(cardsQ.data), [cardsQ.data]);
  const card = cards[0] ?? {};

  const cardId = readStr(card, "cardId", "id");
  const vitoCardNumber = readStr(card, "vitoCardNumber");
  const phrEnabled = card?.phrEnabled === true;
  const masked = readStr(card, "maskedCardNumber");
  const serverLastCounter = readNum(card, "lastOfflineCounter");

  // Default to Pay only once we know a card exists (keeps Identity the friendly default).
  useEffect(() => {
    if (!cardId && tab !== "identity") setTab("identity");
  }, [cardId, tab]);

  if (!enabled) return null;

  return (
    <section
      className="rounded-2xl border border-teal-200 bg-gradient-to-br from-teal-50 to-white p-5 shadow-sm"
      data-testid="digital-card"
    >
      <header className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 text-base font-semibold text-foreground">
            <CreditCard className="h-4 w-4 text-teal-700" /> My digital SMART card
          </h2>
          <p className="mt-0.5 text-xs text-muted-foreground">
            One card, three functions — identity, pay, and your health record.
          </p>
        </div>
        <div className="text-right text-xs">
          {vitoCardNumber ? (
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 font-medium text-emerald-800">
              Device enrolled
            </span>
          ) : (
            <span className="rounded-full bg-amber-100 px-2 py-0.5 font-medium text-amber-800">
              Not enrolled
            </span>
          )}
          {masked && <div className="mt-1 font-mono text-muted-foreground">{masked}</div>}
        </div>
      </header>

      {cardsQ.isLoading && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading your card…
        </p>
      )}
      {cardsQ.isError && (
        <p className="text-sm text-danger" role="alert">
          Your card is temporarily unavailable. Please try again shortly.
        </p>
      )}

      {!cardsQ.isLoading && !cardsQ.isError && (
        <>
          <nav className="mb-4 flex gap-1 rounded-lg border border-border bg-card p-1" role="tablist">
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                role="tab"
                aria-selected={tab === t.id}
                onClick={() => setTab(t.id)}
                data-testid={`digital-card-tab-${t.id}`}
                className={`flex flex-1 items-center justify-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
                  tab === t.id
                    ? "bg-teal-600 text-white"
                    : "text-muted-foreground hover:bg-teal-50"
                }`}
              >
                {t.icon}
                {t.label}
              </button>
            ))}
          </nav>

          {tab === "identity" && <IdentityFunction active={tab === "identity"} />}
          {tab === "pay" && (
            <PayFunction vitoCardNumber={vitoCardNumber} serverLastCounter={serverLastCounter} />
          )}
          {tab === "health" && (
            <HealthFunction active={tab === "health"} cardId={cardId} phrEnabled={phrEnabled} />
          )}
        </>
      )}
    </section>
  );
}
