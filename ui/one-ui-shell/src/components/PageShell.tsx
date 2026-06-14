"use client";

/**
 * PageShell — Standard page wrapper with title and empty state.
 * Used by all route pages to display correct headings/labels from 02.
 */

import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { useInferredServiceSlug } from "@/hooks/useInferredServiceSlug";

interface PageShellProps {
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  /** When true, skip the large title card (e.g. Home already has its own hero). */
  hideHeader?: boolean;
  /** Sovereign service slug — renders branded logo in header when set (overrides route inference) */
  serviceSlug?: string;
  emptyStateLabel?: string;
  children?: React.ReactNode;
}

export function PageShell({ title, subtitle, icon, hideHeader, serviceSlug, emptyStateLabel, children }: PageShellProps) {
  const resolvedSlug = useInferredServiceSlug(serviceSlug);

  return (
    <div className={hideHeader ? "" : "space-y-5"}>
      {!hideHeader ? (
        <div className="impilo-surface-card impilo-subtle-african-accent relative mb-4 overflow-hidden border-l-4 border-l-primary p-4 md:p-5">
          <div className="flex items-center gap-3">
            {resolvedSlug ? (
              <ServiceLogo slug={resolvedSlug} size="header" />
            ) : icon ? (
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-soft text-primary">{icon}</div>
            ) : null}
            <h1 className="text-xl font-bold text-foreground md:text-2xl">{title}</h1>
          </div>
          {subtitle && <p className="mt-1.5 max-w-3xl text-sm text-muted-foreground">{subtitle}</p>}
        </div>
      ) : null}
      {children || (
        <div className="impilo-surface-card relative overflow-hidden p-12 text-center">
          <div
            className="pointer-events-none absolute inset-0 opacity-[0.08]"
            style={{
              backgroundImage: "url('/brand/mark-rgb.svg')",
              backgroundRepeat: "no-repeat",
              backgroundPosition: "center",
              backgroundSize: "160px",
            }}
            aria-hidden
          />
          <p className="relative text-muted-foreground text-sm">
            {emptyStateLabel || "No data available"}
          </p>
        </div>
      )}
    </div>
  );
}
