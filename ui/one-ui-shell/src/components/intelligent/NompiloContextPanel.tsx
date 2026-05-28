"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Sparkles } from "lucide-react";

export function NompiloContextPanel(props: { plane?: string; hint?: string }) {
  const pathname = usePathname() ?? "/home";
  const askHref = `/ask?from=${encodeURIComponent(pathname)}${props.plane ? `&plane=${encodeURIComponent(props.plane)}` : ""}`;

  return (
    <div
      data-testid="nompilo-context-panel"
      className="rounded-xl border border-impilo-100 bg-impilo-50/50 p-4 dark:border-impilo-900 dark:bg-impilo-950/30"
    >
      <div className="flex items-center gap-2 text-sm font-semibold text-impilo-700 dark:text-impilo-300">
        <Sparkles className="h-4 w-4" />
        Nompilo
      </div>
      <p className="mt-2 text-xs text-slate-600 dark:text-slate-400">
        {props.hint ??
          "Ask for next steps, registry lookups, transaction status, or dashboard summaries in this workspace."}
      </p>
      <Link
        href={askHref}
        className="mt-3 inline-flex text-xs font-medium text-impilo-600 hover:text-impilo-700"
      >
        Open contextual assistant →
      </Link>
    </div>
  );
}
