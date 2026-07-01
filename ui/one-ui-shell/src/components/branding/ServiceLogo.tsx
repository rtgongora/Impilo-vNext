"use client";

import { useState } from "react";
import { ShellIcon } from "@/components/shell/ShellIcon";
import {
  getServiceBranding,
  type ServiceBrandingSlug,
} from "@/config/serviceBranding";

/**
 * Context-aware service logo variants. Ordered small → large.
 * - compact: nav/search/sidebar icon tiles (~24px square)
 * - nav: wordmark beside a label (height-capped, width auto)
 * - card: dashboard / module-card icon tile (~40px square)
 * - header: page/section header logo (~56px square)
 * - hero: service landing/open page hero logo
 * - watermark: subtle decorative brand wash (aria-hidden, very low opacity)
 *
 * `ServiceLogoSize` is kept as a backwards-compatible alias of `ServiceLogoVariant`.
 */
export type ServiceLogoVariant =
  | "compact"
  | "nav"
  | "card"
  | "header"
  | "hero"
  | "watermark";
export type ServiceLogoSize = ServiceLogoVariant;

interface VariantSpec {
  /** image element sizing */
  img: string;
  /** fallback lucide icon sizing */
  icon: string;
  /** fallback tile sizing (square container behind the fallback icon) */
  tile: string;
  /** apply a dark-mode-only safe plate so full-colour dark-ink logos stay readable */
  plate: boolean;
  /** decorative-only (aria-hidden, no name, low opacity) */
  decorative: boolean;
}

/** Central sizing/token logic — the single source of truth for logo dimensions. */
const VARIANTS: Record<ServiceLogoVariant, VariantSpec> = {
  compact: { img: "h-6 w-6", icon: "h-4 w-4", tile: "h-6 w-6", plate: false, decorative: false },
  nav: { img: "h-6 w-auto max-w-[150px]", icon: "h-4 w-4", tile: "h-6 w-6", plate: false, decorative: false },
  card: { img: "h-10 w-10", icon: "h-5 w-5", tile: "h-10 w-10", plate: true, decorative: false },
  header: { img: "h-14 w-14", icon: "h-7 w-7", tile: "h-14 w-14", plate: true, decorative: false },
  hero: { img: "h-14 w-14 sm:h-16 sm:w-16", icon: "h-8 w-8", tile: "h-14 w-14 sm:h-16 sm:w-16", plate: true, decorative: false },
  watermark: { img: "h-24 w-auto max-w-[280px]", icon: "h-16 w-16", tile: "h-24 w-24", plate: false, decorative: true },
};

/** Dark-mode-only safe plate. Light mode renders transparent → no visual change for existing callers. */
const PLATE_CLASS =
  "rounded-md dark:bg-white/90 dark:p-1 dark:shadow-sm dark:ring-1 dark:ring-black/5";

export interface ServiceLogoProps {
  /** Canonical or alias service slug */
  slug: ServiceBrandingSlug | string;
  /** Context-aware variant (preferred). */
  variant?: ServiceLogoVariant;
  /** @deprecated use `variant`. Kept for backwards compatibility. */
  size?: ServiceLogoVariant;
  showName?: boolean;
  className?: string;
  /** Force the dark-mode safe plate on/off (defaults per variant). */
  plate?: boolean;
  /** Lucide icon name override for fallback */
  fallbackIcon?: string;
}

function joinClasses(...parts: Array<string | undefined | false>): string {
  return parts.filter(Boolean).join(" ");
}

export function ServiceLogo({
  slug,
  variant,
  size,
  showName = false,
  className,
  plate,
  fallbackIcon,
}: ServiceLogoProps) {
  const branding = getServiceBranding(slug);
  const [failed, setFailed] = useState(false);

  const resolved: ServiceLogoVariant = variant ?? size ?? "card";
  const spec = VARIANTS[resolved];
  const iconName = fallbackIcon ?? branding?.fallbackIcon ?? "Boxes";
  const usePlate = plate ?? spec.plate;

  if (!branding || failed) {
    return (
      <span
        className={joinClasses(
          "inline-flex shrink-0 items-center gap-2",
          showName && "min-w-0",
          className,
        )}
        data-testid="service-logo-fallback"
        aria-hidden={spec.decorative || undefined}
      >
        <span
          className={joinClasses(
            "inline-flex items-center justify-center rounded-md bg-neutral-100 dark:bg-neutral-900",
            spec.tile,
          )}
        >
          <ShellIcon name={iconName} className={joinClasses(spec.icon, "text-foreground dark:text-foreground")} />
        </span>
        {showName && branding?.name ? (
          <span className="truncate text-sm font-medium text-foreground dark:text-foreground">{branding.name}</span>
        ) : null}
      </span>
    );
  }

  return (
    <span
      className={joinClasses(
        "inline-flex shrink-0 items-center gap-2",
        showName && "min-w-0",
        spec.decorative && "pointer-events-none opacity-[0.06] dark:opacity-[0.08]",
        className,
      )}
      data-testid={spec.decorative ? "service-logo-watermark" : "service-logo"}
      data-service-slug={branding.slug}
      data-variant={resolved}
      aria-hidden={spec.decorative || undefined}
    >
      <span className={joinClasses("inline-flex items-center justify-center", usePlate && PLATE_CLASS)}>
        <img
          src={branding.logo}
          alt={spec.decorative ? "" : `${branding.name} logo`}
          className={joinClasses("object-contain", spec.img)}
          draggable={false}
          onError={() => setFailed(true)}
        />
      </span>
      {showName && !spec.decorative ? (
        <span className="truncate text-sm font-medium text-foreground dark:text-foreground">{branding.name}</span>
      ) : null}
    </span>
  );
}
