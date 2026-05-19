"use client";

import type { ReactNode } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";

export function CitizenSelfServiceGate({ children }: { children: ReactNode }) {
  const user = useAuthStore((s) => s.user);

  if (!user) {
    return <p className="text-sm text-[color:var(--text-secondary)]">Sign in to use citizen self-service flows.</p>;
  }

  if (user.actorType !== "CITIZEN") {
    return (
      <div className="rounded-3xl border border-[color:var(--warning)]/30 bg-[color:var(--warning-soft)] p-4 text-sm text-[color:var(--text-primary)]">
        These pages expect an authenticated <strong>citizen</strong> session (<code className="text-xs">actorType CITIZEN</code>
        ). You are signed in as <strong>{user.actorType}</strong>. Use a citizen account or the appropriate professional tools.
      </div>
    );
  }

  return <>{children}</>;
}
