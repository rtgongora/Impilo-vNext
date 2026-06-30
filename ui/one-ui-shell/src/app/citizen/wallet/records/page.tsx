"use client";

/**
 * Wallet · Health record summary. Read-only view of conditions / allergies / medications etc.
 * sourced from BUTANO (IPS/SHR) + PCT via the wallet overview. The wallet never stores clinical
 * truth — it displays what the owning services return, with a source label and truthful empty
 * states. Detailed record access is governed upstream by trust level + consent.
 */

import { useWalletOverview } from "@/hooks/queries/useWallet";

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

function Section({ title, items, render }: { title: string; items: AnyRecord[]; render: (i: AnyRecord) => string }) {
  return (
    <section className="rounded-lg border border-border bg-card p-4">
      <h2 className="mb-2 text-sm font-medium text-foreground">
        {title} <span className="text-xs text-muted-foreground">({items.length})</span>
      </h2>
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nothing recorded.</p>
      ) : (
        <ul className="space-y-1 text-sm text-foreground">
          {items.slice(0, 25).map((i, idx) => (
            <li key={idx} className="border-b border-border/50 pb-1 last:border-0">
              {render(i)}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export default function WalletRecordsPage() {
  const { data, isLoading, isError } = useWalletOverview();
  const records = (data?.data?.records ?? {}) as AnyRecord;
  const summary = (records.summary ?? {}) as AnyRecord;
  const unavailable = records.unavailable === true;

  const conditions = asList(summary.conditions);
  const allergies = asList(summary.allergies);
  const medications = asList(summary.medications);
  const immunizations = asList(summary.immunizations);

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-2">
      <div>
        <h1 className="text-xl font-semibold text-foreground">My health records</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          A summary of your clinical record. This is a live view from the services that hold it —
          nothing is copied or stored here.
        </p>
        <p className="mt-1 text-[10px] uppercase tracking-wide text-muted-foreground/70">
          Source: {String(records._source ?? "BUTANO · PCT")}
        </p>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading your records…</p>
      ) : isError || unavailable ? (
        <div className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
          Your record summary is temporarily unavailable. If you have just registered, it may not be
          linked to a clinical chart yet.
        </div>
      ) : (
        <>
          <Section
            title="Conditions"
            items={conditions}
            render={(i) => String(i.display ?? i.name ?? i.code ?? "Condition")}
          />
          <Section
            title="Allergies"
            items={allergies}
            render={(i) => String(i.display ?? i.substance ?? i.name ?? "Allergy")}
          />
          <Section
            title="Medications"
            items={medications}
            render={(i) => String(i.display ?? i.name ?? i.medication ?? "Medication")}
          />
          <Section
            title="Immunizations"
            items={immunizations}
            render={(i) => String(i.vaccineName ?? i.display ?? i.name ?? "Immunization")}
          />
        </>
      )}
    </div>
  );
}
