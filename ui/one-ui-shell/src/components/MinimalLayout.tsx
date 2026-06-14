"use client";

/**
 * MinimalLayout — Bare layout for fullscreen/minimal views.
 * Layout variant: "minimal"
 */

import { type ReactNode } from "react";

export function MinimalLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-background" role="document" aria-label="Impilo minimal workspace">
      <a href="#minimal-main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-card focus:px-3 focus:py-2">
        Skip to content
      </a>
      <main id="minimal-main">{children}</main>
    </div>
  );
}
