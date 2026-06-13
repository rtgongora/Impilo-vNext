import React from "react";
import { WorkspaceHero } from "./WorkspaceHero";

export interface BrandedPageShellProps {
  title?: string;
  subtitle?: string;
  hero?: React.ReactNode;
  heroActions?: React.ReactNode;
  watermark?: boolean;
  children: React.ReactNode;
  className?: string;
  contentClassName?: string;
}

export function BrandedPageShell({
  title,
  subtitle,
  hero,
  heroActions,
  watermark = false,
  children,
  className = "",
  contentClassName = "",
}: BrandedPageShellProps) {
  return (
    <div className={`min-h-full bg-background ${className}`}>
      <div className={`mx-auto w-full max-w-7xl space-y-6 p-4 md:p-6 ${contentClassName}`}>
        {hero ?? (title ? (
          <WorkspaceHero
            title={title}
            subtitle={subtitle}
            actions={heroActions}
            watermark={watermark}
          />
        ) : null)}
        {children}
      </div>
    </div>
  );
}
