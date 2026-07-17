"use client";

/**
 * Live service-status board (gateway ADR W4). Consumes the public gateway lane
 * GET /internal/v1/public/gateway/service-status and renders each citizen service group
 * honestly:
 *
 *   - a group the backend actually monitors (monitored:true, feed live:true) shows its LIVE state;
 *   - any other group — not monitored, feed not live, or the fetch failed — falls back to the
 *     authored INDICATIVE state from SERVICE_STATUS_BOARD, clearly tagged "indicative".
 *
 * We NEVER fabricate a green "Operational" for an unmonitored group: an unmonitored group is
 * shown with its authored indicative label (tagged indicative), and a monitored-but-unresolved
 * group is shown as UNKNOWN ("Status not monitored"). On total failure the whole board renders
 * as the static indicative fallback, so the page always shows something honest.
 *
 * The authored SERVICE_STATUS_BOARD is the display backbone (the citizen-facing group set and the
 * descriptions); live data is overlaid onto it by group name.
 */

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { useI18n } from "@/lib/i18n/useI18n";
import {
  SERVICE_STATUS_BOARD,
  STATE_META,
  STATUS_BOARD_UPDATED,
  type ServiceState,
} from "@/lib/service-status-board";

/** Frozen upstream contract shape. */
interface LiveGroup {
  group: string;
  description: string;
  state: string;
  monitored: boolean;
}
interface LiveStatus {
  updatedAt: string;
  live: boolean;
  groups: LiveGroup[];
}

/** Live states the backend may return (a superset overlaps our authored union). */
const LIVE_STATES: ServiceState[] = [
  "OPERATIONAL",
  "DEGRADED",
  "PARTIAL",
  "UNAVAILABLE",
  "MAINTENANCE",
  "UNKNOWN",
];

function coerceLiveState(raw: string): ServiceState {
  return (LIVE_STATES as string[]).includes(raw) ? (raw as ServiceState) : "UNKNOWN";
}

interface DisplayRow {
  group: string;
  description: string;
  state: ServiceState;
  /** true → rendered from the live feed; false → authored indicative fallback. */
  live: boolean;
}

/**
 * Overlay live data onto the authored backbone. A group is shown LIVE only when the feed is live
 * AND that group is present and monitored; otherwise it stays authored-indicative.
 */
function buildRows(feed: LiveStatus | null): DisplayRow[] {
  const liveByGroup = new Map<string, LiveGroup>();
  if (feed?.live) {
    for (const g of feed.groups ?? []) liveByGroup.set(g.group, g);
  }
  return SERVICE_STATUS_BOARD.map((entry) => {
    const match = liveByGroup.get(entry.group);
    if (feed?.live && match && match.monitored) {
      return {
        group: entry.group,
        description: entry.description,
        state: coerceLiveState(match.state),
        live: true,
      };
    }
    return {
      group: entry.group,
      description: entry.description,
      state: entry.state,
      live: false,
    };
  });
}

export function ServiceStatusBoard() {
  const { t } = useI18n();
  const [feed, setFeed] = useState<LiveStatus | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const res = await apiClient.get<LiveStatus>(
          "/internal/v1/public/gateway/service-status",
        );
        if (active) setFeed(res);
      } catch {
        // Best-effort: on any failure we render the authored indicative fallback.
        if (active) setFeed(null);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  const rows = buildRows(feed);
  const anyLive = rows.some((r) => r.live);
  const updatedLabel = feed?.live && feed.updatedAt ? feed.updatedAt : STATUS_BOARD_UPDATED;

  return (
    <>
      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">{t("public.status.title")}</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          {t("public.status.intro")}
        </p>

        {anyLive ? (
          <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
            <strong>Live where monitored.</strong> Groups we actively monitor show their live
            state; the rest are shown as an authored, indicative overview. For an emergency, always
            use{" "}
            <Link href="/welcome/emergency" className="font-semibold underline">
              emergency help
            </Link>
            .
          </div>
        ) : (
          <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
            <strong>Indicative only.</strong> Live status isn&apos;t available right now, so this
            page shows an authored overview last reviewed on{" "}
            {new Date(STATUS_BOARD_UPDATED).toLocaleDateString()}. For an emergency, always use{" "}
            <Link href="/welcome/emergency" className="font-semibold underline">
              emergency help
            </Link>
            .
          </div>
        )}
      </section>

      <section className="mt-6 overflow-hidden rounded-2xl border border-slate-200 bg-white">
        {loading ? (
          <div className="flex items-center gap-2 p-6 text-sm text-slate-500">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            Checking live status…
          </div>
        ) : (
          <ul className="divide-y divide-slate-100">
            {rows.map((row) => {
              const meta = STATE_META[row.state];
              return (
                <li key={row.group} className="flex items-start justify-between gap-4 p-5">
                  <div>
                    <p className="font-semibold text-slate-900">{row.group}</p>
                    <p className="mt-1 text-sm text-slate-600">{row.description}</p>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    <span
                      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${meta.badge}`}
                    >
                      <span className={`h-2 w-2 rounded-full ${meta.dot}`} aria-hidden="true" />
                      {meta.label}
                    </span>
                    <span
                      className={`text-[11px] font-medium uppercase tracking-wide ${
                        row.live ? "text-emerald-600" : "text-slate-400"
                      }`}
                    >
                      {row.live ? "Live" : "Indicative"}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <p className="mt-3 text-xs text-slate-400">
        {anyLive ? "Live status last updated" : "Overview last reviewed"}:{" "}
        {(() => {
          const d = new Date(updatedLabel);
          return Number.isNaN(d.getTime()) ? String(updatedLabel) : d.toLocaleString();
        })()}
      </p>
    </>
  );
}
