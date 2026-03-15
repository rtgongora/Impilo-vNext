"use client";

export function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    OPEN: "bg-info/10 text-info",
    IN_PROGRESS: "bg-warning/10 text-warning",
    WAITING: "bg-neutral-100 text-neutral-600",
    RESOLVED: "bg-success/10 text-success",
    CLOSED: "bg-neutral-100 text-neutral-500",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[status] ?? styles.OPEN}`}>
      {status.replace("_", " ")}
    </span>
  );
}

export function PriorityBadge({ priority }: { priority: string }) {
  const styles: Record<string, string> = {
    LOW: "bg-neutral-100 text-neutral-600",
    MEDIUM: "bg-info/10 text-info",
    HIGH: "bg-warning/10 text-warning",
    CRITICAL: "bg-danger/10 text-danger",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${styles[priority] ?? styles.MEDIUM}`}>
      {priority}
    </span>
  );
}

export function EscalationBadge({ level }: { level: number }) {
  if (level === 0) return null;
  const color = level >= 3 ? "bg-danger/10 text-danger" : level >= 2 ? "bg-warning/10 text-warning" : "bg-info/10 text-info";
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${color}`}>
      L{level}
    </span>
  );
}
