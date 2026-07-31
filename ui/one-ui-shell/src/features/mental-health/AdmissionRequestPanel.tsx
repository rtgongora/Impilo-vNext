"use client";

/**
 * Admission requests raised from a mental-health referral.
 *
 * A request is raised here and acknowledged only when an admission actually exists in PCT — the
 * acknowledgement carries that admission's id, so the two records are joined by evidence rather
 * than by a status field somebody flipped. Raising is not admitting: a request that nobody
 * acknowledged is a patient still waiting, and it stays visibly RAISED until one does.
 */

import { useState } from "react";

import {
  useMentalHealthAdmissionRequestActions,
  useMentalHealthAdmissionRequests,
} from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

import { PanelShell } from "./PanelShell";

function when(value: unknown): string {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString();
}

export function AdmissionRequestPanel({ referralId, actor }: { referralId: string; actor: string }) {
  const requests = useMentalHealthAdmissionRequests(referralId);
  const actions = useMentalHealthAdmissionRequestActions(referralId);

  const [reason, setReason] = useState("");
  const [targetFacilityId, setTargetFacilityId] = useState("");
  const [admissionIds, setAdmissionIds] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const rows = requests.data ?? [];

  return (
    <PanelShell
      title="Admission requests"
      subject="this patient's admission requests"
      description="mh_admission_request — acknowledged only against a real PCT admission id, so raising is never mistaken for admitting"
      isLoading={requests.isLoading}
      isError={requests.isError}
      error={requests.error}
      isEmpty={rows.length === 0}
      emptyMessage="No admission has been requested for this patient."
      data-testid="mh-admission-requests"
      actions={
        <form
          className="space-y-3"
          data-testid="mh-admission-form"
          onSubmit={async (event) => {
            event.preventDefault();
            setFormError(null);
            try {
              await actions.raise.mutateAsync({
                reason: reason.trim(),
                targetFacilityId: targetFacilityId.trim() || undefined,
                requestedBy: actor || undefined,
              });
              setReason("");
              setTargetFacilityId("");
            } catch (error) {
              setFormError(bffErrorMessage(error, "this admission request"));
            }
          }}
        >
          <label className="block text-sm">
            <span className="font-medium text-foreground">Reason</span>
            <textarea
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              rows={2}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              data-testid="mh-admission-reason"
            />
          </label>

          <label className="block text-sm">
            <span className="font-medium text-foreground">Target facility</span>
            <input
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              value={targetFacilityId}
              onChange={(event) => setTargetFacilityId(event.target.value)}
              placeholder="Facility id, if the admitting unit is already known"
              data-testid="mh-admission-target-facility"
            />
          </label>

          {formError ? (
            <p className="text-sm text-danger" data-testid="mh-admission-error">
              {formError}
            </p>
          ) : null}

          <button
            type="submit"
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            disabled={actions.raise.isPending || !reason.trim()}
            data-testid="mh-admission-submit"
          >
            {actions.raise.isPending ? "Raising…" : "Raise admission request"}
          </button>
        </form>
      }
    >
      <ul className="space-y-2">
        {actionError ? (
          <li className="text-sm text-danger" data-testid="mh-admission-action-error">
            {actionError}
          </li>
        ) : null}
        {rows.map((row, index) => {
          const id = String(row.id ?? index);
          const acknowledged = row.status === "ACKNOWLEDGED";
          return (
            <li key={id} className="rounded border border-border p-3 text-sm" data-testid="mh-admission-row">
              <p className="font-medium text-foreground">
                {acknowledged ? "Acknowledged" : "Raised — no admission yet"} · {when(row.requested_at)}
              </p>
              <p className="mt-1 text-muted-foreground">{String(row.reason ?? "")}</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Requested by {String(row.requested_by ?? "—")}
                {acknowledged ? ` · PCT admission ${String(row.pct_admission_id ?? "—")}` : ""}
              </p>

              {!acknowledged ? (
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <input
                    className="rounded border border-border bg-background px-2 py-1 text-xs"
                    placeholder="PCT admission id"
                    value={admissionIds[id] ?? ""}
                    onChange={(event) =>
                      setAdmissionIds((prev) => ({ ...prev, [id]: event.target.value }))
                    }
                    data-testid="mh-admission-pct-id"
                  />
                  <button
                    type="button"
                    className="rounded border border-border px-2 py-1 text-xs disabled:opacity-50"
                    disabled={actions.acknowledge.isPending || !(admissionIds[id] ?? "").trim()}
                    data-testid="mh-admission-acknowledge"
                    onClick={async () => {
                      setActionError(null);
                      try {
                        await actions.acknowledge.mutateAsync({
                          requestId: id,
                          pctAdmissionId: (admissionIds[id] ?? "").trim(),
                        });
                      } catch (error) {
                        setActionError(bffErrorMessage(error, "this acknowledgement"));
                      }
                    }}
                  >
                    Acknowledge against admission
                  </button>
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </PanelShell>
  );
}
