"use client";

import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { ShellIcon } from "@/components/shell/ShellIcon";
import { resolveServiceSlug } from "@/config/serviceBranding";

interface ShellAppIconProps {
  icon: string;
  serviceSlug?: string | null;
  name?: string;
  itemCode?: string | null;
  size?: "compact" | "card";
  className?: string;
}

/**
 * Renders a sovereign service logo when a slug can be resolved, otherwise the Lucide icon.
 */
export function ShellAppIcon({
  icon,
  serviceSlug,
  name,
  itemCode,
  size = "compact",
  className,
}: ShellAppIconProps) {
  const slug =
    resolveServiceSlug(serviceSlug ?? undefined) ??
    resolveServiceSlug(itemCode ?? undefined) ??
    resolveServiceSlug(name ?? undefined);

  if (slug) {
    // Full-colour service logos keep a light plate in dark mode so dark-ink
    // wordmarks stay readable (no inversion — see ServiceLogo dark plate).
    return (
      <span
        className={`flex shrink-0 items-center justify-center rounded-md bg-card p-0.5 dark:bg-white/90 ${
          size === "card" ? "h-9 w-9" : "h-6 w-6"
        } ${className ?? ""}`}
      >
        <ServiceLogo slug={slug} variant={size} plate={false} fallbackIcon={icon} />
      </span>
    );
  }

  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-md bg-neutral-100 dark:bg-neutral-900 ${
        size === "card" ? "h-9 w-9" : "h-6 w-6"
      } ${className ?? ""}`}
    >
      <ShellIcon
        name={icon}
        className={`text-foreground dark:text-foreground ${size === "card" ? "h-5 w-5" : "h-4 w-4"}`}
      />
    </span>
  );
}
