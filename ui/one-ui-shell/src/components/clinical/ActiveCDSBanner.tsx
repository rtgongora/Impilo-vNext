"use client";

/**
 * ActiveCDSBanner — Clinical Decision Support alerts strip.
 *
 * Renders real decision-support alerts (severity critical / high / moderate / info)
 * supplied by the caller via the `alerts` prop. Alerts are dismissable and collapsible;
 * when collapsed they auto-rotate every 8 s.
 *
 * Product Truth: this component renders ONLY alerts it is given. It does NOT fabricate
 * patient findings or "AI" insights. (It previously generated hardcoded sepsis/renal/
 * hyperkalaemia alerts and a setTimeout-faked "AI Diagnostic Engine (Live)" insight with
 * invented vitals — all removed.) Real, governed clinical decision support lives in
 * AIDiagnosticAssistant, which is backed by the clinical-knowledge-platform-service.
 * Until a real per-patient alert feed is wired into the clinical toolbar, this strip
 * renders nothing rather than inventing alerts.
 */

import { useState, useEffect } from "react";
import {
  AlertTriangle,
  ChevronRight,
  ChevronDown,
  X,
  Lightbulb,
  Pill,
  Activity,
  CheckCircle2,
  Clock,
  Sparkles,
} from "lucide-react";
import { cn } from "@/lib/accessibility";

// ── Types ──────────────────────────────────────────

export interface CDSGuidanceItem {
  id: string;
  type: "alert" | "recommendation" | "reminder" | "ai-insight";
  severity: "critical" | "high" | "moderate" | "info";
  title: string;
  message: string;
  source: string;
  timestamp: Date;
  dismissed: boolean;
  actionLabel?: string;
}

// ── Severity visual config ─────────────────────────

const severityConfig = {
  critical: {
    bg: "bg-danger-soft",
    border: "border-danger/28",
    text: "text-danger",
    icon: AlertTriangle,
    pulse: true,
  },
  high: {
    bg: "bg-orange-50",
    border: "border-orange-200",
    text: "text-orange-700",
    icon: Pill,
    pulse: false,
  },
  moderate: {
    bg: "bg-warning-soft",
    border: "border-warning/35",
    text: "text-warning-foreground",
    icon: Sparkles,
    pulse: false,
  },
  info: {
    bg: "bg-primary-soft",
    border: "border-primary/25",
    text: "text-primary",
    icon: Lightbulb,
    pulse: false,
  },
};

const typeIcons = {
  alert: AlertTriangle,
  recommendation: Activity,
  reminder: Clock,
  "ai-insight": Sparkles,
};

// ── Props ──────────────────────────────────────────

interface ActiveCDSBannerProps {
  /** Whether a patient chart is currently active */
  hasActivePatient?: boolean;
  /**
   * Real decision-support alerts for the active patient, supplied by the caller from a
   * governed CDS feed. Defaults to none — this component never fabricates alerts.
   */
  alerts?: CDSGuidanceItem[];
}

// ── Component ──────────────────────────────────────

export function ActiveCDSBanner({ hasActivePatient = true, alerts = [] }: ActiveCDSBannerProps) {
  const [guidance, setGuidance] = useState<CDSGuidanceItem[]>(alerts);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [expanded, setExpanded] = useState(false);

  const activeItems = guidance.filter((g) => !g.dismissed);

  // Render only the real alerts supplied by the caller. No fabrication, no setTimeout-faked
  // "AI" insight. When no real feed is wired, `alerts` is empty and the strip renders nothing.
  useEffect(() => {
    setGuidance(alerts);
    setCurrentIndex(0);
  }, [alerts]);

  // Auto-rotate through non-dismissed items
  useEffect(() => {
    if (!expanded && activeItems.length > 1) {
      const timer = setInterval(() => {
        setCurrentIndex((prev) => (prev + 1) % activeItems.length);
      }, 8_000);
      return () => clearInterval(timer);
    }
  }, [expanded, activeItems.length]);

  const dismissItem = (id: string) => {
    setGuidance((prev) =>
      prev.map((g) => (g.id === id ? { ...g, dismissed: true } : g)),
    );
  };

  const dismissAll = () => {
    setGuidance((prev) => prev.map((g) => ({ ...g, dismissed: true })));
  };

  if (!hasActivePatient || activeItems.length === 0) return null;

  const current = activeItems[currentIndex % activeItems.length];
  if (!current) return null;

  const config = severityConfig[current.severity];
  const TypeIcon = typeIcons[current.type];

  return (
    <div className={cn("border-t", config.bg)}>
      {/* Compact banner -- single line with rotation */}
      <div
        className="flex items-center gap-2 px-3 py-1.5 cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        <div className={cn("flex items-center gap-1.5 shrink-0", config.text)}>
          {config.pulse ? (
            <span className="relative flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-500 opacity-75" />
              <span className="relative inline-flex rounded-full h-3 w-3 bg-red-600" />
            </span>
          ) : (
            <TypeIcon className="h-3.5 w-3.5" />
          )}
        </div>

        <span
          className={cn(
            "text-[9px] px-1.5 py-0 rounded shrink-0 font-medium",
            config.bg,
            config.text,
            config.border,
            "border",
          )}
        >
          {current.type === "ai-insight"
            ? "AI"
            : current.severity.toUpperCase()}
        </span>

        <div className="flex-1 min-w-0 flex items-center gap-2">
          <span className="text-xs font-semibold shrink-0">{current.title}</span>
          <span className="text-xs text-muted-foreground truncate">{current.message}</span>
        </div>

        {current.actionLabel && (
          <button
            className={cn(
              "h-6 text-[10px] px-2 shrink-0 rounded hover:bg-black/5 flex items-center gap-0.5",
              config.text,
            )}
            onClick={(e) => {
              e.stopPropagation();
            }}
          >
            {current.actionLabel}
            <ChevronRight className="h-3 w-3 ml-0.5" />
          </button>
        )}

        <div className="flex items-center gap-1 shrink-0">
          {activeItems.length > 1 && (
            <span className="text-[10px] text-muted-foreground">
              {(currentIndex % activeItems.length) + 1}/{activeItems.length}
            </span>
          )}

          <button
            className="h-5 w-5 flex items-center justify-center rounded hover:bg-black/5"
            onClick={(e) => {
              e.stopPropagation();
              dismissItem(current.id);
            }}
          >
            <X className="h-3 w-3" />
          </button>

          <ChevronDown
            className={cn(
              "h-3.5 w-3.5 text-muted-foreground transition-transform",
              expanded && "rotate-180",
            )}
          />
        </div>
      </div>

      {/* Expanded view -- all items */}
      {expanded && (
        <div className="overflow-hidden">
          <div className="px-3 pb-2 space-y-1.5 border-t border-border/30">
            {activeItems.map((item) => {
              const itemConfig = severityConfig[item.severity];
              const ItemIcon = typeIcons[item.type];
              return (
                <div
                  key={item.id}
                  className={cn(
                    "flex items-start gap-2 px-2 py-1.5 rounded border",
                    itemConfig.bg,
                    itemConfig.border,
                  )}
                >
                  <ItemIcon
                    className={cn(
                      "h-3.5 w-3.5 mt-0.5 shrink-0",
                      itemConfig.text,
                    )}
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-semibold">{item.title}</span>
                      <span className="text-[9px] text-muted-foreground">
                        {item.source}
                      </span>
                    </div>
                    <p className="text-[11px] text-muted-foreground leading-relaxed mt-0.5">
                      {item.message}
                    </p>
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    {item.actionLabel && (
                      <button
                        className={cn(
                          "h-5 text-[10px] px-1.5 rounded hover:bg-black/5",
                          itemConfig.text,
                        )}
                      >
                        {item.actionLabel}
                      </button>
                    )}
                    <button
                      className="h-5 w-5 flex items-center justify-center rounded hover:bg-black/5"
                      onClick={() => dismissItem(item.id)}
                    >
                      <CheckCircle2 className="h-3 w-3" />
                    </button>
                  </div>
                </div>
              );
            })}

            <div className="flex justify-end pt-1">
              <button
                className="h-6 text-[10px] text-muted-foreground px-2 rounded hover:bg-black/5"
                onClick={dismissAll}
              >
                Dismiss All
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
