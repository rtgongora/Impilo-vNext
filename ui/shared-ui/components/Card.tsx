import React from "react";

export interface CardProps {
  children: React.ReactNode;
  className?: string;
  padding?: "sm" | "md" | "lg";
}

const paddingStyles: Record<string, string> = {
  sm: "p-3",
  md: "p-4",
  lg: "p-6",
};

export function Card({ children, className = "", padding = "md" }: CardProps) {
  return (
    <div
      className={`bg-white rounded-[12px] shadow-subtle border border-neutral-100
        ${paddingStyles[padding]} ${className}`}
    >
      {children}
    </div>
  );
}

export function CardHeader({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`pb-3 mb-3 border-b border-neutral-100 ${className}`}>
      {children}
    </div>
  );
}

export function CardTitle({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return (
    <h3 className={`text-base font-semibold text-neutral-900 ${className}`}>
      {children}
    </h3>
  );
}
