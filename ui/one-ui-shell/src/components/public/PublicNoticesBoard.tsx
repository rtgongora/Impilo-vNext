"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  Loader2,
  Megaphone,
  ShieldAlert,
  CloudRain,
  Scale,
  Package,
  Activity,
} from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Public Notices & Bulletins archive (PF-W2). Browsable, no sign-in: currently-published
 * public notices across categories, from the anonymous gateway lane
 * GET /internal/v1/public/gateway/notices. Allow-listed fields only — no PII. Regulatory
 * and recall notices appear only when a named authority has approved them (guidance
 * AdvisoryAdminService); this page never shows internal disciplinary/case data.
 */

interface Notice {
  id: string;
  category: string;
  severity?: string;
  title: string;
  body: string;
  publishedAt?: string;
  primaryActions?: { label?: string; href?: string }[];
}

interface NoticesResponse {
  data?: { notices?: Notice[]; total?: number };
}

const CATEGORIES: { key: string; label: string; icon: typeof Megaphone }[] = [
  { key: "", label: "All notices", icon: Activity },
  { key: "PUBLIC_HEALTH_CAMPAIGN", label: "Campaigns", icon: Megaphone },
  { key: "SAFETY_RECALL", label: "Safety & recalls", icon: ShieldAlert },
  { key: "DISASTER_ALERT", label: "Alerts", icon: CloudRain },
  { key: "REGULATORY_NOTICE", label: "Regulatory", icon: Scale },
  { key: "PROCUREMENT_NOTICE", label: "Procurement", icon: Package },
];

const CATEGORY_META: Record<string, { label: string; icon: typeof Megaphone; tone: string }> = {
  PUBLIC_HEALTH_CAMPAIGN: { label: "Campaign", icon: Megaphone, tone: "bg-emerald-50 text-emerald-700" },
  SAFETY_RECALL: { label: "Safety / recall", icon: ShieldAlert, tone: "bg-amber-50 text-amber-800" },
  DISASTER_ALERT: { label: "Alert", icon: CloudRain, tone: "bg-red-50 text-red-700" },
  REGULATORY_NOTICE: { label: "Regulatory notice", icon: Scale, tone: "bg-slate-100 text-slate-700" },
  PROCUREMENT_NOTICE: { label: "Procurement", icon: Package, tone: "bg-indigo-50 text-indigo-700" },
  SERVICE_STATUS: { label: "Service status", icon: Activity, tone: "bg-sky-50 text-sky-700" },
};

function formatDate(iso?: string): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function PublicNoticesBoard() {
  const [category, setCategory] = useState("");
  const [notices, setNotices] = useState<Notice[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      setError("");
      const params = new URLSearchParams({ page: "0", size: "50" });
      if (category) params.set("category", category);
      try {
        const res = await apiClient.get<NoticesResponse>(
          `/internal/v1/public/gateway/notices?${params.toString()}`,
        );
        if (!cancelled) setNotices(res.data?.notices ?? []);
      } catch {
        if (!cancelled) setError("Notices are temporarily unavailable. Please try again shortly.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [category]);

  return (
    <div>
      <div className="flex flex-wrap gap-2" role="tablist" aria-label="Notice categories">
        {CATEGORIES.map((c) => {
          const Icon = c.icon;
          const active = c.key === category;
          return (
            <button
              key={c.key || "all"}
              type="button"
              role="tab"
              aria-selected={active}
              data-testid={`notices-filter-${c.key || "all"}`}
              onClick={() => setCategory(c.key)}
              className={`inline-flex min-h-[40px] items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm font-medium transition focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 ${
                active
                  ? "border-emerald-600 bg-emerald-600 text-white"
                  : "border-slate-300 bg-white text-slate-700 hover:border-emerald-400 hover:bg-emerald-50"
              }`}
            >
              <Icon className="h-4 w-4" aria-hidden />
              {c.label}
            </button>
          );
        })}
      </div>

      {loading && (
        <p className="mt-6 flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading notices…
        </p>
      )}
      {error && <p className="mt-6 text-sm text-red-700">{error}</p>}
      {notices && notices.length === 0 && !loading && (
        <p className="mt-6 text-sm text-slate-600" data-testid="notices-empty">
          There are no current notices in this category.
        </p>
      )}

      {notices && notices.length > 0 && (
        <ul className="mt-6 space-y-3" data-testid="notices-list">
          {notices.map((n) => {
            const meta = CATEGORY_META[n.category] ?? CATEGORY_META.SERVICE_STATUS;
            const Icon = meta.icon;
            const date = formatDate(n.publishedAt);
            return (
              <li
                key={n.id}
                className="rounded-xl border border-slate-200 bg-white p-4 sm:p-5"
                data-testid="notice-card"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[12px] font-semibold ${meta.tone}`}>
                    <Icon className="h-3.5 w-3.5" aria-hidden />
                    {meta.label}
                  </span>
                  {date && <span className="text-xs text-slate-500">{date}</span>}
                </div>
                <h2 className="mt-2 text-lg font-bold text-slate-900">{n.title}</h2>
                <p className="mt-1 text-[15px] leading-relaxed text-slate-600">{n.body}</p>
                {n.primaryActions && n.primaryActions.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {n.primaryActions
                      .filter((a) => a.href && a.label)
                      .map((a) => (
                        <Link
                          key={`${a.href}-${a.label}`}
                          href={a.href as string}
                          className="inline-flex min-h-[40px] items-center rounded-lg border border-emerald-300 bg-emerald-50 px-3.5 py-1.5 text-sm font-semibold text-emerald-800 hover:bg-emerald-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
                        >
                          {a.label}
                        </Link>
                      ))}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
