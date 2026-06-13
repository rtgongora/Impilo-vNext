import React from "react";

export interface CardProps {
  children: React.ReactNode;
  className?: string;
  padding?: "sm" | "md" | "lg";
  accent?: "none" | "top" | "left";
  accentColor?: "green" | "yellow" | "red";
}

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
}: CardProps) {
  const accentClass =
    accent === "top"
      ? accentTopStyles[accentColor]
      : accent === "left"
        ? accentLeftStyles[accentColor]
        : "";

  return (
    <div
      className={`bg-card rounded-2xl shadow-impilo-card border border-border
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
