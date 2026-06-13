"use client";

/**
 * NdilaOfflineNotice — small banner that tells the user when Ndila is using
 * cached / offline-queued data. Designed for the Provider + Citizen apps as
 * well as low-connectivity One UI dashboards.
 */

import { CloudOff } from "lucide-react";

interface Props {
  offline: boolean;
  pendingSyncCount?: number;
  lastSyncAt?: string;
}

export function NdilaOfflineNotice({ offline, pendingSyncCount, lastSyncAt }: Props) {
  if (!offline && !pendingSyncCount) return null;
  return (
    <div className="rounded-md border border-warning/35 bg-warning-soft px-3 py-2 text-warning-foreground text-[11px] flex items-center gap-2">
      <CloudOff className="h-3.5 w-3.5 text-warning-foreground" />
      <div className="flex-1">
        <div className="font-medium">Ndila is operating in low-connectivity mode</div>
        <div className="text-[10px] text-warning-foreground">
          Map tiles, boundaries and last-known locations are served from cache.
          {pendingSyncCount && pendingSyncCount > 0
            ? ` ${pendingSyncCount} captures queued for sync.`
            : ""}
          {lastSyncAt ? ` Last sync ${lastSyncAt}.` : ""}
        </div>
      </div>
    </div>
  );
}
