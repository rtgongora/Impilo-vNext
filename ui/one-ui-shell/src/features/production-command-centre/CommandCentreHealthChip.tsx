"use client";

import type { TileIntegrationHealth } from "@/features/production-command-centre/tile-integration-health";
import { tileIntegrationHealthLabel } from "@/features/production-command-centre/tile-integration-health";

const CHIP_STYLES: Record<TileIntegrationHealth, string> = {
  checking: "bg-neutral-100 text-muted-foreground",
  hub_down: "bg-danger-soft text-danger",
  routed: "bg-success-soft text-primary-hover",
  not_in_hub: "bg-warning-soft text-warning-foreground",
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
