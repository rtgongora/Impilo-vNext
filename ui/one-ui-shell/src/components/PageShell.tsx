"use client";

/**
 * PageShell — Standard page wrapper with title and empty state.
 * Used by all route pages to display correct headings/labels from 02.
 */

import { ModuleWorkspaceHero } from "@/components/workspace/ModuleWorkspaceHero";
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
        <ModuleWorkspaceHero
          title={title}
          subtitle={subtitle}
          serviceSlug={resolvedSlug}
          icon={resolvedSlug ? undefined : icon}
        />
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
