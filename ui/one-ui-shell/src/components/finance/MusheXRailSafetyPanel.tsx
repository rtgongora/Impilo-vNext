"use client";

import Link from "next/link";
import { AlertTriangle, FlaskConical, Shield } from "lucide-react";
import { usePayerOpsAdapters } from "@/hooks/queries/useFinancePayerOps";

/**
 * Operator-facing MusheX rail safety gate — live money remains blocked by design;
 * sandbox attempts are opt-in and require explicit adapter readiness.
 */
export function MusheXRailSafetyPanel() {
  const adaptersQ = usePayerOpsAdapters(true);

  const rows = Array.isArray(adaptersQ.data)
    ? adaptersQ.data
    : adaptersQ.data && typeof adaptersQ.data === "object"
      ? ((adaptersQ.data as Record<string, unknown>).data as unknown[]) ??
        ((adaptersQ.data as Record<string, unknown>).items as unknown[]) ??
        []
      : [];

  const sandboxReady = rows.some((row) => {
    if (!row || typeof row !== "object") return false;
    const r = row as Record<string, unknown>;
    const status = String(r.status ?? r.readiness ?? "").toUpperCase();
    const rail = String(r.rail ?? r.adapterType ?? r.type ?? "").toUpperCase();
    return status.includes("SANDBOX") || rail.includes("SANDBOX");
  });

  return (
    <section className="rounded-xl border border-warning/35 bg-warning-soft/70 p-5 shadow-sm">
      <div className="flex flex-wrap items-start gap-3">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-100 text-warning-foreground">
          <Shield className="h-5 w-5" />
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="text-sm font-semibold text-foreground">MusheX rail safety (operator)</h2>
          <p className="mt-1 text-sm text-foreground">
            Live money movement stays <strong>blocked</strong> until rail credentials and step-up auth are proven.
            Preview may enable the <strong>sandbox adapter</strong> for simulation-only payment attempts.
          </p>
          <ul className="mt-3 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
            <li>
              Initiate attempt only after loading a payment intent and confirming sandbox readiness below.
            </li>
            <li>
              Production rails (mobile money, cards, bank) return{" "}
              <code className="text-[11px]">BLOCKED_NOT_LIVE_CAPABLE</code> until operator unlock procedures complete.
            </li>
            <li>
              Full platform adapter matrix:{" "}
              <Link href="/finance/mushex-platform" className="text-primary-hover hover:underline">
                MusheX platform admin
              </Link>
              .
            </li>
          </ul>
        </div>
        <span
          className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs font-medium ${
            sandboxReady
              ? "border-sky-200 bg-sky-50 text-sky-800"
              : "border-amber-300 bg-card text-warning-foreground"
          }`}
        >
          {sandboxReady ? (
            <>
              <FlaskConical className="h-3.5 w-3.5" /> Sandbox ready
            </>
          ) : (
            <>
              <AlertTriangle className="h-3.5 w-3.5" /> Sandbox not ready
            </>
          )}
        </span>
      </div>
      {adaptersQ.isError ? (
        <p className="mt-3 text-xs text-danger">
          Could not load adapter readiness — deploy MusheX (wave 8 finance pilot) or check BFF upstream.
        </p>
      ) : null}
      <p className="mt-3 text-xs text-muted-foreground">
        Preview env: set <code className="text-[11px]">MUSHEX_SANDBOX_ENABLED=true</code> on{" "}
        <code className="text-[11px]">mushex-service</code> via{" "}
        <code className="text-[11px]">FULL_BOOT_MAX_WAVE=8</code> promote script.
      </p>
    </section>
  );
}
