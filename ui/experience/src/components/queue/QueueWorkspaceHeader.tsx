import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import { Building2 } from "lucide-react";

type QueueWorkspaceAction = {
  href: string;
  label: string;
  icon: LucideIcon;
  tone?: "primary" | "secondary";
};

type QueueWorkspaceMetric = {
  label: string;
  value: string;
  detail: string;
};

export function QueueWorkspaceHeader({
  badge,
  badgeIcon: BadgeIcon,
  title,
  description,
  facilityName,
  actions,
  metrics,
}: {
  badge: string;
  badgeIcon: LucideIcon;
  title: string;
  description: string;
  facilityName?: string | null;
  actions: QueueWorkspaceAction[];
  metrics?: QueueWorkspaceMetric[];
}) {
  return (
    <div className="space-y-4">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#f8fbff_0%,#eef6ff_42%,#fffaf0_100%)] p-5 shadow-sm">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div className="space-y-3">
              <div className="inline-flex items-center gap-2 rounded-full bg-white/90 px-3 py-1 text-xs font-medium text-slate-600">
                <BadgeIcon className="h-3.5 w-3.5 text-blue-600" />
                {badge}
              </div>
              <div>
                <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
                <p className="mt-1 max-w-2xl text-sm text-slate-600">{description}</p>
              </div>
              <div className="inline-flex items-center gap-2 rounded-2xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                <Building2 className="h-4 w-4 text-slate-500" />
                <span>Facility context:</span>
                <span className="font-medium text-slate-900">{facilityName || "Select a facility to continue"}</span>
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              {actions.map((action) => (
                <Link
                  key={action.href + action.label}
                  href={action.href}
                  className={
                    action.tone === "secondary"
                      ? "inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                      : "inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
                  }
                >
                  <action.icon className="h-4 w-4" />
                  {action.label}
                </Link>
              ))}
            </div>
          </div>
        </div>

        {metrics?.length ? (
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
            {metrics.map((metric) => (
              <div key={metric.label} className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
                <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">{metric.label}</p>
                <p className="mt-2 text-2xl font-semibold text-slate-900">{metric.value}</p>
                <p className="mt-1 text-xs text-slate-500">{metric.detail}</p>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}
