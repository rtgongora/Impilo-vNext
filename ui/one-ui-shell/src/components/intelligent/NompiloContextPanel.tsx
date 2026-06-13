"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NompiloTipCard } from "shared-ui";

export function NompiloContextPanel(props: { plane?: string; hint?: string }) {
  const pathname = usePathname() ?? "/home";
  const askHref = `/ask?from=${encodeURIComponent(pathname)}${props.plane ? `&plane=${encodeURIComponent(props.plane)}` : ""}`;

  return (
    <div data-testid="nompilo-context-panel">
      <NompiloTipCard
        message={
          props.hint ??
          "Ask for next steps, registry lookups, transaction status, or dashboard summaries in this workspace."
        }
      />
      <Link
        href={askHref}
        className="mt-3 inline-flex text-xs font-medium text-primary hover:text-primary-hover"
      >
        Open contextual assistant →
      </Link>
    </div>
  );
}
