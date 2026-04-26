"use client";

/**
 * SecureChartAccessFlow — Break-glass chart access authorization.
 * Ported from Lovable (164L). Orchestrates: patient selection → authorization
 * dialog → audit logging → secure navigation to chart.
 * Standards: IHE CCOW, HIPAA 45 CFR 164.312, NPSG.01.01.01
 */

import { useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import { Shield, AlertTriangle, User, Lock, X, Loader2, CheckCircle2 } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";

type ChartAccessReason = "queue_assignment" | "direct_care" | "referral" | "emergency" | "break_glass" | "admin_review";
type PatientContextSource = "queue" | "search" | "referral" | "schedule" | "bed_board" | "direct_url";

export interface PendingChartAccess {
  patientId: string;
  encounterId: string;
  patientName: string;
  patientMrn: string;
  patientDob: string;
  source: PatientContextSource;
}

const ACCESS_REASONS: { id: ChartAccessReason; label: string; description: string; requiresJustification: boolean }[] = [
  { id: "direct_care", label: "Direct Care", description: "Currently providing care to this patient", requiresJustification: false },
  { id: "referral", label: "Referral Review", description: "Reviewing a referral for this patient", requiresJustification: false },
  { id: "emergency", label: "Emergency Access", description: "Urgent clinical need — no prior relationship", requiresJustification: true },
  { id: "break_glass", label: "Break Glass", description: "Override access controls — will be audited", requiresJustification: true },
  { id: "admin_review", label: "Administrative Review", description: "Quality assurance or audit purpose", requiresJustification: true },
];

export function useSecureChartAccess() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [pendingAccess, setPendingAccess] = useState<PendingChartAccess | null>(null);
  const [isAuthorizing, setIsAuthorizing] = useState(false);

  const initiateChartAccess = useCallback((pending: PendingChartAccess) => {
    setPendingAccess(pending);
  }, []);

  const cancelChartAccess = useCallback(() => {
    setPendingAccess(null);
  }, []);

  const authorizeAndOpen = useCallback(async (reason: ChartAccessReason, justification?: string) => {
    if (!pendingAccess) return;
    setIsAuthorizing(true);
    try {
      await apiClient.post("/internal/v1/audit/chart-access", {
        entityType: "patient_chart",
        entityId: pendingAccess.encounterId,
        action: "chart_access_authorized",
        performedBy: user?.id || "unknown",
        metadata: {
          patientId: pendingAccess.patientId,
          accessReason: reason,
          justification,
          source: pendingAccess.source,
          authorizedAt: new Date().toISOString(),
        },
      });
      router.push(`/ehr/${pendingAccess.patientId}?source=${pendingAccess.source}&reason=${reason}`);
      setPendingAccess(null);
    } catch (error) {
      console.error("Error authorizing chart access:", error);
      router.push(`/ehr/${pendingAccess.patientId}?source=${pendingAccess.source}&reason=${reason}`);
      setPendingAccess(null);
    } finally {
      setIsAuthorizing(false);
    }
  }, [pendingAccess, user, router]);

  const quickAccessFromQueue = useCallback(async (encounterId: string, patientId: string) => {
    try {
      await apiClient.post("/internal/v1/audit/chart-access", {
        entityType: "patient_chart", entityId: encounterId, action: "chart_access_queue",
        performedBy: user?.id || "unknown",
        metadata: { patientId, accessReason: "queue_assignment", source: "queue", authorizedAt: new Date().toISOString() },
      });
    } catch { /* audit is best-effort */ }
    router.push(`/ehr/${patientId}?source=queue&reason=queue_assignment`);
  }, [user, router]);

  return { pendingAccess, isAuthorizing, initiateChartAccess, cancelChartAccess, authorizeAndOpen, quickAccessFromQueue };
}

interface SecureChartAccessFlowProps {
  pendingAccess: PendingChartAccess | null;
  isAuthorizing: boolean;
  onAuthorize: (reason: ChartAccessReason, justification?: string) => void;
  onCancel: () => void;
}

export function SecureChartAccessFlow({ pendingAccess, isAuthorizing, onAuthorize, onCancel }: SecureChartAccessFlowProps) {
  const [selectedReason, setSelectedReason] = useState<ChartAccessReason | null>(null);
  const [justification, setJustification] = useState("");

  if (!pendingAccess) return null;

  const reason = ACCESS_REASONS.find(r => r.id === selectedReason);
  const canProceed = selectedReason && (!reason?.requiresJustification || justification.trim().length > 10);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onCancel} />
      <div className="relative bg-white rounded-xl shadow-2xl w-[500px] max-h-[90vh] overflow-y-auto">
        <div className="px-6 py-4 border-b flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-impilo-100 flex items-center justify-center"><Shield className="w-5 h-5 text-impilo-500" /></div>
            <div><h2 className="text-base font-semibold text-gray-900">Authorize Chart Access</h2><p className="text-xs text-gray-500">Identity verification required</p></div>
          </div>
          <button onClick={onCancel} className="p-1.5 rounded hover:bg-gray-100"><X className="w-4 h-4" /></button>
        </div>

        <div className="p-6 space-y-5">
          {/* Patient identity */}
          <div className="bg-gray-50 rounded-lg p-4 flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-impilo-50 flex items-center justify-center"><User className="w-6 h-6 text-impilo-500" /></div>
            <div><p className="text-sm font-semibold text-gray-900">{pendingAccess.patientName}</p><p className="text-xs text-gray-500">MRN: {pendingAccess.patientMrn} &middot; DOB: {pendingAccess.patientDob}</p></div>
          </div>

          {/* Access reason */}
          <div>
            <label className="text-sm font-medium text-gray-700">Reason for Access</label>
            <div className="mt-2 space-y-2">
              {ACCESS_REASONS.map(r => (
                <button key={r.id} onClick={() => setSelectedReason(r.id)}
                  className={`w-full flex items-start gap-3 p-3 rounded-lg border-2 text-left transition-all ${selectedReason === r.id ? "border-impilo-400 bg-impilo-50" : "border-gray-200 hover:bg-gray-50"}`}>
                  <div className={`mt-0.5 w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0 ${selectedReason === r.id ? "border-impilo-400 bg-impilo-500" : "border-gray-300"}`}>
                    {selectedReason === r.id && <CheckCircle2 className="w-3 h-3 text-white" />}
                  </div>
                  <div><p className="text-sm font-medium text-gray-900">{r.label}</p><p className="text-xs text-gray-500">{r.description}</p>
                    {r.requiresJustification && <span className="inline-flex items-center gap-1 mt-1 text-[10px] text-amber-600"><AlertTriangle className="w-3 h-3" /> Requires written justification</span>}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Justification (if required) */}
          {reason?.requiresJustification && (
            <div>
              <label className="text-sm font-medium text-gray-700">Justification <span className="text-red-500">*</span></label>
              <textarea value={justification} onChange={e => setJustification(e.target.value)} rows={3} placeholder="Explain why you need access to this patient's chart..." className="w-full mt-1 px-3 py-2 text-sm border rounded-lg focus:ring-2 focus:ring-impilo-400 focus:border-impilo-400" />
              {justification.length > 0 && justification.length < 10 && <p className="text-xs text-red-500 mt-1">Minimum 10 characters required</p>}
            </div>
          )}

          {/* Warning */}
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 flex items-start gap-2">
            <Lock className="w-4 h-4 text-amber-600 mt-0.5 shrink-0" />
            <p className="text-xs text-amber-800">This access will be logged and audited. Unauthorized access to patient records is a violation of policy and may result in disciplinary action.</p>
          </div>
        </div>

        <div className="px-6 py-4 border-t flex justify-end gap-3">
          <button onClick={onCancel} className="px-4 py-2 text-sm font-medium text-gray-600 border rounded-lg hover:bg-gray-100">Cancel</button>
          <button onClick={() => selectedReason && onAuthorize(selectedReason, justification || undefined)} disabled={!canProceed || isAuthorizing}
            className="px-4 py-2 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 disabled:opacity-50 flex items-center gap-2">
            {isAuthorizing ? <><Loader2 className="w-4 h-4 animate-spin" /> Authorizing...</> : <><Shield className="w-4 h-4" /> Authorize & Open Chart</>}
          </button>
        </div>
      </div>
    </div>
  );
}
