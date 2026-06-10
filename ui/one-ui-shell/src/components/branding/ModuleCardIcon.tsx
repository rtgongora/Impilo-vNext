"use client";

import type { ComponentType } from "react";
import { ServiceLogo } from "@/components/branding/ServiceLogo";

interface ModuleCardIconProps {
  serviceSlug?: string;
  icon: ComponentType<{ className?: string }>;
  color: string;
  size?: "sm" | "md";
}

export function ModuleCardIcon({ serviceSlug, icon: Icon, color, size = "md" }: ModuleCardIconProps) {
  if (serviceSlug) {
    return <ServiceLogo slug={serviceSlug} size={size === "sm" ? "compact" : "card"} />;
  }

  const boxClass = size === "sm" ? "w-7 h-7 rounded" : "w-9 h-9 rounded-lg";
  const iconClass = size === "sm" ? "w-3.5 h-3.5" : "w-4.5 h-4.5";

  return (
    <div className={`${boxClass} ${color} flex items-center justify-center shrink-0`}>
      <Icon className={iconClass} />
    </div>
  );
}
