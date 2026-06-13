"use client";

import { useCallback, useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { AlertTriangle, X } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { apiClient } from "@/lib/api-client";
import { emitShellEvent } from "@/lib/shell/shell-events";

const ESCALATION_TYPES = [
  { id: "clinical_emergency", label: "Clinical emergency" },
  { id: "security_incident", label: "Security incident" },
  { id: "system_outage", label: "System outage" },
  { id: "data_privacy_incident", label: "Data / privacy incident" },
  { id: "connectivity_failure", label: "Connectivity failure" },
  { id: "equipment_failure", label: "Equipment failure" },
  { id: "facility_emergency", label: "Facility emergency / disaster" },
  { id: "safeguarding_concern", label: "Safeguarding concern" },
] as const;

export interface ShellSosDialogProps {
  open: boolean;
  onClose: () => void;
}

/**
 * SOS — shell-level escalation. Emits session events; POSTs to BFF when available.
 * Payload includes `eventSchemaId` for alignment with sos.*.v1 audit contracts.
 */
export function ShellSosDialog({ open, onClose }: ShellSosDialogProps) {
  const pathname = usePathname();
  const user = useAuthStore((s) => s.user);
  const { facility, workspace } = useExperienceEntry();
  const [typeId, setTypeId] = useState<string>(ESCALATION_TYPES[0].id);
  const [note, setNote] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [backendError, setBackendError] = useState<string | null>(null);
  const [wasSubmitted, setWasSubmitted] = useState(false);

  useEffect(() => {
    if (!open) return;
    setWasSubmitted(false);
    setNote("");
    setStatusMessage(null);
    setBackendError(null);
    setTypeId(ESCALATION_TYPES[0].id);
  }, [open]);

  const basePayload = useCallback(() => {
    const patientMatch = pathname?.match(/^\/ehr\/([^/]+)/);
    return {
      eventSchemaId: "sos.activated.v1",
      escalationType: typeId,
      note: note.trim() || undefined,
      pathname: pathname ?? "",
      moduleFromPath: pathname?.split("/").filter(Boolean)[0],
      activePatientId: patientMatch?.[1],
      userId: user?.id,
      actorType: user?.actorType,
      roles: user?.roles,
      facilityId: facility?.id,
      facilityName: facility?.name,
      workspaceId: workspace?.id,
      workspaceName: workspace?.name,
      timestamp: new Date().toISOString(),
      online: typeof navigator !== "undefined" ? navigator.onLine : true,
    };
  }, [pathname, user, facility, workspace, typeId, note]);

  const handleSubmit = async () => {
    setSubmitting(true);
    setBackendError(null);
    setStatusMessage(null);
    const payload = basePayload();
    emitShellEvent("sos_activated", payload);

    try {
      await apiClient.post("/internal/v1/experience/sos/activations", {
        ...payload,
        eventSchemaId: "sos.escalated.v1",
      });
      emitShellEvent("sos_escalated", { ...payload, routed: true });
      setStatusMessage("Escalation recorded. Response teams have been notified where the service is available.");
    } catch {
      emitShellEvent("sos_escalated", { ...payload, routed: false });
      setBackendError(
        "We could not reach the escalation service from this device. Your request was logged locally in this session — please also use facility emergency procedures (phone, overhead, or local runbook).",
      );
    } finally {
      setSubmitting(false);
      setWasSubmitted(true);
    }
  };

  const finishClose = () => {
    if (wasSubmitted) {
      emitShellEvent("sos_closed", { ...basePayload(), eventSchemaId: "sos.closed.v1" });
    } else {
      emitShellEvent("sos_cancelled", { ...basePayload(), eventSchemaId: "sos.cancelled.v1" });
    }
    setNote("");
    setStatusMessage(null);
    setBackendError(null);
    setWasSubmitted(false);
    onClose();
  };

  const handleCancel = () => {
    finishClose();
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[10002] flex items-center justify-center p-4">
      <button
        type="button"
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        aria-label="Close SOS"
        onClick={handleCancel}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="sos-title"
        className="relative w-full max-w-md rounded-2xl border border-danger/28 bg-card p-5 shadow-2xl dark:border-red-900/50 dark:bg-card"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-2 text-danger dark:text-red-400">
            <AlertTriangle className="h-6 w-6 shrink-0" />
            <h2 id="sos-title" className="text-lg font-semibold">
              SOS — Escalate
            </h2>
          </div>
          <button
            type="button"
            className="rounded-lg p-2 text-muted-foreground hover:bg-neutral-100 dark:hover:bg-neutral-900"
            onClick={handleCancel}
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <p className="mt-2 text-sm text-muted-foreground dark:text-muted-foreground">
          Use for real emergencies and operational incidents. This action is audited.
        </p>

        <label className="mt-4 block text-sm font-medium text-foreground dark:text-foreground">
          Escalation type
          <select
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm dark:border-border dark:bg-neutral-900"
            value={typeId}
            onChange={(e) => setTypeId(e.target.value)}
          >
            {ESCALATION_TYPES.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </select>
        </label>

        <label className="mt-3 block text-sm font-medium text-foreground dark:text-foreground">
          Details (optional)
          <textarea
            className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm dark:border-border dark:bg-neutral-900"
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="What is happening, where, and who is affected?"
          />
        </label>

        {backendError ? (
          <p className="mt-3 rounded-lg border border-warning/35 bg-warning-soft p-3 text-sm text-warning-foreground dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100">
            {backendError}
          </p>
        ) : null}
        {statusMessage ? (
          <p className="mt-3 rounded-lg border border-success/25 bg-success-soft p-3 text-sm text-primary-hover dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-100">
            {statusMessage}
          </p>
        ) : null}

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <button
            type="button"
            className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background dark:border-border dark:text-foreground dark:hover:bg-neutral-900"
            onClick={handleCancel}
          >
            {statusMessage || backendError ? "Close" : "Cancel"}
          </button>
          {!statusMessage && !backendError ? (
            <button
              type="button"
              disabled={submitting}
              className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
              onClick={handleSubmit}
            >
              {submitting ? "Sending…" : "Send escalation"}
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
