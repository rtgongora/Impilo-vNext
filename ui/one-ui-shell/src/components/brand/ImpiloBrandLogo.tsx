"use client";

/**
 * ImpiloBrandLogo — Canonical SVG-backed brand mark and wordmark.
 */

import Image from "next/image";

interface ImpiloBrandLogoProps {
  variant?: "mark" | "full";
  tone?: "brand" | "white" | "black";
  size?: number;
  className?: string;
}

const FULL_RATIO = 595.28 / 301.79;

const assetMap = {
  full: {
    brand: "/brand/logo-rgb.svg",
    white: "/brand/logo-white.svg",
    black: "/brand/logo-black.svg",
  },
  mark: {
    brand: "/brand/mark-rgb.svg",
    white: "/brand/mark-white.svg",
    black: "/brand/mark-black.svg",
  },
} as const;

export function ImpiloBrandLogo({
  variant = "full",
  tone = "brand",
  size = 32,
  className,
}: ImpiloBrandLogoProps) {
  const src = assetMap[variant][tone];
  const width = variant === "mark" ? size : Math.round(size * FULL_RATIO);

  return (
    <span className={`inline-flex items-center ${className ?? ""}`}>
      <Image
        src={src}
        alt="Impilo"
        width={width}
        height={size}
        className="block h-auto"
        draggable={false}
        unoptimized
      />
    </span>
  );
}
