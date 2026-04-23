"use client";

import { useEffect, useRef } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";
import type { RecentItem } from "@/lib/shell/types";
import { useShellStore } from "@/hooks/useShellStore";

function normalizeRecentItems(raw: unknown): RecentItem[] {
  if (!Array.isArray(raw)) return [];
  const out: RecentItem[] = [];
  for (const row of raw) {
    if (!row || typeof row !== "object") continue;
    const o = row as Record<string, unknown>;
    const id = typeof o.id === "string" ? o.id : crypto.randomUUID();
    const kind = o.kind === "app" || o.kind === "route" || o.kind === "resource" || o.kind === "search" ? o.kind : "route";
    const title = typeof o.title === "string" ? o.title : "Item";
    const href = typeof o.href === "string" ? o.href : "/home";
    const refKey = typeof o.refKey === "string" ? o.refKey : `remote:${id}`;
    const recordedAt = typeof o.recordedAt === "string" ? o.recordedAt : new Date().toISOString();
    const subtitle = typeof o.subtitle === "string" ? o.subtitle : undefined;
    const sensitivity =
      o.sensitivity === "clinical" || o.sensitivity === "registry" || o.sensitivity === "normal"
        ? o.sensitivity
        : undefined;
    out.push({ id, kind, title, subtitle, href, refKey, recordedAt, sensitivity });
  }
  return out.slice(0, 40);
}

/**
 * Hydrates shell pins/recents from Redis via BFF after session restore, and debounces writes back.
 * Server applies Tshepo visibility filtering on read/write. GET results are **merged** with session
 * (see `mergeRemoteWorkspaceWithSession`).
 */
export function ShellWorkspaceRemoteSync() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const userId = useAuthStore((s) => s.user?.id);
  const hydratedRemote = useRef(false);

  useEffect(() => {
    if (!isAuthenticated || !userId) {
      hydratedRemote.current = false;
      return;
    }
    useShellStore.getState().hydrateFromSession();
    let cancelled = false;
    void (async () => {
      try {
        const res = await apiClient.get<{
          data?: { pinnedAppCodes?: string[]; recentItems?: unknown[] };
        }>("/internal/v1/shell/workspace-state");
        if (cancelled) return;
        const d = res?.data;
        if (d) {
          const pins = Array.isArray(d.pinnedAppCodes) ? d.pinnedAppCodes : [];
          const recents = normalizeRecentItems(d.recentItems ?? []);
          useShellStore.getState().mergeRemoteWorkspaceWithSession({ pinnedAppCodes: pins, recentItems: recents });
        }
        hydratedRemote.current = true;
      } catch {
        hydratedRemote.current = true;
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, userId]);

  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    if (!isAuthenticated || !userId) return;

    let lastSerialized = JSON.stringify({
      pins: useShellStore.getState().pinnedAppCodes,
      recents: useShellStore.getState().recentItems,
    });
    const unsub = useShellStore.subscribe((s) => {
      const serialized = JSON.stringify({ pins: s.pinnedAppCodes, recents: s.recentItems });
      if (serialized === lastSerialized) return;
      lastSerialized = serialized;
      if (!hydratedRemote.current) return;
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(() => {
        const { pinnedAppCodes, recentItems } = useShellStore.getState();
        const payload = {
          pinnedAppCodes,
          recentItems: recentItems.map(({ id, kind, title, subtitle, href, refKey, recordedAt, sensitivity }) => ({
            id,
            kind,
            title,
            subtitle,
            href,
            refKey,
            recordedAt,
            sensitivity,
          })),
        };
        void apiClient.put("/internal/v1/shell/workspace-state", payload).catch(() => {});
      }, 900);
    });

    return () => {
      unsub();
      if (saveTimer.current) clearTimeout(saveTimer.current);
    };
  }, [isAuthenticated, userId]);

  return null;
}
