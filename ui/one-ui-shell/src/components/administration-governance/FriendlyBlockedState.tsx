"use client";

import Link from "next/link";
import { ShieldAlert } from "lucide-react";
import { resolveFriendlyStateMessage } from "@/lib/administration-governance";

interface FriendlyBlockedStateProps {
  state?: string;
  backHref?: string;
  backLabel?: string;
}

export function FriendlyBlockedState({ state, backHref = "/work/administration-governance", backLabel = "Administration & Governance" }: FriendlyBlockedStateProps) {
  const msg = resolveFriendlyStateMessage(state);

  return (
    <div className="rounded-2xl border border-warning/35 bg-warning-soft/90 p-6">
      <div className="flex items-start gap-3">
        <ShieldAlert className="mt-0.5 h-6 w-6 shrink-0 text-warning-foreground" aria-hidden />
        <div className="space-y-3">
          <div>
            <h3 className="text-base font-semibold text-warning-foreground">{msg.title}</h3>
            <p className="mt-1 text-sm text-warning-foreground/90">{msg.description}</p>
          </div>
          <ul className="flex flex-wrap gap-2">
            {msg.actions.map((action) => (
              <li key={action}>
                <span className="inline-flex rounded-full border border-amber-300 bg-card px-3 py-1 text-xs font-medium text-warning-foreground">
                  {action}
                </span>
              </li>
            ))}
          </ul>
          <Link href={backHref} className="inline-block text-sm font-medium text-warning-foreground underline-offset-2 hover:underline">
            Back to {backLabel}
          </Link>
        </div>
      </div>
    </div>
  );
}
