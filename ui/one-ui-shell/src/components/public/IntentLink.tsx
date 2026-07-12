"use client";

/**
 * Pillar-card link that attaches a fresh gateway intent at CLICK time. Intent tokens
 * must not be baked into server-rendered markup: `createdAt` would freeze at render
 * time and cached pages would serve already-stale tokens (rejected after 60 minutes).
 * Falls back to plain navigation when the intent fails validation.
 */

import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { createIntent, withIntentToken, type GatewayPillar } from "@/lib/gateway-intent";

export function IntentLink({
  pillar,
  goal,
  dest,
  from,
  href,
  className,
  children,
}: {
  pillar: GatewayPillar;
  goal: string;
  dest: string;
  from: string;
  href: string;
  className?: string;
  children: ReactNode;
}) {
  const router = useRouter();
  return (
    <a
      href={href}
      className={className}
      onClick={(e) => {
        e.preventDefault();
        const intent = createIntent(pillar, goal, { dest, from });
        router.push(intent ? withIntentToken(href, intent) : href);
      }}
    >
      {children}
    </a>
  );
}
