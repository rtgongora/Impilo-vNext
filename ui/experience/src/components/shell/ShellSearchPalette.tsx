"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Hash, Loader2, Search, X } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { fetchShellFusionHints } from "@/hooks/useIntelligence";
import { useShellStore } from "@/hooks/useShellStore";
import { apiClient } from "@/lib/api-client";
import { emitShellEvent } from "@/lib/shell/shell-events";
import type { ShellFusionHint } from "@/lib/intelligence/types";
import { normalizeFusionHints } from "@/lib/shell/normalize-fusion-hints";
import { listVisibleShellApps, visibleShellCommands } from "@/lib/shell/app-registry";
import type { ShellCommand } from "@/lib/shell/types";
import { ShellIcon } from "./ShellIcon";
import { resolveIndexHitHref, type IndexSearchHitRef } from "@/lib/shell/search-hit-routing";

interface PlatformHit extends IndexSearchHitRef {
  id: string;
  title: string;
  subtitle: string;
}

function normalizeHits(raw: unknown): PlatformHit[] {
  if (!raw || typeof raw !== "object") return [];
  const root = raw as Record<string, unknown>;
  const data = root.data as Record<string, unknown> | undefined;
  const inner = (data?.data ?? data) as Record<string, unknown> | undefined;
  const results = inner?.results;
  if (!Array.isArray(results)) return [];
  return results
    .map((item, index) => {
      if (!item || typeof item !== "object") return null;
      const o = item as Record<string, unknown>;
      const id = typeof o.id === "string" ? o.id : `hit-${index}`;
      const entityType = typeof o.entityType === "string" ? o.entityType : "entity";
      const entityId = typeof o.entityId === "string" ? o.entityId : "";
      const cj = o.contentJson as Record<string, unknown> | undefined;
      let title =
        (cj && typeof cj.title === "string" && cj.title) ||
        (cj && typeof cj.name === "string" && cj.name) ||
        (typeof o.searchableText === "string" && o.searchableText.slice(0, 120)) ||
        entityType;
      const text = typeof o.searchableText === "string" ? o.searchableText.trim() : "";
      const subtitle = text && text !== title ? text.slice(0, 160) : entityType;
      return {
        id,
        title: String(title),
        subtitle: String(subtitle),
        entityType,
        entityId,
        contentJson: cj,
      };
    })
    .filter(Boolean) as PlatformHit[];
}

export function ShellSearchPalette() {
  const router = useRouter();
  const hasRole = useAuthStore((s) => s.hasRole);
  const facilityId = useFacilityStore((s) => s.facility?.id ?? null);
  const setSearchOpen = useShellStore((s) => s.setSearchOpen);
  const searchPaletteFocusTick = useShellStore((s) => s.searchPaletteFocusTick);
  const recordRecent = useShellStore((s) => s.recordRecent);
  const openTasks = useShellStore((s) => s.openTasks);
  const recentItems = useShellStore((s) => s.recentItems);

  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [fusionLoading, setFusionLoading] = useState(false);
  const [platformHits, setPlatformHits] = useState<PlatformHit[]>([]);
  const [fusionHints, setFusionHints] = useState<ShellFusionHint[]>([]);

  const q = query.trim().toLowerCase();

  const apps = useMemo(() => {
    const list = listVisibleShellApps(hasRole);
    if (!q) return list.slice(0, 8);
    return list.filter(
      (a) =>
        a.name.toLowerCase().includes(q) ||
        a.description.toLowerCase().includes(q) ||
        a.appCode.toLowerCase().includes(q),
    );
  }, [hasRole, q]);

  const tasks = useMemo(() => {
    if (!q) return openTasks.slice(0, 8);
    return openTasks.filter(
      (t) => t.title.toLowerCase().includes(q) || t.route.toLowerCase().includes(q),
    );
  }, [openTasks, q]);

  const recents = useMemo(() => {
    if (!q) return recentItems.slice(0, 8);
    return recentItems.filter(
      (r) => r.title.toLowerCase().includes(q) || (r.subtitle ?? "").toLowerCase().includes(q),
    );
  }, [recentItems, q]);

  const commands = useMemo(() => {
    const list = visibleShellCommands(hasRole);
    if (!q) return list.slice(0, 6);
    return list.filter((c) => {
      const blob = [c.label, c.description, ...c.keywords].join(" ").toLowerCase();
      return blob.includes(q);
    });
  }, [hasRole, q]);

  const runCommand = useCallback(
    (cmd: ShellCommand) => {
      let keepSearchOpen = false;
      switch (cmd.action.type) {
        case "navigate":
          router.push(cmd.action.href);
          break;
        case "open-search":
          useShellStore.getState().focusSearchPalette();
          keepSearchOpen = true;
          break;
        case "open-start":
          useShellStore.getState().setSearchOpen(false);
          useShellStore.getState().setStartOpen(true);
          break;
        case "open-task-manager":
          router.push("/shell/task-manager");
          break;
        case "open-file-manager":
          router.push("/shell/file-manager");
          break;
        default:
          break;
      }
      if (cmd.action.type !== "open-search") {
        recordRecent({
          kind: "search",
          title: cmd.label,
          subtitle: "Command",
          href: commandRecentHref(cmd),
          refKey: `cmd:${cmd.id}`,
          sensitivity: "normal",
        });
      }
      if (!keepSearchOpen) setSearchOpen(false);
    },
    [recordRecent, router, setSearchOpen],
  );

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setPlatformHits([]);
      setLoading(false);
      return;
    }
    const t = setTimeout(() => {
      setLoading(true);
      emitShellEvent("search_executed", { scope: "platform_index", q: trimmed });
      const sp = new URLSearchParams({ q: trimmed, page: "0", size: "8" });
      apiClient
        .get<unknown>(`/internal/v1/search?${sp.toString()}`)
        .then((res) => {
          setPlatformHits(normalizeHits(res));
        })
        .catch(() => setPlatformHits([]))
        .finally(() => setLoading(false));
    }, 280);
    return () => clearTimeout(t);
  }, [query]);

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setFusionHints([]);
      setFusionLoading(false);
      return;
    }
    let cancelled = false;
    const t = setTimeout(() => {
      setFusionLoading(true);
      fetchShellFusionHints(trimmed, facilityId)
        .then((res) => {
          if (cancelled) return;
          const hints = normalizeFusionHints(res);
          setFusionHints(hints);
          emitShellEvent("fusion_hints_loaded", { count: hints.length, q: trimmed });
        })
        .catch(() => {
          if (!cancelled) setFusionHints([]);
        })
        .finally(() => {
          if (!cancelled) setFusionLoading(false);
        });
    }, 280);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [query, facilityId]);

  useEffect(() => {
    if (searchPaletteFocusTick > 0) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [searchPaletteFocusTick]);

  return (
    <div className="fixed inset-0 z-[10001] flex items-start justify-center pt-[12vh]">
      <button
        type="button"
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        aria-label="Close search"
        onClick={() => setSearchOpen(false)}
      />
      <div className="relative w-full max-w-2xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-950">
        <div className="flex items-center gap-2 border-b border-slate-100 px-3 py-2 dark:border-slate-800">
          <Search className="h-5 w-5 shrink-0 text-slate-400" />
          <input
            ref={inputRef}
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search apps, tasks, recents, platform index…"
            className="min-w-0 flex-1 border-0 bg-transparent py-2 text-base text-slate-900 outline-none placeholder:text-slate-400 dark:text-slate-100"
          />
          {loading || fusionLoading ? <Loader2 className="h-5 w-5 animate-spin text-impilo-500" /> : null}
          <button
            type="button"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-900"
            onClick={() => setSearchOpen(false)}
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="max-h-[60vh] overflow-y-auto px-2 py-2 text-sm">
          {commands.length > 0 ? (
            <section className="mb-3">
              <h3 className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                Quick actions
              </h3>
              <ul>
                {commands.map((cmd) => (
                  <li key={cmd.id}>
                    <button
                      type="button"
                      onClick={() => runCommand(cmd)}
                      className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                    >
                      <Hash className="h-4 w-4 text-violet-500" />
                      <span>
                        <span className="font-medium text-slate-800 dark:text-slate-100">{cmd.label}</span>
                        {cmd.description ? (
                          <span className="block text-xs text-slate-500">{cmd.description}</span>
                        ) : null}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {apps.length > 0 ? (
            <section className="mb-3">
              <h3 className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">Apps</h3>
              <ul>
                {apps.map((app) => (
                  <li key={app.appCode}>
                    <button
                      type="button"
                      onClick={() => {
                        useShellStore.getState().launchApp(app, (href) => router.push(href));
                        setSearchOpen(false);
                      }}
                      className="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                    >
                      <ShellIcon name={app.icon} className="h-4 w-4 text-slate-600" />
                      <span>
                        <span className="font-medium text-slate-800 dark:text-slate-100">{app.name}</span>
                        <span className="block text-xs text-slate-500">{app.description}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {tasks.length > 0 ? (
            <section className="mb-3">
              <h3 className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                Open tasks
              </h3>
              <ul>
                {tasks.map((task) => (
                  <li key={task.id}>
                    <button
                      type="button"
                      onClick={() => {
                        router.push(task.route);
                        useShellStore.getState().setActiveTask(task.id);
                        setSearchOpen(false);
                      }}
                      className="flex w-full flex-col rounded-lg px-2 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                    >
                      <span className="font-medium text-slate-800 dark:text-slate-100">{task.title}</span>
                      <span className="text-xs text-slate-500">{task.route}</span>
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {recents.length > 0 ? (
            <section className="mb-3">
              <h3 className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">Recent</h3>
              <ul>
                {recents.map((r) => (
                  <li key={r.id}>
                    <button
                      type="button"
                      onClick={() => {
                        router.push(r.href);
                        setSearchOpen(false);
                      }}
                      className="flex w-full flex-col rounded-lg px-2 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                    >
                      <span className="font-medium text-slate-800 dark:text-slate-100">{r.title}</span>
                      {r.subtitle ? <span className="text-xs text-slate-500">{r.subtitle}</span> : null}
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {q.length >= 2 && fusionHints.length > 0 ? (
            <section className="mb-3">
              <h3 className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                Fused hints
              </h3>
              <p className="px-2 pb-1 text-[10px] text-slate-500">
                Index + guidance knowledge (Health Intelligence plane). Same visibility rules as underlying services.
              </p>
              <ul>
                {fusionHints.map((h) => {
                  const body = (
                    <>
                      <span className="font-medium text-slate-800 dark:text-slate-100">{h.title}</span>
                      <span className="block text-xs text-slate-500">
                        {h.source.replace(/_/g, " ")}
                        {h.href ? " · Open" : ""} · {h.subtitle}
                      </span>
                    </>
                  );
                  if (h.href) {
                    return (
                      <li key={h.id}>
                        <button
                          type="button"
                          className="w-full rounded-lg px-2 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                          onClick={() => {
                            recordRecent({
                              kind: "resource",
                              title: h.title,
                              subtitle: `${h.source} · fused`,
                              href: h.href!,
                              refKey: `fusion:${h.id}`,
                              sensitivity: "normal",
                            });
                            router.push(h.href!);
                            setSearchOpen(false);
                          }}
                        >
                          {body}
                        </button>
                      </li>
                    );
                  }
                  return (
                    <li key={h.id}>
                      <div className="rounded-lg px-2 py-2">{body}</div>
                    </li>
                  );
                })}
              </ul>
            </section>
          ) : null}

          {q.length >= 2 ? (
            <section>
              <h3 className="px-2 pb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                Platform index
              </h3>
              {platformHits.length === 0 && !loading ? (
                <p className="px-2 py-3 text-xs text-slate-500">No indexed matches for this query.</p>
              ) : (
                <ul>
                  {platformHits.map((h) => {
                    const href = resolveIndexHitHref(h);
                    const body = (
                      <>
                        <span className="font-medium text-slate-800 dark:text-slate-100">{h.title}</span>
                        <span className="block text-xs text-slate-500">
                          {h.entityType}
                          {href ? " · Open" : ""} · {h.subtitle}
                        </span>
                      </>
                    );
                    if (href) {
                      return (
                        <li key={h.id}>
                          <button
                            type="button"
                            className="w-full rounded-lg px-2 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                            onClick={() => {
                              recordRecent({
                                kind: "resource",
                                title: h.title,
                                subtitle: `${h.entityType} · index`,
                                href,
                                refKey: `search-hit:${h.id}`,
                                sensitivity: h.entityType.toLowerCase().includes("patient")
                                  ? "clinical"
                                  : "normal",
                              });
                              router.push(href);
                              setSearchOpen(false);
                            }}
                          >
                            {body}
                          </button>
                        </li>
                      );
                    }
                    return (
                      <li key={h.id}>
                        <div className="rounded-lg px-2 py-2">{body}</div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </section>
          ) : null}
        </div>

        <div className="border-t border-slate-100 px-3 py-2 text-[10px] text-slate-400 dark:border-slate-800">
          Ctrl+K toggles search. Results respect your current roles; clinical routes still follow facility/workspace/shift
          guards.
        </div>
      </div>
    </div>
  );
}

function commandRecentHref(cmd: ShellCommand): string {
  switch (cmd.action.type) {
    case "navigate":
      return cmd.action.href;
    case "open-file-manager":
      return "/shell/file-manager";
    case "open-task-manager":
      return "/shell/task-manager";
    case "open-search":
      return "/search";
    case "open-start":
      return "/home";
    default:
      return "/home";
  }
}
