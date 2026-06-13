"use client";

import { useState } from "react";
import { ShellIcon } from "@/components/shell/ShellIcon";
import {
  getServiceBranding,
  type ServiceBrandingSlug,
} from "@/config/serviceBranding";

export type ServiceLogoSize = "compact" | "card" | "header";

const SIZE_CLASSES: Record<ServiceLogoSize, string> = {
  compact: "h-6 w-6",
  card: "h-10 w-10",
  header: "h-14 w-14",
};

const ICON_SIZE_CLASSES: Record<ServiceLogoSize, string> = {
  compact: "h-4 w-4",
  card: "h-5 w-5",
  header: "h-7 w-7",
};

export interface ServiceLogoProps {
  /** Canonical or alias service slug */
  slug: ServiceBrandingSlug | string;
  size?: ServiceLogoSize;
  showName?: boolean;
  className?: string;
  /** Lucide icon name override for fallback */
  fallbackIcon?: string;
}

function joinClasses(...parts: Array<string | undefined | false>): string {
  return parts.filter(Boolean).join(" ");
}

export function ServiceLogo({
  slug,
  size = "card",
  showName = false,
  className,
  fallbackIcon,
}: ServiceLogoProps) {
  const branding = getServiceBranding(slug);
  const [failed, setFailed] = useState(false);

  const iconName = fallbackIcon ?? branding?.fallbackIcon ?? "Boxes";
  const sizeClass = SIZE_CLASSES[size];
  const iconClass = ICON_SIZE_CLASSES[size];

  if (!branding || failed) {
    return (
      <span
        className={joinClasses(
          "inline-flex shrink-0 items-center gap-2",
          showName && "min-w-0",
          className,
        )}
        data-testid="service-logo-fallback"
      >
        <span
          className={joinClasses(
            "inline-flex items-center justify-center rounded-md bg-neutral-100 dark:bg-neutral-900",
            sizeClass,
          )}
        >
          <ShellIcon name={iconName} className={joinClasses(iconClass, "text-foreground dark:text-foreground")} />
        </span>
        {showName && branding?.name ? (
          <span className="truncate text-sm font-medium text-foreground dark:text-foreground">{branding.name}</span>
        ) : null}
      </span>
    );
  }

  return (
    <span
      className={joinClasses("inline-flex shrink-0 items-center gap-2", showName && "min-w-0", className)}
      data-testid="service-logo"
      data-service-slug={branding.slug}
    >
      <img
        src={branding.logo}
        alt={`${branding.name} logo`}
        className={joinClasses("object-contain", sizeClass)}
        onError={() => setFailed(true)}
      />
      {showName ? (
        <span className="truncate text-sm font-medium text-foreground dark:text-foreground">{branding.name}</span>
      ) : null}
    </span>
  );
}
