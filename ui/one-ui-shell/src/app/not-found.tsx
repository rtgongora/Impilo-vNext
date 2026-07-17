"use client";

/**
 * Global dead-end (404) page. An honest "we couldn't find that page" surface that never
 * strands a citizen — it offers the calm way back to the things that matter (home, find care,
 * service status, report a problem).
 *
 * Observability: on mount it records a PII-free UI_DEAD_END observation (pathname + code only)
 * so operators can see which links/routes are dead-ending citizens. No bodies, no query, no PII.
 */

import { useEffect } from "react";
import Link from "next/link";
import { Compass } from "lucide-react";
import { PublicShell } from "@/components/public/PublicShell";
import { recordClientObservation } from "@/lib/client-observability";

export default function NotFound() {
  useEffect(() => {
    recordClientObservation({
      route: typeof window !== "undefined" ? window.location.pathname : "/",
      code: "UI_DEAD_END",
      at: new Date().toISOString(),
    });
  }, []);

  return (
    <PublicShell>
      <section className="mx-auto max-w-2xl rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="flex items-start gap-3">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-slate-100 text-slate-500">
            <Compass className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <p className="text-sm font-medium text-slate-500">Page not found</p>
            <h1 className="mt-1 text-2xl font-bold text-slate-900">
              We couldn&apos;t find that page
            </h1>
            <p className="mt-2 max-w-xl text-slate-600">
              The page may have moved, or the link might be out of date. Nothing is wrong with
              your account — here are the quickest ways back.
            </p>
          </div>
        </div>

        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href="/home"
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
          >
            Go home
          </Link>
          <Link
            href="/welcome/find-care"
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Find care
          </Link>
          <Link
            href="/status"
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Service status
          </Link>
          <Link
            href="/welcome/report"
            className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Report a problem
          </Link>
        </div>
      </section>
    </PublicShell>
  );
}
