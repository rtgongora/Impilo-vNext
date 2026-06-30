"use client";

/**
 * Wallet · Communication preferences. Read-only summary of the person's channel / language /
 * reminder preferences (sourced from notification-service via the wallet overview). All messaging
 * is delivered through Khuluma/notification-service — the wallet does not send notifications and
 * does not own preferences. Editing routes to the canonical preference editor so there is a single
 * write path.
 */

import Link from "next/link";
import { useWalletOverview } from "@/hooks/queries/useWallet";

type AnyRecord = Record<string, unknown>;

export default function WalletCommsPage() {
  const { data, isLoading, isError } = useWalletOverview();
  const card = (data?.data?.commsPreferences ?? {}) as AnyRecord;
  const prefs = (card.preferences ?? {}) as AnyRecord;
  const unavailable = card.unavailable === true;

  const channels = Array.isArray(prefs.channels) ? (prefs.channels as unknown[]) : [];

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-2">
      <div>
        <h1 className="text-xl font-semibold text-foreground">Communication preferences</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          How and when you hear from Impilo. Messages are delivered through Khuluma.
        </p>
        <p className="mt-1 text-[10px] uppercase tracking-wide text-muted-foreground/70">
          Source: {String(card._source ?? "notification-service")}
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading your preferences…</p>
      ) : isError || unavailable ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Your preferences are temporarily unavailable.
        </div>
      ) : (
        <section className="space-y-2 rounded-lg border border-border bg-card p-4 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Preferred language</span>
            <span className="text-foreground">{String(prefs.language ?? prefs.preferredLanguage ?? "—")}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Reminders</span>
            <span className="text-foreground">
              {prefs.remindersEnabled === false ? "Off" : "On"}
            </span>
          </div>
          <div>
            <span className="text-muted-foreground">Channels</span>
            <div className="mt-1 flex flex-wrap gap-1">
              {channels.length === 0 ? (
                <span className="text-foreground">Default</span>
              ) : (
                channels.map((c, idx) => (
                  <span key={idx} className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs text-foreground">
                    {String((c as AnyRecord)?.type ?? c)}
                  </span>
                ))
              )}
            </div>
          </div>
        </section>
      )}

      <Link
        href="/settings/notifications"
        className="inline-block rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white hover:bg-blue-800"
      >
        Edit communication preferences
      </Link>
    </div>
  );
}
