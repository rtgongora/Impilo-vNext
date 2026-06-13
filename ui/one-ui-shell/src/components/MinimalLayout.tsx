"use client";

/**
 * MinimalLayout — Bare layout for fullscreen/minimal views.
 * Layout variant: "minimal"
 */

import { type ReactNode } from "react";

export function MinimalLayout({ children }: { children: ReactNode }) {
  return <div className="min-h-screen bg-background">{children}</div>;
}
