"use client";

import { useState } from "react";
import { Hash, Loader2 } from "lucide-react";
import { KhulumaWorkspace } from "@/components/khuluma/KhulumaWorkspace";
import { KhulumaSection } from "@/components/khuluma/KhulumaSection";
import { KhulumaEmptyState } from "@/components/khuluma/KhulumaEmptyState";
import { useAuthStore } from "@/hooks/useAuthStore";
import { matchesRequiredRole } from "@/lib/auth/role-groups";
import { useDiscoverChannels, useJoinChannel } from "@/hooks/queries/useKhulumaChannels";
import { asRecord, readStr, resolvePersona } from "@/components/khuluma/util";

const SCOPES = ["FACILITY", "PROGRAMME", "COMMUNITY"];

/**
 * Khuluma Teams & Channels — discover and join facility teams, programme channels and
 * communities. Discovery is scope-based (a facility/programme/community reference), so the
 * user picks a scope; results and join actions are real khuluma-service channel operations.
 */
export default function KhulumaChannelsPage() {
  const hasRole = useAuthStore((s) => s.hasRole);
  const isWork = resolvePersona((k) => matchesRequiredRole(hasRole, k)) === "work";
  const [scopeType, setScopeType] = useState("FACILITY");
  const [scopeRef, setScopeRef] = useState("");
  const [applied, setApplied] = useState<{ t: string; r: string } | null>(null);
  const q = useDiscoverChannels(applied?.t, applied?.r);
  const join = useJoinChannel();
  const channels = (q.data ?? []).map(asRecord);

  return (
    <KhulumaWorkspace title="Khuluma — Teams & Channels" subtitle="Facility teams, programme channels and communities">
      <KhulumaSection title="Discover channels" icon={<Hash className="h-4 w-4 text-emerald-700" />}>
        <div className="flex flex-wrap gap-2">
          <select value={scopeType} onChange={(e) => setScopeType(e.target.value)} className="rounded-lg border border-border bg-background px-2 py-2 text-sm">
            {SCOPES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <input className="min-w-[200px] flex-1 rounded-lg border border-border px-3 py-2 text-sm" placeholder={`${scopeType.toLowerCase()} reference (id)`} value={scopeRef} onChange={(e) => setScopeRef(e.target.value)} />
          <button type="button" disabled={!scopeRef} onClick={() => setApplied({ t: scopeType, r: scopeRef })} className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50">Discover</button>
        </div>

        {!applied ? (
          <p className="mt-3 text-sm text-muted-foreground">Choose a scope and reference to find channels you can join.</p>
        ) : q.isLoading ? (
          <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading…</p>
        ) : q.isError ? (
          <p className="mt-3 text-sm text-danger">Could not load channels for that scope.</p>
        ) : !channels.length ? (
          <div className="mt-3">
            <KhulumaEmptyState
              title="No channels in this scope yet"
              explanation="Facility teams, programme channels and communities appear here once created for this scope."
              when="Channels appear as your facility and programmes create them."
              guidance={isWork ? "As a team lead you can create a channel from the operations console." : undefined}
            />
          </div>
        ) : (
          <ul className="mt-3 divide-y divide-border">
            {channels.map((c, i) => {
              const id = readStr(c, "conversationId", "id");
              return (
                <li key={id || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="min-w-0">
                    <span className="block truncate text-foreground">{readStr(c, "title", "name") || "Channel"}</span>
                    <span className="block truncate text-xs text-muted-foreground">{readStr(c, "description") || readStr(c, "scopeType")}</span>
                  </span>
                  {id ? (
                    <button type="button" disabled={join.isPending} onClick={() => join.mutate({ conversationId: id })} className="shrink-0 rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50">Join</button>
                  ) : null}
                </li>
              );
            })}
          </ul>
        )}
      </KhulumaSection>
    </KhulumaWorkspace>
  );
}
