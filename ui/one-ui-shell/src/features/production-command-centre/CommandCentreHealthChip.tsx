"use client";

import type { TileIntegrationHealth } from "@/features/production-command-centre/tile-integration-health";
import { tileIntegrationHealthLabel } from "@/features/production-command-centre/tile-integration-health";

const CHIP_STYLES: Record<TileIntegrationHealth, string> = {
  checking: "bg-slate-100 text-slate-600",
  hub_down: "bg-rose-50 text-rose-700",
  routed: "bg-emerald-50 text-emerald-700",
  not_in_hub: "bg-amber-50 text-amber-800",
  not_applicable: "",
};

export function CommandCentreHealthChip({ state }: { state: TileIntegrationHealth }) {
  if (state === "not_applicable") return null;

  const label = tileIntegrationHealthLabel(state);
  if (!label) return null;

  return (
    <span
      className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${CHIP_STYLES[state]}`}
      title="Integration hub route probe via Experience BFF"
    >
      {label}
    </span>
  );
}
