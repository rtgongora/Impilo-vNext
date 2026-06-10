"use client";

import { InvitationLifecycleActions } from "./InvitationLifecycleActions";
import { InvitationStatusBadge } from "./InvitationStatusBadge";
import { normalizeInvitationView, type InvitationView } from "@/lib/admin-governance/invitation-lifecycle";

export interface ImportRowReviewItem {
  id: string;
  rowNumber?: number;
  outcome?: string;
  invitationId?: string;
  normalizedPayloadJson?: string;
  invitation?: Record<string, unknown>;
}

interface ImportRowReviewTableProps {
  rows: ImportRowReviewItem[];
  onResend?: (rowId: string) => Promise<void>;
  onRevoke?: (rowId: string) => Promise<void>;
}

function rowEmail(row: ImportRowReviewItem): string {
  if (!row.normalizedPayloadJson) return "—";
  try {
    const payload = JSON.parse(row.normalizedPayloadJson) as Record<string, string>;
    return payload.email ?? payload.official_email ?? "—";
  } catch {
    return "—";
  }
}

export function ImportRowReviewTable({ rows, onResend, onRevoke }: ImportRowReviewTableProps) {
  if (!rows.length) {
    return <p className="text-sm text-slate-600">No staged import rows yet. Upload and approve a batch to review invitation status.</p>;
  }

  return (
    <div className="overflow-auto rounded-lg border">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-600">
          <tr>
            <th className="px-3 py-2">Row</th>
            <th className="px-3 py-2">Email</th>
            <th className="px-3 py-2">Stage outcome</th>
            <th className="px-3 py-2">Invitation</th>
            <th className="px-3 py-2">Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const invitation: InvitationView = normalizeInvitationView(row.invitation);
            return (
              <tr key={row.id} className="border-t align-top">
                <td className="px-3 py-2">{row.rowNumber ?? row.id.slice(0, 8)}</td>
                <td className="px-3 py-2">{rowEmail(row)}</td>
                <td className="px-3 py-2">{row.outcome ?? "—"}</td>
                <td className="px-3 py-2">
                  <InvitationStatusBadge invitation={invitation} compact />
                </td>
                <td className="px-3 py-2">
                  <InvitationLifecycleActions
                    invitation={invitation}
                    onResend={onResend ? () => onResend(row.id) : undefined}
                    onRevoke={onRevoke ? () => onRevoke(row.id) : undefined}
                  />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
