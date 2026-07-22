"use client";

import { useSearchParams } from "next/navigation";
import { KhulumaWorkspace } from "@/components/khuluma/KhulumaWorkspace";
import { CommsHub } from "@/components/comms/CommsHub";
import { useAuthStore } from "@/hooks/useAuthStore";
import { matchesRequiredRole } from "@/lib/auth/role-groups";
import { resolvePersona } from "@/components/khuluma/util";

/**
 * Khuluma Inbox — the messaging workspace. Embeds the existing, deployed CommsHub (one chat
 * surface, zero forked code); ?compose=1 opens the new-conversation composer directly.
 */
export default function KhulumaInboxPage() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const persona = resolvePersona((k) => matchesRequiredRole(hasRole, k));
  const params = useSearchParams();
  const compose = params?.get("compose") === "1";
  return (
    <KhulumaWorkspace title="Khuluma — Inbox" subtitle="Your conversations, direct and group">
      <CommsHub persona={persona} defaultCompose={compose} />
    </KhulumaWorkspace>
  );
}
