"use client";

/**
 * PageShell — Standard page wrapper with title and empty state.
 * Used by all route pages to display correct headings/labels from 02.
 */

interface PageShellProps {
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  actions?: React.ReactNode;
  emptyStateLabel?: string;
  children?: React.ReactNode;
}

export function PageShell({ title, subtitle, icon, actions, emptyStateLabel, children }: PageShellProps) {
  return (
    <div className="space-y-5">
      <div className="impilo-surface-card impilo-subtle-african-accent mb-6 p-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              {icon && <div className="text-[color:var(--primary)]">{icon}</div>}
              <h1 className="text-2xl font-bold text-gray-900 text-[color:var(--text-primary)] md:text-3xl">{title}</h1>
            </div>
            {subtitle && <p className="text-sm text-[color:var(--text-secondary)] mt-2 max-w-3xl">{subtitle}</p>}
          </div>
          {actions ? <div className="shrink-0">{actions}</div> : null}
        </div>
      </div>
      {children || (
        <div className="impilo-surface-card p-12 text-center">
          <p className="text-[color:var(--text-muted)] text-sm">
            {emptyStateLabel || "No data available"}
          </p>
        </div>
      )}
    </div>
  );
}
