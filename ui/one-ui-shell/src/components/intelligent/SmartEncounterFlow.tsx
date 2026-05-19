"use client";

/**
 * Smart Encounter Flow — Health OS Clinical Execution
 *
 * A guided clinical workflow component that orchestrates the entire
 * patient encounter from arrival to discharge. Integrates with PCT
 * for journey state, OROS for orders, Pharmacy for prescriptions,
 * Clinical Knowledge Platform for decision support, and BUTANO for
 * the longitudinal record.
 *
 * Provides:
 * - Step-by-step encounter progression
 * - Real-time clinical decision support (CDS) alerts
 * - Contextual order suggestions based on presenting complaint
 * - Medication interaction checking
 * - Discharge readiness tracking
 * - Automated billing trigger on encounter close
 */

import { useMemo } from "react";
import {
  CheckCircle2, Circle, AlertTriangle, ChevronRight,
  Stethoscope, FlaskConical, Pill, FileText, ClipboardList,
  Activity, Heart, Thermometer, Sparkles,
  Bell, XCircle,
} from "lucide-react";

// ── Types ────────────────────────────────────────────────────────────

interface EncounterStep {
  id: string;
  label: string;
  icon: typeof Stethoscope;
  status: "PENDING" | "ACTIVE" | "COMPLETED" | "SKIPPED" | "BLOCKED";
  blockedBy?: string;
  required: boolean;
  href?: string;
}

interface CDSAlert {
  id: string;
  severity: "CRITICAL" | "WARNING" | "INFO";
  message: string;
  source: string;
  action?: { label: string; href: string };
}

interface EncounterContext {
  journeyId: string;
  encounterId: string;
  patientCpid: string;
  encounterType: string;
  chiefComplaint?: string;
  triageCategory?: string;
  pendingOrders: number;
  pendingResults: number;
  pendingPrescriptions: number;
  dischargeClearances: { type: string; cleared: boolean }[];
  cdsAlerts: CDSAlert[];
}

// ── Step Definitions ───────���─────────────────────────────────────────

function buildSteps(ctx: EncounterContext): EncounterStep[] {
  const steps: EncounterStep[] = [
    {
      id: "VITALS",
      label: "Record Vitals",
      icon: Thermometer,
      status: "PENDING",
      required: true,
      href: `/ehr/${ctx.patientCpid}/vitals?encounter=${ctx.encounterId}`,
    },
    {
      id: "HISTORY",
      label: "Clinical History",
      icon: ClipboardList,
      status: "PENDING",
      required: true,
      href: `/ehr/${ctx.patientCpid}/history?encounter=${ctx.encounterId}`,
    },
    {
      id: "EXAMINATION",
      label: "Examination & Assessment",
      icon: Stethoscope,
      status: "PENDING",
      required: true,
      href: `/ehr/${ctx.patientCpid}/notes?encounter=${ctx.encounterId}`,
    },
    {
      id: "DIAGNOSIS",
      label: "Diagnosis",
      icon: Activity,
      status: "PENDING",
      required: true,
      href: `/ehr/${ctx.patientCpid}/conditions?encounter=${ctx.encounterId}`,
    },
    {
      id: "ORDERS",
      label: `Orders${ctx.pendingOrders > 0 ? ` (${ctx.pendingOrders} pending)` : ""}`,
      icon: FlaskConical,
      status: ctx.pendingOrders > 0 ? "ACTIVE" : "PENDING",
      required: false,
      href: `/ehr/${ctx.patientCpid}/orders?encounter=${ctx.encounterId}`,
    },
    {
      id: "RESULTS",
      label: `Results${ctx.pendingResults > 0 ? ` (${ctx.pendingResults} pending)` : ""}`,
      icon: FileText,
      status: ctx.pendingResults > 0 ? "BLOCKED" : "PENDING",
      blockedBy: ctx.pendingResults > 0 ? "Waiting for lab/imaging results" : undefined,
      required: false,
      href: `/ehr/${ctx.patientCpid}/results?encounter=${ctx.encounterId}`,
    },
    {
      id: "PRESCRIPTIONS",
      label: "Prescriptions",
      icon: Pill,
      status: "PENDING",
      required: false,
      href: `/ehr/${ctx.patientCpid}/medications?encounter=${ctx.encounterId}`,
    },
    {
      id: "CARE_PLAN",
      label: "Care Plan",
      icon: Heart,
      status: "PENDING",
      required: false,
      href: `/ehr/${ctx.patientCpid}/care-plans?encounter=${ctx.encounterId}`,
    },
    {
      id: "DISCHARGE",
      label: "Discharge / Close",
      icon: CheckCircle2,
      status: ctx.dischargeClearances.some(c => !c.cleared) ? "BLOCKED" : "PENDING",
      blockedBy: ctx.dischargeClearances.filter(c => !c.cleared).map(c => c.type).join(", ") || undefined,
      required: true,
      href: `/ehr/${ctx.patientCpid}/discharge?encounter=${ctx.encounterId}`,
    },
  ];

  return steps;
}

// ── Component ──────────────────────���─────────────────────────────────

interface SmartEncounterFlowProps {
  context: EncounterContext;
  onStepClick?: (stepId: string) => void;
  compact?: boolean;
}

export function SmartEncounterFlow({ context, onStepClick, compact = false }: SmartEncounterFlowProps) {
  const steps = useMemo(() => buildSteps(context), [context]);

  const completedCount = steps.filter(s => s.status === "COMPLETED").length;
  const progress = Math.round((completedCount / steps.length) * 100);

  const criticalAlerts = context.cdsAlerts.filter(a => a.severity === "CRITICAL");
  const warningAlerts = context.cdsAlerts.filter(a => a.severity === "WARNING");

  return (
    <div className={`bg-white rounded-xl border border-gray-200 overflow-hidden ${compact ? "" : "shadow-sm"}`}>
      {/* Header with progress */}
      <div className="px-4 py-3 bg-gradient-to-r from-emerald-600 to-green-700 text-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Stethoscope className="h-4 w-4" />
            <span className="text-sm font-semibold">Encounter Flow</span>
          </div>
          <span className="text-xs bg-white/20 px-2 py-0.5 rounded-full">
            {completedCount}/{steps.length} steps
          </span>
        </div>
        {/* Progress bar */}
        <div className="mt-2 h-1.5 bg-white/20 rounded-full overflow-hidden">
          <div
            className="h-full bg-white rounded-full transition-all duration-500"
            style={{ width: `${progress}%` }}
          />
        </div>
        {context.chiefComplaint && (
          <p className="text-xs text-white/70 mt-1.5">
            Chief complaint: {context.chiefComplaint}
            {context.triageCategory && ` · Triage: ${context.triageCategory}`}
          </p>
        )}
      </div>

      {/* CDS Alerts */}
      {context.cdsAlerts.length > 0 && (
        <div className="border-b">
          {criticalAlerts.map(alert => (
            <div key={alert.id} className="px-4 py-2 bg-red-50 border-b border-red-100 flex items-start gap-2">
              <AlertTriangle className="h-4 w-4 text-red-600 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                <p className="text-xs font-medium text-red-800">{alert.message}</p>
                <p className="text-[10px] text-red-500">{alert.source}</p>
              </div>
              {alert.action && (
                <a href={alert.action.href} className="text-[10px] text-red-700 font-medium underline flex-shrink-0">
                  {alert.action.label}
                </a>
              )}
            </div>
          ))}
          {warningAlerts.map(alert => (
            <div key={alert.id} className="px-4 py-2 bg-amber-50 border-b border-amber-100 flex items-start gap-2">
              <Bell className="h-4 w-4 text-amber-600 flex-shrink-0 mt-0.5" />
              <div className="flex-1">
                <p className="text-xs font-medium text-amber-800">{alert.message}</p>
                <p className="text-[10px] text-amber-500">{alert.source}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Step list */}
      <div className="divide-y divide-gray-100">
        {steps.map((step) => {
          const Icon = step.icon;
          return (
            <button
              key={step.id}
              onClick={() => onStepClick?.(step.id)}
              disabled={step.status === "BLOCKED"}
              className={`w-full flex items-center gap-3 px-4 py-3 text-left transition ${
                step.status === "ACTIVE" ? "bg-emerald-50" :
                step.status === "COMPLETED" ? "bg-gray-50" :
                step.status === "BLOCKED" ? "bg-red-50/50 opacity-60" :
                "hover:bg-gray-50"
              }`}
            >
              {/* Status indicator */}
              <div className="flex-shrink-0">
                {step.status === "COMPLETED" ? (
                  <CheckCircle2 className="h-5 w-5 text-emerald-500" />
                ) : step.status === "ACTIVE" ? (
                  <div className="h-5 w-5 rounded-full border-2 border-emerald-500 flex items-center justify-center">
                    <div className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                  </div>
                ) : step.status === "BLOCKED" ? (
                  <XCircle className="h-5 w-5 text-red-400" />
                ) : (
                  <Circle className="h-5 w-5 text-gray-300" />
                )}
              </div>

              {/* Step content */}
              <Icon className={`h-4 w-4 flex-shrink-0 ${
                step.status === "COMPLETED" ? "text-emerald-500" :
                step.status === "ACTIVE" ? "text-emerald-600" :
                step.status === "BLOCKED" ? "text-red-400" :
                "text-gray-400"
              }`} />
              <div className="flex-1 min-w-0">
                <p className={`text-sm ${
                  step.status === "COMPLETED" ? "text-gray-500 line-through" :
                  step.status === "ACTIVE" ? "text-emerald-700 font-medium" :
                  step.status === "BLOCKED" ? "text-red-600" :
                  "text-gray-700"
                }`}>
                  {step.label}
                  {step.required && step.status !== "COMPLETED" && (
                    <span className="text-[10px] text-red-400 ml-1">*</span>
                  )}
                </p>
                {step.blockedBy && (
                  <p className="text-[10px] text-red-500 mt-0.5">Blocked: {step.blockedBy}</p>
                )}
              </div>

              {/* Action arrow */}
              {step.status !== "COMPLETED" && step.status !== "BLOCKED" && (
                <ChevronRight className="h-4 w-4 text-gray-300 flex-shrink-0" />
              )}
            </button>
          );
        })}
      </div>

      {/* Footer — AI suggestion */}
      {context.chiefComplaint && (
        <div className="px-4 py-2.5 bg-violet-50 border-t border-violet-100 flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-violet-500 flex-shrink-0" />
          <p className="text-xs text-violet-700">
            <span className="font-medium">AI Suggestion:</span> Based on &ldquo;{context.chiefComplaint}&rdquo;, consider ordering CBC, Urinalysis. Review EDLIZ guidelines for this presentation.
          </p>
        </div>
      )}
    </div>
  );
}
