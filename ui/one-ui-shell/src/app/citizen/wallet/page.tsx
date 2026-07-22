"use client";

/**
 * Mushe Personal Health Wallet — home / overview (MY LIFE · MY HEALTH).
 *
 * The person anchor: identity & trust, profile completion, next-step guidance (Nompilo), and a
 * grid of real, source-labelled cards that link into the existing record / timeline / consent /
 * dependants / payments / comms surfaces. Every card connects to a real BFF capability (no mock
 * cards); cards degrade to truthful "unavailable" / empty states when an owner is down. Content
 * adapts to trust level — sensitive sections are governed by the wallet OPA policy + the sovereign
 * owners, not widened here.
 */

import Link from "next/link";
import {
  ShieldCheck,
  IdCard,
  HeartPulse,
  CalendarClock,
  Share2,
  Users,
  CreditCard,
  Bell,
  Sparkles,
  ArrowUpRight,
  AlertCircle,
  Wallet,
} from "lucide-react";
import { IdentityAssuranceBanner } from "@/components/citizen/IdentityAssuranceBanner";
import { ActingForBanner } from "@/components/citizen/ActingForBanner";
import { useWalletOverview, type WalletNextAction } from "@/hooks/queries/useWallet";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";

type AnyRecord = Record<string, unknown>;

function num(v: unknown): number {
  return typeof v === "number" ? v : 0;
}

function ProfileMeter({ percent, missing }: { percent: number; missing: string[] }) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium text-foreground">Profile completion</span>
        <span className="text-sm font-semibold text-foreground">{percent}%</span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-neutral-100">
        <div
          className="h-full rounded-full bg-emerald-500 transition-all"
          style={{ width: `${Math.min(100, Math.max(0, percent))}%` }}
        />
      </div>
      {missing.length > 0 ? (
        <p className="mt-2 text-xs text-muted-foreground">
          Add: {missing.join(", ")} ·{" "}
          <Link href="/citizen/wallet/profile" className="text-primary-hover underline">
            Update profile
          </Link>
        </p>
      ) : (
        <p className="mt-2 text-xs text-muted-foreground">Your profile is complete.</p>
      )}
    </div>
  );
}

function NextActions({ actions }: { actions: WalletNextAction[] }) {
  if (actions.length === 0) return null;
  return (
    <div className="rounded-lg border border-amber-200 bg-warning-soft p-4">
      <div className="mb-2 flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-amber-700" />
        <span className="text-sm font-semibold text-amber-900">What to do next</span>
        <span className="text-[10px] uppercase tracking-wide text-amber-700">Nompilo guidance</span>
      </div>
      <ul className="space-y-2">
        {actions.map((a) => (
          <li key={a.code}>
            <Link
              href={a.href}
              className="flex items-start justify-between gap-3 rounded-lg border border-amber-200 bg-card/70 p-3 hover:bg-card"
            >
              <span>
                <span className="block text-sm font-medium text-foreground">{a.title}</span>
                <span className="block text-xs text-muted-foreground">{a.why}</span>
              </span>
              <ArrowUpRight className="h-4 w-4 shrink-0 text-amber-700" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

interface CardSpec {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  count?: number;
  countLabel?: string;
  source?: string;
  unavailable?: boolean;
  note?: string;
}

function WalletCard({ spec }: { spec: CardSpec }) {
  const Icon = spec.icon;
  return (
    <Link
      href={spec.href}
      className="flex flex-col gap-1 rounded-lg border border-border bg-card p-4 transition-all hover:border-primary/25 hover:shadow-sm"
    >
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-2 text-sm font-medium text-foreground">
          <Icon className="h-4 w-4 text-primary" /> {spec.label}
        </span>
        {spec.unavailable ? (
          <AlertCircle className="h-4 w-4 text-muted-foreground" aria-label="Temporarily unavailable" />
        ) : null}
      </div>
      {spec.unavailable ? (
        <span className="text-xs text-muted-foreground">Temporarily unavailable — try again shortly.</span>
      ) : typeof spec.count === "number" ? (
        <span className="text-xs text-muted-foreground">
          {spec.count} {spec.countLabel}
        </span>
      ) : spec.note ? (
        <span className="text-xs text-muted-foreground">{spec.note}</span>
      ) : null}
      {spec.source ? (
        <span className="mt-1 text-[10px] uppercase tracking-wide text-muted-foreground/70">{spec.source}</span>
      ) : null}
    </Link>
  );
}

export default function PersonHealthWalletPage() {
  const { data, isLoading, isError, refetch } = useWalletOverview();
  const overview = data?.data;

  const completion = overview?.profileCompletion;
  const records = (overview?.records ?? {}) as AnyRecord;
  const timeline = (overview?.careTimeline ?? {}) as AnyRecord;
  const consent = (overview?.consent ?? {}) as AnyRecord;
  const dependants = (overview?.dependants ?? {}) as AnyRecord;
  const payments = (overview?.payments ?? {}) as AnyRecord;
  const comms = (overview?.commsPreferences ?? {}) as AnyRecord;

  const cards: CardSpec[] = [
    {
      href: "/citizen/wallet/identity",
      label: "Identity & trust",
      icon: IdCard,
      source: "VITO · Identity assurance",
      note: "View your Impilo ID, trust level and how to upgrade",
    },
    {
      href: "/citizen/wallet/records",
      label: "My health records",
      icon: HeartPulse,
      unavailable: records.unavailable === true,
      source: String(records._source ?? "BUTANO · PCT"),
      note: "Conditions, allergies, medications, results",
    },
    {
      href: "/citizen/wallet/timeline",
      label: "Care timeline",
      icon: CalendarClock,
      count: num(timeline.eventCount),
      countLabel: "events",
      unavailable: timeline.unavailable === true,
      source: String(timeline._source ?? "PCT"),
    },
    {
      href: "/citizen/record-sharing",
      label: "Sharing & consent",
      icon: Share2,
      count: num(consent.activeCount),
      countLabel: "active consents",
      unavailable: consent.unavailable === true,
      source: String(consent._source ?? "Tshepo consent"),
    },
    {
      href: "/citizen/wallet/dependants",
      label: "Dependants & proxy",
      icon: Users,
      count: num(dependants.iCanActForCount),
      countLabel: "people you can act for",
      source: String(dependants._source ?? "Mvumo · VITO"),
    },
    {
      href: "/citizen/wallet/payments",
      label: "Payments & bills",
      icon: CreditCard,
      count: num(payments.outstandingCount),
      countLabel: "outstanding",
      unavailable: payments.unavailable === true,
      source: String(payments._source ?? "Costa · MusheX"),
    },
    {
      // The money face of the wallet — the existing MusheX financial wallet (balance, cards,
      // send/deposit). Cross-link only; MusheX owns the financial system-of-record.
      href: "/wallet",
      label: "Money — MusheX Wallet",
      icon: Wallet,
      source: "MusheX",
      note: "Balance, cards, send & deposit",
    },
    {
      href: "/citizen/wallet/comms",
      label: "Communication preferences",
      icon: Bell,
      unavailable: comms.unavailable === true,
      source: String(comms._source ?? "Khuluma delivery"),
      note: "Channels, language and reminders",
    },
  ];

  return (
    <div className="space-y-6">
      <ActingForBanner />

      <div>
        <h1 className="flex items-center gap-2 text-xl font-semibold text-foreground">
          <ShieldCheck className="h-5 w-5 text-primary" /> Mushe Personal Health Wallet
        </h1>
        <p className="mt-0.5 text-sm font-medium text-primary">Your Health, Your Wealth</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Your health life in one place — identity, records, care, consent, dependants and payments.
          Everything here is drawn live from the services that own it.
        </p>
      </div>

      {/* Identity & trust — reuses the canonical assurance journey banner */}
      <IdentityAssuranceBanner />

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading your wallet…</p>
      ) : isError ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm">
          <p className="text-danger">We couldn&apos;t load your wallet just now.</p>
          <button
            type="button"
            onClick={() => void refetch()}
            className="mt-2 text-xs text-primary-hover underline"
          >
            Try again
          </button>
        </div>
      ) : (
        <>
          {completion ? <ProfileMeter percent={completion.percent} missing={completion.missing} /> : null}

          <NextActions actions={overview?.nextActions ?? []} />

          {/* Nompilo deepens the wallet's "what next" cards with config-driven contextual guidance:
              locked-state explainers, route-to-owner CTAs, Khuluma follow-ups, and dismissal. */}
          <NompiloContextualGuidance routePath="/citizen/wallet" title="Nompilo · your guide" />

          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {cards.map((spec) => (
              <li key={spec.href}>
                <WalletCard spec={spec} />
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
