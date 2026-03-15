/**
 * SkeletonLoader — Placeholder skeleton for loading states.
 */

import React from "react";

export interface SkeletonLoaderProps {
  width?: string | number;
  height?: string | number;
  borderRadius?: number;
  variant?: "text" | "rectangular" | "circular";
  lines?: number;
  testID?: string;
}

export function SkeletonLoader({
  width = "100%",
  height = 16,
  borderRadius = 4,
  variant = "text",
  lines = 1,
  testID,
}: SkeletonLoaderProps) {
  const br = variant === "circular" ? "50%" : borderRadius;
  const h = variant === "circular" ? width : height;

  const skeletonStyle = {
    width,
    height: h,
    borderRadius: br,
    backgroundColor: "#E0E0E0",
    animation: "pulse 1.5s ease-in-out infinite",
  };

  if (lines === 1) {
    return React.createElement("div", {
      "data-testid": testID,
      "aria-busy": true,
      "aria-label": "Loading",
      style: skeletonStyle,
    });
  }

  return React.createElement(
    "div",
    { "data-testid": testID, "aria-busy": true, "aria-label": "Loading" },
    ...Array.from({ length: lines }, (_, i) =>
      React.createElement("div", {
        key: i,
        style: {
          ...skeletonStyle,
          width: i === lines - 1 ? "60%" : width,
          marginBottom: i < lines - 1 ? 8 : 0,
        },
      })
    )
  );
}
