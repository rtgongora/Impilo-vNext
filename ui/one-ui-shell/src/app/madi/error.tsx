"use client";

import { useEffect } from "react";
import Link from "next/link";
import { AlertTriangle } from "lucide-react";

/**
 * Segment-scoped error boundary for the MADI (blood services) surface. Keeps a
 * single donor/blood-bank page failure from blanking the whole shell, and gives
 * the user a way to retry or step back without a full reload.
 */
export default function MadiError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("[MadiError]", error);
  }, [error]);

  return (
    <div className="mx-auto max-w-lg rounded-2xl border border-danger/30 bg-card p-6 shadow-impilo-card m-4">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-danger" aria-hidden />
        <div className="min-w-0">
          <h2 className="text-sm font-semibold text-foreground">
            Blood services page hit an error
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">
            {error.message.includes("fetch")
              ? "A blood-services backend may not be reachable in this environment yet."
              : error.message || "Unexpected error"}
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => reset()}
              className="rounded-xl bg-rose-600 px-4 py-2 text-xs font-medium text-white hover:bg-rose-700"
            >
              Try again
            </button>
            <Link
              href="/madi/donor"
              className="rounded-xl border border-border px-4 py-2 text-xs font-medium text-foreground hover:bg-muted"
            >
              Back to donor hub
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
