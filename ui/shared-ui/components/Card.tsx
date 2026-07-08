import React from "react";

export interface CardProps {
  children: React.ReactNode;
  className?: string;
  padding?: "sm" | "md" | "lg";
  accent?: "none" | "top" | "left";
  accentColor?: "green" | "yellow" | "red";
  /**
   * Surface treatment. `glass` renders the frosted-glass recipe with an automatic solid fallback
   * (baseline tier / no `backdrop-filter`) so content stays AA-legible. See the Future-Realism guide.
   */
  surface?: "solid" | "glass";
  /** Optional single focal neon glow. `danger` reserved for emergency/SOS. */
  glow?: "none" | "teal" | "gold" | "danger";
}

const glowStyles: Record<string, string> = {
  none: "",
  teal: "shadow-glow-teal",
  gold: "shadow-glow-gold",
  danger: "shadow-glow-danger",
};

const solidSurface = "bg-card rounded-2xl shadow-impilo-card border border-border";
const glassSurface =
  "rounded-2xl border border-glass-border bg-glass-fill shadow-impilo-floating backdrop-blur-glass " +
  "supports-[not(backdrop-filter:blur(0px))]:bg-glass-fallback " +
  "[.low-blur_&]:bg-glass-fallback [.low-blur_&]:backdrop-blur-none";

const paddingStyles: Record<string, string> = {
  sm: "p-3",
  md: "p-4",
  lg: "p-6",
};

const accentTopStyles: Record<string, string> = {
  green: "border-t-4 border-t-primary",
  yellow: "border-t-4 border-t-warning",
  red: "border-t-4 border-t-danger",
};

const accentLeftStyles: Record<string, string> = {
  green: "border-l-4 border-l-primary",
  yellow: "border-l-4 border-l-warning",
  red: "border-l-4 border-l-danger",
};

export function Card({
  children,
  className = "",
  padding = "md",
  accent = "none",
  accentColor = "green",
  surface = "solid",
  glow = "none",
}: CardProps) {
  const accentClass =
    accent === "top"
      ? accentTopStyles[accentColor]
      : accent === "left"
        ? accentLeftStyles[accentColor]
        : "";

  const surfaceClass = surface === "glass" ? glassSurface : solidSurface;

  return (
    <div
      className={`${surfaceClass} ${glowStyles[glow]}
        ${paddingStyles[padding]} ${accentClass} ${className}`}
    >
      {children}
    </div>
  );
}

export function CardHeader({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`pb-3 mb-3 border-b border-border ${className}`}>
      {children}
    </div>
  );
}

export function CardTitle({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <h3 className={`text-base font-semibold text-foreground ${className}`}>
      {children}
    </h3>
  );
}
