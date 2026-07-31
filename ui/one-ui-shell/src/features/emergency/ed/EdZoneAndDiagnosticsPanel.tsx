"use client";

import { useEffect, useState } from "react";
import { bffErrorMessage } from "@/lib/bff-errors";

const ZONES = ["TRIAGE", "RESUS", "MAJORS", "MINORS", "OBSERVATION", "FAST_TRACK", "OTHER"] as const;

export type DiagnosticOrderRow = {
  linkId: string;
  label: string;
  status?: string;
  orosOrderId?: string;
};

export interface EdZoneAndDiagnosticsPanelProps {
  currentZone?: string | null;
  /** Durable worklist from visit GET `diagnostic_orders` (survives reload). */
  diagnosticOrders?: Array<Record<string, unknown>>;
  onAssignZone: (body: { zone: string }) => Promise<unknown>;
  onOrderDiagnostic: (body: Record<string, unknown>) => Promise<{ data?: Record<string, unknown> }>;
  onAct: (body: { linkId: string; actedBy?: string; note?: string }) => Promise<unknown>;
  onClose: (body: { linkId: string; closedBy?: string; reason?: string }) => Promise<unknown>;
  onReconcile: (body: Record<string, unknown>) => Promise<unknown>;
  actor: string;
  isMutating?: boolean;
}

function mapOrders(rows: Array<Record<string, unknown>> | undefined): DiagnosticOrderRow[] {
  if (!rows?.length) return [];
  return rows.map((row) => ({
    linkId: String(row.link_id ?? row.linkId ?? row.id ?? ""),
    label: String(row.test_code ?? row.testCode ?? row.oros_order_id ?? "Diagnostic"),
    status: row.status ? String(row.status) : undefined,
    orosOrderId: row.oros_order_id ? String(row.oros_order_id) : undefined,
  })).filter((r) => r.linkId);
}

/**
 * Zone assignment + ED diagnostic safety lane (order / act / close / reconcile-critical).
 * Links load from the visit read model and refresh after mutations via parent invalidate.
 */
export function EdZoneAndDiagnosticsPanel({
  currentZone,
  diagnosticOrders,
  onAssignZone,
  onOrderDiagnostic,
  onAct,
  onClose,
  onReconcile,
  actor,
  isMutating,
}: EdZoneAndDiagnosticsPanelProps) {
  const [zone, setZone] = useState(currentZone || "MAJORS");
  const [testCode, setTestCode] = useState("TROPONIN");
  const [orderType, setOrderType] = useState("LAB");
  const [links, setLinks] = useState<DiagnosticOrderRow[]>(() => mapOrders(diagnosticOrders));
  const [orosOrderId, setOrosOrderId] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLinks(mapOrders(diagnosticOrders));
  }, [diagnosticOrders]);

  const run = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(bffErrorMessage(e, "ED diagnostics / zone"));
    }
  };

  return (
    <div className="space-y-4" data-testid="ed-zone-diagnostics">
      <div className="rounded-xl border p-4 space-y-2">
        <h3 className="font-semibold">ED zone</h3>
        <p className="text-xs text-muted-foreground">
          Current zone: {currentZone ? String(currentZone) : "not assigned"}
        </p>
        <div className="flex flex-wrap gap-2">
          <select
            className="rounded border px-2 py-1 text-sm"
            value={zone}
            onChange={(e) => setZone(e.target.value)}
            data-testid="ed-zone-select"
            aria-label="ED zone"
          >
            {ZONES.map((z) => (
              <option key={z} value={z}>
                {z.replaceAll("_", " ")}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
            disabled={isMutating}
            data-testid="ed-zone-assign"
            onClick={() => run(() => onAssignZone({ zone }))}
          >
            Assign zone
          </button>
        </div>
      </div>

      <div className="rounded-xl border p-4 space-y-3">
        <h3 className="font-semibold">Diagnostics (ED safety lane)</h3>
        <p className="text-xs text-muted-foreground">
          Orders create the pct↔OROS link that raises pct.ed.critical_result. Act before close when
          a result is critical. Worklist is loaded from the visit record.
        </p>
        <div className="flex flex-wrap gap-2">
          <input
            className="rounded border px-2 py-1 text-sm"
            value={testCode}
            onChange={(e) => setTestCode(e.target.value)}
            placeholder="Test code"
            data-testid="ed-diag-test-code"
          />
          <select
            className="rounded border px-2 py-1 text-sm"
            value={orderType}
            onChange={(e) => setOrderType(e.target.value)}
            data-testid="ed-diag-order-type"
            aria-label="Order type"
          >
            <option value="LAB">LAB</option>
            <option value="IMAGING">IMAGING</option>
            <option value="OTHER">OTHER</option>
          </select>
          <button
            type="button"
            className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
            disabled={!testCode.trim() || isMutating}
            data-testid="ed-diag-order"
            onClick={() =>
              run(async () => {
                const res = await onOrderDiagnostic({
                  testCode: testCode.trim(),
                  orderType,
                  priority: "STAT",
                });
                const data = res?.data ?? (res as Record<string, unknown>);
                const linkId = String(
                  (data as Record<string, unknown>).link_id ??
                    (data as Record<string, unknown>).linkId ??
                    (data as Record<string, unknown>).id ??
                    "",
                );
                if (linkId) {
                  setLinks((prev) => {
                    if (prev.some((p) => p.linkId === linkId)) return prev;
                    return [
                      ...prev,
                      {
                        linkId,
                        label: testCode.trim(),
                        status: String((data as Record<string, unknown>).status ?? "PLACED"),
                      },
                    ];
                  });
                }
              })
            }
          >
            Order diagnostic
          </button>
        </div>

        <ul className="space-y-2 text-sm">
          {links.length === 0 ? (
            <li className="text-muted-foreground">No diagnostic orders on this visit yet.</li>
          ) : (
            links.map((link) => (
              <li key={link.linkId} className="flex flex-wrap items-center gap-2 rounded border p-2">
                <span className="flex-1">
                  {link.label}
                  {link.status ? ` · ${link.status}` : ""}{" "}
                  <code className="text-xs">{link.linkId}</code>
                </span>
                <button
                  type="button"
                  className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
                  disabled={isMutating || link.status === "CLOSED" || link.status === "CANCELLED"}
                  onClick={() =>
                    run(() => onAct({ linkId: link.linkId, actedBy: actor, note: "Acted on result" }))
                  }
                >
                  Act
                </button>
                <button
                  type="button"
                  className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
                  disabled={isMutating || link.status === "CLOSED" || link.status === "CANCELLED"}
                  onClick={() =>
                    run(() =>
                      onClose({ linkId: link.linkId, closedBy: actor, reason: "Completed" }),
                    )
                  }
                >
                  Close
                </button>
              </li>
            ))
          )}
        </ul>

        <div className="flex flex-wrap gap-2 border-t border-border pt-3">
          <input
            className="min-w-[12rem] flex-1 rounded border px-2 py-1 text-sm"
            placeholder="OROS order id to reconcile critical"
            value={orosOrderId}
            onChange={(e) => setOrosOrderId(e.target.value)}
            data-testid="ed-diag-oros-id"
          />
          <button
            type="button"
            className="rounded border px-3 py-1 text-sm disabled:opacity-50"
            disabled={!orosOrderId.trim() || isMutating}
            data-testid="ed-diag-reconcile"
            onClick={() =>
              run(() =>
                onReconcile({
                  orosOrderId: orosOrderId.trim(),
                  oros_order_id: orosOrderId.trim(),
                }),
              )
            }
          >
            Reconcile critical
          </button>
        </div>
      </div>

      {error ? (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
