import React from "react";

/**
 * Adaptive dashboard grid — content-sized tiles that fill the available width
 * with as many columns as fit (`auto-fit` + minmax), instead of a fixed grid
 * of oversized cards. Use `min="sm"` for dense operational comparison views.
 */
export interface AdaptiveGridProps {
  children: React.ReactNode;
  className?: string;
  /** Minimum tile width: sm=13rem (dense), md=16rem, lg=20rem (chart-bearing). */
  min?: "sm" | "md" | "lg";
  gap?: "default" | "compact";
}

const minStyles: Record<string, string> = {
  sm: "[grid-template-columns:repeat(auto-fit,minmax(13rem,1fr))]",
  md: "[grid-template-columns:repeat(auto-fit,minmax(16rem,1fr))]",
  lg: "[grid-template-columns:repeat(auto-fit,minmax(20rem,1fr))]",
};

export function AdaptiveGrid({ children, className = "", min = "md", gap = "default" }: AdaptiveGridProps) {
  return (
    <div className={`grid ${gap === "compact" ? "gap-2" : "gap-3"} ${minStyles[min]} ${className}`}>
      {children}
    </div>
  );
}
