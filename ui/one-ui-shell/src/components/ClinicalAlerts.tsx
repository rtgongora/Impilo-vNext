"use client";

/**
 * Clinical Alerts Banner — Displays rule-based CDS alerts
 * in the EHR encounter context.
 */

import { AlertTriangle, AlertCircle, Info, X } from "lucide-react";
import { useState } from "react";
import { type ClinicalAlert, type AlertSeverity } from "@/hooks/useClinicalAlerts";

const SEVERITY_STYLES: Record<AlertSeverity, { bg: string; border: string; icon: typeof AlertTriangle; iconColor: string }> = {
  critical: { bg: "bg-danger-soft", border: "border-danger/28", icon: AlertTriangle, iconColor: "text-red-600" },
  warning: { bg: "bg-warning-soft", border: "border-warning/35", icon: AlertCircle, iconColor: "text-amber-600" },
  info: { bg: "bg-primary-soft", border: "border-primary/25", icon: Info, iconColor: "text-primary" },
};

export function ClinicalAlerts({ alerts }: { alerts: ClinicalAlert[] }) {
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  if (alerts.length === 0) return null;

  const visible = alerts.filter((a) => !dismissed.has(a.id));
  const critical = visible.filter((a) => a.severity === "critical");
  const warnings = visible.filter((a) => a.severity === "warning");
  const infos = visible.filter((a) => a.severity === "info");
  const sorted = [...critical, ...warnings, ...infos];

  if (sorted.length === 0) return null;

  return (
    <div className="space-y-1.5 mb-3">
      {sorted.slice(0, 5).map((alert) => {
        const style = SEVERITY_STYLES[alert.severity];
        const Icon = style.icon;
        return (
          <div key={alert.id} className={`flex items-start gap-2 px-3 py-2 rounded-lg border ${style.bg} ${style.border}`}>
            <Icon className={`w-4 h-4 shrink-0 mt-0.5 ${style.iconColor}`} />
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-foreground">{alert.title}</p>
              <p className="text-xs text-muted-foreground">{alert.message}</p>
            </div>
            <button onClick={() => setDismissed((prev) => new Set(prev).add(alert.id))}
              className="shrink-0 p-0.5 text-muted-foreground hover:text-muted-foreground">
              <X className="w-3 h-3" />
            </button>
          </div>
        );
      })}
      {sorted.length > 5 && (
        <p className="text-xs text-muted-foreground px-3">+{sorted.length - 5} more alerts</p>
      )}
    </div>
  );
}
