"use client";

import type { RunningTask } from "@/lib/shell/types";

interface ShellTaskPreviewProps {
  task: RunningTask;
  x: number;
  y: number;
}

function formatWhen(iso: string) {
  try {
    return new Date(iso).toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

/** Hover preview card for open dock tasks. */
export function ShellTaskPreview({ task, x, y }: ShellTaskPreviewProps) {
  return (
    <div
      className="pointer-events-none fixed z-[10001] w-[min(18rem,calc(100vw-2rem))] rounded-xl border border-border bg-card p-3 shadow-lg"
      style={{ left: Math.max(8, x - 40), top: y - 88 }}
      data-testid="shell-task-preview"
    >
      <p className="text-sm font-semibold text-foreground">{task.title}</p>
      <p className="mt-1 truncate text-xs text-muted-foreground">{task.route}</p>
      <dl className="mt-2 grid grid-cols-2 gap-x-2 gap-y-1 text-[10px] text-muted-foreground">
        <dt>Module</dt>
        <dd className="truncate text-foreground">{task.appId}</dd>
        <dt>Status</dt>
        <dd className="capitalize text-foreground">{task.status}</dd>
        <dt>Opened</dt>
        <dd>{formatWhen(task.openedAt)}</dd>
        <dt>Active</dt>
        <dd>{formatWhen(task.lastActiveAt)}</dd>
      </dl>
      {task.contextRef ? (
        <p className="mt-2 truncate text-[10px] text-muted-foreground">Context: {task.contextRef}</p>
      ) : null}
    </div>
  );
}
