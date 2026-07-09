"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface Escalation {
  id: string;
  priority?: string;
  status?: string;
  tier?: number;
  assignedTo?: string;
  resolutionBreached?: boolean;
  firstResponseBreached?: boolean;
}
interface Adapter { channel: string; status: string; provider?: string }
interface OnCall { actorId: string; actorType?: string; dutyStatus?: string }
interface ChannelStatus { channel: string; activeProvider: string; productionReady: boolean; note?: string }

function useList<T>(key: string, path: string) {
  return useQuery<{ data: T[] }>({
    queryKey: [key],
    queryFn: () => apiClient.get(path),
    refetchInterval: 30000,
  });
}

export default function CommsOpsPage() {
  const escalations = useList<Escalation>("comms-escalations", "/internal/v1/khuluma/escalations");
  const adapters = useList<Adapter>("comms-adapters", "/internal/v1/khuluma/delivery/adapters");
  const onCall = useList<OnCall>("comms-oncall", "/internal/v1/khuluma/on-call");
  const channelStatus = useQuery<{ data: { channels?: ChannelStatus[] } }>({
    queryKey: ["notification-channel-status"],
    queryFn: () => apiClient.get("/internal/v1/notifications/channels/status"),
    refetchInterval: 60000,
  });
  const channels = channelStatus.data?.data?.channels ?? [];

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-lg font-semibold text-foreground">Comms operations</h1>
        <p className="text-xs text-muted-foreground">Escalation queue, channel adapters and the on-call roster.</p>
      </header>

      <section className="rounded-lg border border-border bg-card p-4">
        <h2 className="text-sm font-medium text-foreground mb-3">Escalation queue</h2>
        {escalations.isLoading && <p className="text-xs text-muted-foreground">Loading…</p>}
        {escalations.data && escalations.data.data.length === 0 && (
          <p className="text-xs text-muted-foreground">No open escalations.</p>
        )}
        <ul className="space-y-2">
          {escalations.data?.data.map((e) => (
            <li key={e.id} className="flex items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-xs">
              <span className="font-medium text-foreground">{e.priority}</span>
              <span className="text-muted-foreground">{e.status} · tier {e.tier ?? 1}</span>
              <span className="text-muted-foreground">{e.assignedTo ?? "unassigned"}</span>
              {(e.resolutionBreached || e.firstResponseBreached) && (
                <span className="rounded-full bg-destructive/10 px-2 py-0.5 font-medium text-destructive">SLA breached</span>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded-lg border border-border bg-card p-4">
        <h2 className="text-sm font-medium text-foreground mb-3">Channel adapters</h2>
        <ul className="grid gap-2 sm:grid-cols-2">
          {adapters.data?.data.map((a) => (
            <li key={a.channel} className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-xs">
              <span className="font-medium text-foreground">{a.channel}</span>
              <span
                className={
                  a.status === "CONFIGURED"
                    ? "rounded-full bg-emerald-500/10 px-2 py-0.5 font-medium text-emerald-600"
                    : "rounded-full bg-muted px-2 py-0.5 font-medium text-muted-foreground"
                }
              >
                {a.status === "CONFIGURED" ? (a.provider ?? "configured") : "not configured"}
              </span>
            </li>
          ))}
          {adapters.data && adapters.data.data.length === 0 && (
            <li className="text-xs text-muted-foreground">No external adapters configured — only native in-app delivery is active.</li>
          )}
        </ul>
      </section>

      <section className="rounded-lg border border-border bg-card p-4">
        <h2 className="text-sm font-medium text-foreground mb-3">Notification delivery channels</h2>
        <p className="mb-3 text-xs text-muted-foreground">
          Live posture from notification-service. A stub/log channel records messages but does NOT deliver them
          externally — provide SMTP/SMS credentials via the secrets bootstrap to go live.
        </p>
        {channelStatus.isLoading && <p className="text-xs text-muted-foreground">Loading…</p>}
        <ul className="grid gap-2 sm:grid-cols-3">
          {channels.map((c) => (
            <li key={c.channel} className="rounded-md border border-border px-3 py-2 text-xs" title={c.note}>
              <div className="flex items-center justify-between">
                <span className="font-medium text-foreground">{c.channel}</span>
                <span
                  className={
                    c.productionReady
                      ? "rounded-full bg-emerald-500/10 px-2 py-0.5 font-medium text-emerald-600"
                      : "rounded-full bg-amber-500/10 px-2 py-0.5 font-medium text-amber-600"
                  }
                >
                  {c.productionReady ? "delivering" : "stub — not delivered"}
                </span>
              </div>
              <p className="mt-1 text-muted-foreground">{c.activeProvider}</p>
            </li>
          ))}
          {!channelStatus.isLoading && channels.length === 0 && (
            <li className="text-xs text-muted-foreground">Channel status unavailable from notification-service.</li>
          )}
        </ul>
      </section>

      <section className="rounded-lg border border-border bg-card p-4">
        <h2 className="text-sm font-medium text-foreground mb-3">On-call roster</h2>
        {onCall.data && onCall.data.data.length === 0 && (
          <p className="text-xs text-muted-foreground">No one is on call.</p>
        )}
        <ul className="space-y-1">
          {onCall.data?.data.map((o) => (
            <li key={o.actorId} className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-xs">
              <span className="font-medium text-foreground">{o.actorId}</span>
              <span className="text-muted-foreground">{o.actorType} · {o.dutyStatus}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
