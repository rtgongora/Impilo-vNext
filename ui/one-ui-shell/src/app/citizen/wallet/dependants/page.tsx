"use client";

/**
 * Wallet · Dependants & proxy. Shows who the person can act for (Mvumo delegations where they are
 * the delegate) and who can act for them (delegations where they are the subject). Entering a
 * dependant context sets the shared "acting for" delegation store (X-Subject-ID), so every
 * subsequent action is authorised + audited as delegated, with the persistent ActingForBanner.
 * The wallet does NOT own delegations — Mvumo does.
 */

import { useWalletOverview } from "@/hooks/queries/useWallet";
import { useDelegationStore } from "@/hooks/useDelegationStore";

type AnyRecord = Record<string, unknown>;

function asList(v: unknown): AnyRecord[] {
  if (Array.isArray(v)) return v.filter((x): x is AnyRecord => typeof x === "object" && x !== null);
  if (v && typeof v === "object") {
    const r = v as AnyRecord;
    for (const k of ["items", "content", "data"]) {
      if (Array.isArray(r[k])) return r[k] as AnyRecord[];
    }
  }
  return [];
}

export default function WalletDependantsPage() {
  const { data, isLoading } = useWalletOverview();
  const card = (data?.data?.dependants ?? {}) as AnyRecord;
  const iCanActFor = asList(card.iCanActFor);
  const canActForMe = asList(card.canActForMe);

  const enterContext = useDelegationStore((s) => s.enterContext);
  const actingFor = useDelegationStore((s) => s.actingFor);

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-2">
      <div>
        <h1 className="text-xl font-semibold text-foreground">Dependants & proxy</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          People you are authorised to act for, and people who can act for you. Acting for someone is
          recorded on every action.
        </p>
        <p className="mt-1 text-[10px] uppercase tracking-wide text-muted-foreground/70">
          Source: {String(card._source ?? "Mvumo · VITO")}
        </p>
      </div>

      <section>
        <h2 className="mb-2 text-sm font-medium text-foreground">People I can act for</h2>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : iCanActFor.length === 0 ? (
          <p className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
            You aren&apos;t currently authorised to act for anyone.
          </p>
        ) : (
          <ul className="space-y-2">
            {iCanActFor.map((d, idx) => {
              const subjectRef = String(d.subjectRef ?? d.subject ?? "");
              const label = String(d.subjectLabel ?? d.subjectName ?? subjectRef);
              const rel = String(d.relationshipType ?? d.relationship ?? "delegate");
              const isActive = actingFor?.subjectRef === subjectRef;
              return (
                <li
                  key={idx}
                  className="flex items-center justify-between rounded-lg border border-border bg-card p-3"
                >
                  <span>
                    <span className="block text-sm font-medium text-foreground">{label}</span>
                    <span className="block text-xs text-muted-foreground">{rel}</span>
                  </span>
                  <button
                    type="button"
                    disabled={isActive || !subjectRef}
                    onClick={() => enterContext({ subjectRef, subjectLabel: label, relationshipType: rel })}
                    className="rounded bg-amber-600 px-3 py-1 text-xs font-medium text-white hover:bg-amber-700 disabled:opacity-50"
                  >
                    {isActive ? "Active" : "Act for"}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section>
        <h2 className="mb-2 text-sm font-medium text-foreground">People who can act for me</h2>
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : canActForMe.length === 0 ? (
          <p className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
            No one currently has delegated access to your wallet.
          </p>
        ) : (
          <ul className="space-y-2">
            {canActForMe.map((d, idx) => (
              <li key={idx} className="rounded-lg border border-border bg-card p-3 text-sm">
                <span className="font-medium text-foreground">
                  {String(d.delegateLabel ?? d.delegateName ?? d.delegate ?? "Delegate")}
                </span>
                <span className="ml-2 text-xs text-muted-foreground">
                  {String(d.relationshipType ?? d.relationship ?? "")}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
