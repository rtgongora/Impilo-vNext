"use client";

/**
 * Trauma blood-readiness gate (WU4) — read-through of MADI truth for a blood order:
 * READY only when the order is RESERVED + crossmatch COMPATIBLE, else BLOCKED with the
 * honest blocker reason (never false-ready). Consumes GET /internal/v1/ed/blood-readiness
 * ?orderId= (pct read-model). A clinician checks this before transfusing. No mocks.
 */

import { useState } from "react";
import { CheckCircle2, Droplet, Loader2, ShieldAlert } from "lucide-react";
import { useBloodReadiness } from "@/hooks/queries/useDaidzai";

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

const STATUS_STYLE: Record<string, string> = {
  READY: "border-green-300 bg-green-50 text-green-900",
  BLOCKED: "border-amber-300 bg-amber-50 text-amber-900",
  UNAVAILABLE: "border-neutral-300 bg-neutral-50 text-neutral-700",
  NO_BLOOD_REQUEST: "border-neutral-300 bg-neutral-50 text-neutral-700",
};

export function BloodReadinessPanel({ orderId: fixedOrderId }: { orderId?: string }) {
  const [entered, setEntered] = useState(fixedOrderId ?? "");
  const orderId = (fixedOrderId ?? entered).trim();
  const readiness = useBloodReadiness(orderId || undefined);
  const data = readiness.data ?? null;
  const status = str(data?.status).toUpperCase();

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm" data-testid="blood-readiness-panel">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Droplet className="h-4 w-4 text-red-600" /> Blood readiness
      </h3>

      {!fixedOrderId && (
        <div className="mt-2 flex gap-2">
          <input
            value={entered}
            onChange={(e) => setEntered(e.target.value)}
            placeholder="MADI blood order reference"
            className="flex-1 rounded-lg border border-border px-3 py-2 font-mono text-sm"
          />
        </div>
      )}

      {!orderId ? (
        <p className="mt-3 text-xs text-muted-foreground">Enter a blood order reference to check reservation + crossmatch status.</p>
      ) : readiness.isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Checking MADI…</div>
      ) : readiness.isError ? (
        <p className="mt-3 text-sm text-red-700">Could not read blood readiness for this order.</p>
      ) : data ? (
        <div className={`mt-3 rounded-lg border px-3 py-2.5 text-sm ${STATUS_STYLE[status] ?? "border-border bg-background"}`}>
          <div className="flex items-center gap-2 font-semibold">
            {status === "READY" ? <CheckCircle2 className="h-4 w-4" /> : <ShieldAlert className="h-4 w-4" />}
            {status || "UNKNOWN"}
          </div>
          <dl className="mt-1.5 grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs">
            <div><dt className="inline text-muted-foreground">Reservation: </dt><dd className="inline">{str(data.reservation_status) || "—"}</dd></div>
            <div><dt className="inline text-muted-foreground">Crossmatch: </dt><dd className="inline">{str(data.crossmatch_status) || "—"}</dd></div>
            <div><dt className="inline text-muted-foreground">MADI status: </dt><dd className="inline">{str(data.madi_status) || "—"}</dd></div>
          </dl>
          {data.blocker ? <p className="mt-1.5 text-xs">{str(data.blocker)}</p> : null}
        </div>
      ) : null}
    </section>
  );
}
