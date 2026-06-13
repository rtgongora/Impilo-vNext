import Link from "next/link";
import type { LucideIcon } from "lucide-react";

type WorkflowAction = {
  href: string;
  label: string;
  icon: LucideIcon;
  tone?: "primary" | "secondary";
};

type WorkflowMetric = {
  label: string;
  value: string;
  detail: string;
};

type WorkflowContext = {
  label: string;
  value: string;
};

export function WorkflowHeader({
  badge,
  badgeIcon: BadgeIcon,
  title,
  description,
  context,
  actions,
  metrics,
}: {
  badge: string;
  badgeIcon: LucideIcon;
  title: string;
  description: string;
  context: WorkflowContext[];
  actions: WorkflowAction[];
  metrics: WorkflowMetric[];
}) {
  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1.45fr)_minmax(0,1fr)]">
      <div className="rounded-3xl border border-[color:var(--border-soft)] bg-[linear-gradient(135deg,var(--surface-soft)_0%,var(--surface-warm)_48%,#f7fbff_100%)] p-5 shadow-impilo-card">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="space-y-3">
            <div className="inline-flex items-center gap-2 rounded-full border border-[color:var(--border-soft)] bg-card/90 px-3 py-1 text-xs font-medium text-[color:var(--text-secondary)]">
              <BadgeIcon className="h-3.5 w-3.5 text-[color:var(--primary)]" />
              {badge}
            </div>
            <div>
              <h2 className="text-lg font-semibold text-[color:var(--text-primary)]">{title}</h2>
              <p className="mt-1 max-w-2xl text-sm text-[color:var(--text-secondary)]">{description}</p>
            </div>
            <div className="flex flex-wrap gap-2">
              {context.map((item) => (
                <div
                  key={`${item.label}-${item.value}`}
                  className="inline-flex items-center gap-2 rounded-2xl border border-[color:var(--border-soft)] bg-card px-3 py-2 text-sm text-[color:var(--text-secondary)]"
                >
                  <span>{item.label}:</span>
                  <span className="font-medium text-[color:var(--text-primary)]">{item.value}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="flex flex-wrap gap-2">
            {actions.map((action) => (
              <Link
                key={`${action.href}-${action.label}`}
                href={action.href}
                className={
                  action.tone === "secondary"
                    ? "inline-flex items-center gap-1.5 rounded-full border border-[color:var(--border-soft)] bg-card px-4 py-2 text-sm font-medium text-[color:var(--text-secondary)] transition-colors hover:bg-[color:var(--surface-soft)]"
                    : "inline-flex items-center gap-1.5 rounded-full bg-[color:var(--primary)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[color:var(--primary-hover)]"
                }
              >
                <action.icon className="h-4 w-4" />
                {action.label}
              </Link>
            ))}
          </div>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-1">
        {metrics.map((metric) => (
          <div key={metric.label} className="rounded-3xl border border-[color:var(--border-soft)] bg-card p-4 shadow-impilo-card">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-[color:var(--text-muted)]">{metric.label}</p>
            <p className="mt-2 text-2xl font-semibold text-[color:var(--text-primary)]">{metric.value}</p>
            <p className="mt-1 text-xs text-[color:var(--text-muted)]">{metric.detail}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
