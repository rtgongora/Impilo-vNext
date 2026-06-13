"use client";

import { AlertTriangle, CheckCircle2, Loader2, Radio } from "lucide-react";
import { useTelemedicineRtcHealth } from "@/hooks/queries/useTelemedicine";

export function TelemedicineRtcHealthPanel() {
  const { data, isLoading, isError } = useTelemedicineRtcHealth();
  const health = data?.data;

  if (isLoading) {
    return (
      <section
        className="rounded-xl border border-border bg-card px-4 py-3 text-sm text-muted-foreground"
        data-testid="telemedicine-rtc-health-panel"
      >
        <div className="flex items-center gap-2">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading RTC gateway health…
        </div>
      </section>
    );
  }

  if (isError || !health) {
    return (
      <section
        className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground"
        data-testid="telemedicine-rtc-health-panel"
      >
        <div className="flex items-start gap-2">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            RTC gateway health is unavailable. Governed teleconsult notes and referral workflows remain live;
            real-time audio/video stays preview-limited until rtc-gateway reports healthy.
          </p>
        </div>
      </section>
    );
  }

  const productionReady = Boolean(health.productionReady);

  return (
    <section
      className={`rounded-xl border px-4 py-3 text-sm ${
        productionReady
          ? "border-success/25 bg-success-soft/70 text-emerald-950"
          : "border-warning/35 bg-warning-soft/70 text-warning-foreground"
      }`}
      data-testid="telemedicine-rtc-health-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          {productionReady ? (
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          ) : (
            <Radio className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
          )}
          <div>
            <p className="font-medium">
              RTC media · {productionReady ? "production-ready" : "preview boundary"}
            </p>
            <p className="mt-1 text-xs opacity-90">
              Provider <strong>{health.provider}</strong>
              {health.serverUrl ? (
                <>
                  {" "}
                  · server <code className="rounded bg-card/60 px-1">{health.serverUrl}</code>
                </>
              ) : null}
            </p>
            {!productionReady ? (
              <p className="mt-2 text-xs">
                Dev preview: governed session lifecycle and chat are live via Experience BFF. LiveKit rooms and
                participant tokens require a configured rtc-gateway (dev mode off, LiveKit enabled + secrets set).
                {health.devModeEnabled ? " Dev mode is currently enabled." : ""}
              </p>
            ) : (
              <p className="mt-2 text-xs">
                LiveKit is configured. Governed media tokens can be issued for consented teleconsult sessions.
              </p>
            )}
          </div>
        </div>
        <div className="flex flex-wrap gap-2 text-[10px] font-medium uppercase tracking-wide">
          <span className="rounded-full bg-card/70 px-2 py-0.5">
            livekit {health.livekitEnabled ? "on" : "off"}
          </span>
          <span className="rounded-full bg-card/70 px-2 py-0.5">
            configured {health.livekitConfigured ? "yes" : "no"}
          </span>
          {typeof health.activeSessions === "number" ? (
            <span className="rounded-full bg-card/70 px-2 py-0.5">
              sessions {health.activeSessions}
            </span>
          ) : null}
        </div>
      </div>
    </section>
  );
}
