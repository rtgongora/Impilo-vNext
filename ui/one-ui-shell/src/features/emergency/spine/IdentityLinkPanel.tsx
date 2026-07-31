"use client";

import { useState } from "react";
import { bffErrorMessage } from "@/lib/bff-errors";
import type { EmergencyIdentityLinkRow } from "@/hooks/queries/useEmergencyEpisode";

const METHODS = ["VITO_LOOKUP", "NATIONAL_ID", "BIOMETRIC", "FAMILY_CONFIRMATION", "PROVISIONAL_MERGE", "OTHER"] as const;

export interface IdentityLinkPanelProps {
  links: EmergencyIdentityLinkRow[] | undefined;
  isLoading: boolean;
  isError: boolean;
  onLink: (body: {
    resolvedSubjectCpid: string;
    resolutionMethod: string;
    evidenceRef?: string;
    resolvedBy?: string;
  }) => Promise<unknown>;
  isMutating?: boolean;
  actor?: string;
}

export function IdentityLinkPanel({
  links,
  isLoading,
  isError,
  onLink,
  isMutating,
  actor,
}: IdentityLinkPanelProps) {
  const [cpid, setCpid] = useState("");
  const [method, setMethod] = useState<string>(METHODS[0]);
  const [evidence, setEvidence] = useState("");
  const [error, setError] = useState<string | null>(null);

  return (
    <div className="space-y-3 rounded border p-4" data-testid="identity-link-panel">
      <h3 className="text-sm font-semibold">Identity link</h3>
      <p className="text-xs text-muted-foreground">
        Append-only resolution onto a CPID. Prior provisional identities are retained, never overwritten.
      </p>

      {isLoading ? <p className="text-sm text-muted-foreground">Loading links…</p> : null}
      {isError ? (
        <p className="text-sm text-danger" data-testid="identity-link-unreadable">
          Identity links could not be read.
        </p>
      ) : null}

      <ul className="space-y-1 text-sm" data-testid="identity-link-list">
        {(links ?? []).map((link) => (
          <li key={String(link.link_id)} className="flex justify-between gap-2">
            <span>{String(link.resolved_subject_cpid ?? "—")}</span>
            <span className="text-muted-foreground">{String(link.resolution_method ?? "")}</span>
          </li>
        ))}
        {!isLoading && !isError && (links ?? []).length === 0 ? (
          <li className="text-muted-foreground">No identity links recorded yet.</li>
        ) : null}
      </ul>

      <div className="flex flex-wrap gap-2">
        <input
          className="min-w-[10rem] flex-1 rounded border px-2 py-1 text-sm"
          placeholder="Resolved CPID"
          value={cpid}
          onChange={(e) => setCpid(e.target.value)}
          data-testid="identity-cpid"
        />
        <select
          className="rounded border px-2 py-1 text-sm"
          value={method}
          onChange={(e) => setMethod(e.target.value)}
          data-testid="identity-method"
          aria-label="Resolution method"
        >
          {METHODS.map((m) => (
            <option key={m} value={m}>
              {m.replaceAll("_", " ")}
            </option>
          ))}
        </select>
        <input
          className="min-w-[8rem] flex-1 rounded border px-2 py-1 text-sm"
          placeholder="Evidence ref (optional)"
          value={evidence}
          onChange={(e) => setEvidence(e.target.value)}
          data-testid="identity-evidence"
        />
        <button
          type="button"
          className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
          disabled={!cpid.trim() || isMutating}
          data-testid="identity-link-submit"
          onClick={async () => {
            setError(null);
            try {
              await onLink({
                resolvedSubjectCpid: cpid.trim(),
                resolutionMethod: method,
                evidenceRef: evidence.trim() || undefined,
                resolvedBy: actor,
              });
              setCpid("");
              setEvidence("");
            } catch (e) {
              setError(bffErrorMessage(e, "identity link"));
            }
          }}
        >
          Link identity
        </button>
      </div>
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
