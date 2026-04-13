"use client";

/**
 * ActiveCDSBanner — Real-time Clinical Decision Support alerts panel.
 *
 * Shows patient-context alerts (sepsis screening, drug interactions,
 * renal dosing, DVT prophylaxis) with severity levels (critical / high /
 * moderate / info).  Alerts are dismissable and collapsible; when collapsed
 * they auto-rotate every 8 s.
 */

import { useState, useEffect, useCallback } from "react";
import {
  Sparkles,
  AlertTriangle,
  ChevronRight,
  ChevronDown,
  X,
  Lightbulb,
  Pill,
  Activity,
  CheckCircle2,
  Clock,
  Loader2,
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

// ── Mock guidance that would come from CDS rules engine + AI ──

const generateContextualGuidance = (): CDSGuidanceItem[] => [
  {
    id: "cds-1",
    type: "alert",
    severity: "critical",
    title: "Sepsis Screening Due",
    message:
      "Patient has fever (38.2 C), tachycardia (103 bpm), and elevated WBC. qSOFA score >= 2 -- initiate Sepsis Bundle within 1 hour.",
    source: "Clinical Decision Support Rules Engine",
    timestamp: new Date(),
    dismissed: false,
    actionLabel: "Start Sepsis Bundle",
  },
  {
    id: "cds-2",
    type: "recommendation",
    severity: "high",
    title: "Renal Dose Adjustment Needed",
    message:
      "Creatinine rising (1.8 -> 2.1 mg/dL). Metformin 500mg requires dose review -- hold or reduce per eGFR calculation.",
    source: "Pharmacy Clinical Decision Support",
    timestamp: new Date(Date.now() - 5 * 60_000),
    dismissed: false,
    actionLabel: "Review Medications",
  },
  {
    id: "cds-3",
    type: "ai-insight",
    severity: "moderate",
    title: "AI Clinical Insight",
    message:
      "Based on presenting symptoms (acute cholecystitis K81.0), vitals pattern, and diabetes history -- consider early surgical consultation. Evidence suggests better outcomes with cholecystectomy within 72h of admission.",
    source: "AI Diagnostic Assistant",
    timestamp: new Date(Date.now() - 10 * 60_000),
    dismissed: false,
    actionLabel: "Request Consult",
  },
  {
    id: "cds-4",
    type: "reminder",
    severity: "info",
    title: "DVT Prophylaxis",
    message:
      "Patient is post-operative day 1. VTE risk assessment indicates enoxaparin 40mg SC daily. Next dose due at 18:00.",
    source: "Care Protocol Engine",
    timestamp: new Date(Date.now() - 15 * 60_000),
    dismissed: false,
  },
];

// ── Severity visual config ─────────────────────────

const severityConfig = {
  critical: {
    bg: "bg-red-50",
    border: "border-red-200",
    text: "text-red-700",
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
    bg: "bg-amber-50",
    border: "border-amber-200",
    text: "text-amber-700",
    icon: Sparkles,
    pulse: false,
  },
  info: {
    bg: "bg-impilo-50",
    border: "border-impilo-200",
    text: "text-impilo-600",
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
}

// ── Component ──────────────────────────────────────

export function ActiveCDSBanner({ hasActivePatient = true }: ActiveCDSBannerProps) {
  const [guidance, setGuidance] = useState<CDSGuidanceItem[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const [isLoadingAI, setIsLoadingAI] = useState(false);
  const [aiInsight, setAiInsight] = useState<string | null>(null);

  const activeItems = guidance.filter((g) => !g.dismissed);

  // Load contextual guidance when patient is active
  useEffect(() => {
    if (hasActivePatient) {
      const items = generateContextualGuidance();
      setGuidance(items);
      setCurrentIndex(0);
      fetchAIGuidance();
    }
  }, [hasActivePatient]);

  // Auto-rotate through non-dismissed items
  useEffect(() => {
    if (!expanded && activeItems.length > 1) {
      const timer = setInterval(() => {
        setCurrentIndex((prev) => (prev + 1) % activeItems.length);
      }, 8_000);
      return () => clearInterval(timer);
    }
  }, [expanded, activeItems.length]);

  const fetchAIGuidance = useCallback(async () => {
    setIsLoadingAI(true);
    try {
      // In production this would call the vNext BFF /ai-diagnostic endpoint.
      // Simulating async fetch for now.
      await new Promise((r) => setTimeout(r, 1_500));

      const clinicalPearls = [
        "Potassium 5.8 mmol/L is critically elevated -- ECG urgently.",
        "Blood glucose 245 mg/dL with ketosis risk in acute illness.",
        "Penicillin allergy documented -- avoid amoxicillin/ampicillin derivatives.",
      ];

      setAiInsight(clinicalPearls.join(" | "));

      setGuidance((prev) => [
        ...prev,
        {
          id: `ai-live-${Date.now()}`,
          type: "ai-insight" as const,
          severity: "high" as const,
          title: "AI Red Flag Detection",
          message:
            "Hyperkalaemia (K+ 5.8) with renal impairment -- cardiac monitoring required.",
          source: "AI Diagnostic Engine (Live)",
          timestamp: new Date(),
          dismissed: false,
        },
      ]);
    } catch {
      console.warn("Clinical Decision Support AI guidance unavailable");
    } finally {
      setIsLoadingAI(false);
    }
  }, []);

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
          <span className="text-xs text-gray-500 truncate">{current.message}</span>
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
          {isLoadingAI && (
            <Loader2 className="h-3 w-3 animate-spin text-gray-400" />
          )}

          {activeItems.length > 1 && (
            <span className="text-[10px] text-gray-400">
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
              "h-3.5 w-3.5 text-gray-400 transition-transform",
              expanded && "rotate-180",
            )}
          />
        </div>
      </div>

      {/* Expanded view -- all items */}
      {expanded && (
        <div className="overflow-hidden">
          <div className="px-3 pb-2 space-y-1.5 border-t border-gray-200/30">
            {/* AI insight banner */}
            {aiInsight && (
              <div className="flex items-start gap-2 px-2 py-1.5 mt-1.5 rounded bg-impilo-50 border border-impilo-100">
                <Sparkles className="h-3.5 w-3.5 text-impilo-500 mt-0.5 shrink-0" />
                <p className="text-[11px] text-gray-500 leading-relaxed">
                  <span className="font-medium text-gray-900">
                    AI Clinical Pearls:{" "}
                  </span>
                  {aiInsight}
                </p>
              </div>
            )}

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
                      <span className="text-[9px] text-gray-400">
                        {item.source}
                      </span>
                    </div>
                    <p className="text-[11px] text-gray-500 leading-relaxed mt-0.5">
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
                className="h-6 text-[10px] text-gray-400 px-2 rounded hover:bg-black/5"
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
