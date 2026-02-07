import React from "react";

export interface StatusIndicatorProps {
  status: "active" | "inactive" | "pending" | "error";
  label?: string;
  className?: string;
}

const statusColors: Record<string, string> = {
  active: "bg-success",
  inactive: "bg-neutral-500",
  pending: "bg-warning",
  error: "bg-danger",
};

export function StatusIndicator({ status, label, className = "" }: StatusIndicatorProps) {
  return (
    <div className={`inline-flex items-center gap-2 ${className}`}>
      <span className={`w-2 h-2 rounded-full ${statusColors[status]}`} />
      {label && <span className="text-sm text-neutral-900">{label}</span>}
    </div>
  );
}
