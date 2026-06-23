"use client";

import { useEffect, useState } from "react";

function formatClock(now: Date) {
  return now.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}

function formatDate(now: Date) {
  return now.toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" });
}

/** Live time + date for the top account strip (and reusable on EHR TopBar). */
export function ShellSystemClock() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 30_000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <div
      className="hidden min-w-[7.5rem] flex-col items-end leading-tight text-[color:var(--text-muted)] sm:flex"
      data-testid="shell-system-clock"
      title={now.toLocaleString()}
    >
      <span className="text-xs font-semibold tabular-nums text-[color:var(--text-primary)]">{formatClock(now)}</span>
      <span className="text-[10px]">{formatDate(now)}</span>
    </div>
  );
}
