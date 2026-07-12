import React from "react";

/**
 * Responsive split view — primary workspace plus a supporting side panel.
 * Side panel sits beside the work on wide viewports and stacks below it on
 * narrow ones (never squeezing the active task). The primary pane always
 * gets the remaining width (`minmax(0,1fr)` guards against overflow).
 */
export interface SplitViewProps {
  /** Primary workspace content. */
  children: React.ReactNode;
  /** Supporting panel — context, summary, guidance. */
  side: React.ReactNode;
  /** Side panel width on wide viewports. */
  sideWidth?: "sm" | "md" | "lg";
  sidePosition?: "left" | "right";
  /** Breakpoint at which the panes sit side by side. */
  splitAt?: "md" | "lg" | "xl";
  gap?: "default" | "compact";
  className?: string;
}

/**
 * Literal class strings so Tailwind can see them (no dynamic interpolation).
 * sideWidth: sm=280px, md=320px, lg=400px.
 */
const layoutStyles: Record<string, Record<string, { right: string; left: string }>> = {
  md: {
    sm: { right: "md:grid-cols-[minmax(0,1fr)_280px]", left: "md:grid-cols-[280px_minmax(0,1fr)]" },
    md: { right: "md:grid-cols-[minmax(0,1fr)_320px]", left: "md:grid-cols-[320px_minmax(0,1fr)]" },
    lg: { right: "md:grid-cols-[minmax(0,1fr)_400px]", left: "md:grid-cols-[400px_minmax(0,1fr)]" },
  },
  lg: {
    sm: { right: "lg:grid-cols-[minmax(0,1fr)_280px]", left: "lg:grid-cols-[280px_minmax(0,1fr)]" },
    md: { right: "lg:grid-cols-[minmax(0,1fr)_320px]", left: "lg:grid-cols-[320px_minmax(0,1fr)]" },
    lg: { right: "lg:grid-cols-[minmax(0,1fr)_400px]", left: "lg:grid-cols-[400px_minmax(0,1fr)]" },
  },
  xl: {
    sm: { right: "xl:grid-cols-[minmax(0,1fr)_280px]", left: "xl:grid-cols-[280px_minmax(0,1fr)]" },
    md: { right: "xl:grid-cols-[minmax(0,1fr)_320px]", left: "xl:grid-cols-[320px_minmax(0,1fr)]" },
    lg: { right: "xl:grid-cols-[minmax(0,1fr)_400px]", left: "xl:grid-cols-[400px_minmax(0,1fr)]" },
  },
};

const orderStyles: Record<string, Record<string, { main: string; side: string }>> = {
  // Stacked (narrow): side panel renders after the primary work regardless of position.
  md: { left: { main: "md:order-2", side: "md:order-1" }, right: { main: "", side: "" } },
  lg: { left: { main: "lg:order-2", side: "lg:order-1" }, right: { main: "", side: "" } },
  xl: { left: { main: "xl:order-2", side: "xl:order-1" }, right: { main: "", side: "" } },
};

export function SplitView({
  children,
  side,
  sideWidth = "md",
  sidePosition = "right",
  splitAt = "lg",
  gap = "default",
  className = "",
}: SplitViewProps) {
  const cols = layoutStyles[splitAt][sideWidth][sidePosition];
  const order = orderStyles[splitAt][sidePosition];
  return (
    <div className={`grid grid-cols-1 ${gap === "compact" ? "gap-3" : "gap-4"} ${cols} ${className}`}>
      <div className={`min-w-0 ${order.main}`}>{children}</div>
      <div className={`min-w-0 ${order.side}`}>{side}</div>
    </div>
  );
}
