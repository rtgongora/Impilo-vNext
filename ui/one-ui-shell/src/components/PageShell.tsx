"use client";

/**
 * PageShell — Standard page wrapper with title and empty state.
 * Used by all route pages to display correct headings/labels from 02.
 */

import { ServiceLogo } from "@/components/branding/ServiceLogo";

interface PageShellProps {
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  /** Sovereign service slug — renders branded logo in header when set */
  serviceSlug?: string;
  emptyStateLabel?: string;
  children?: React.ReactNode;
}

export function PageShell({ title, subtitle, icon, serviceSlug, emptyStateLabel, children }: PageShellProps) {
  return (
    <div className="space-y-5">
      <div className="impilo-surface-card impilo-subtle-african-accent mb-6 p-6">
        <div className="flex items-center gap-3">
          {serviceSlug ? (
            <ServiceLogo slug={serviceSlug} size="header" />
          ) : icon ? (
            <div className="text-[color:var(--primary)]">{icon}</div>
          ) : null}
          <h1 className="text-2xl font-bold text-gray-900 text-[color:var(--text-primary)] md:text-3xl">{title}</h1>
        </div>
        {subtitle && <p className="text-sm text-[color:var(--text-secondary)] mt-2 max-w-3xl">{subtitle}</p>}
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
