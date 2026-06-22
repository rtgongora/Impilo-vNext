"use client";

import type { ReactNode } from "react";
import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { getServiceBranding, type ServiceBrandingSlug } from "@/config/serviceBranding";
import { ShellIcon } from "@/components/shell/ShellIcon";

export interface ModuleWorkspaceHeroProps {
  title: string;
  subtitle?: string;
  /** Sovereign service slug — renders branded logo from registry when set */
  serviceSlug?: ServiceBrandingSlug | string;
  /** Optional icon when no service slug is available */
  icon?: ReactNode;
  /** Optional accent colour class for the logo tile border/background */
  accentClassName?: string;
}

/**
 * Branded hero header for workspace/module pages — protected logo zone,
 * prominent title, and descriptor. Reuses sovereign service branding registry.
 */
export function ModuleWorkspaceHero({
  title,
  subtitle,
  serviceSlug,
  icon,
  accentClassName,
}: ModuleWorkspaceHeroProps) {
  const branding = serviceSlug ? getServiceBranding(serviceSlug) : undefined;
  const accent =
    accentClassName ??
    "border-[color:var(--primary-muted)]/40 bg-[color:var(--primary-soft)]/60";

  return (
    <section
      className="impilo-module-hero relative mb-5 overflow-hidden rounded-3xl border border-[color:var(--border-strong)] p-4 sm:p-5 md:p-6"
      data-testid="module-workspace-hero"
    >
      <div className="relative z-10 flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-5">
        <div
          className={`module-hero-logo-tile inline-flex shrink-0 items-center justify-center rounded-2xl border p-3 shadow-sm ${accent}`}
          data-testid="module-hero-logo-tile"
        >
          {serviceSlug ? (
            <ServiceLogo slug={serviceSlug} size="hero" />
          ) : icon ? (
            <div className="flex h-[4.5rem] w-[4.5rem] items-center justify-center rounded-xl bg-[color:var(--primary-soft)] text-[color:var(--primary)]">
              {icon}
            </div>
          ) : branding ? (
            <span className="flex h-[4.5rem] w-[4.5rem] items-center justify-center rounded-xl bg-[color:var(--surface-soft)]">
              <ShellIcon
                name={branding.fallbackIcon}
                className="h-8 w-8 text-[color:var(--text-secondary)]"
              />
            </span>
          ) : null}
        </div>

        <div className="min-w-0 flex-1">
          <h1 className="text-xl font-semibold tracking-tight text-[color:var(--text-primary)] md:text-2xl lg:text-[1.75rem]">
            {title}
          </h1>
          {subtitle ? (
            <p className="mt-1.5 max-w-3xl text-sm leading-relaxed text-[color:var(--text-muted)] md:text-base">
              {subtitle}
            </p>
          ) : null}
          {branding?.domain ? (
            <p className="mt-2 inline-flex rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface)]/80 px-2.5 py-0.5 text-[11px] font-medium uppercase tracking-wide text-[color:var(--text-secondary)]">
              {branding.domain}
            </p>
          ) : null}
        </div>
      </div>
    </section>
  );
}
