"use client";

/**
 * Platform Origin admin console — country operations + pending platform-action
 * approvals inbox + create-country-operation form.
 *
 * Route: /platform-origin | role-gated PLATFORM_ORIGIN_ADMINISTRATOR
 * (reads open to NATIONAL_ADMINISTRATOR). Experience → BFF → workforce-governance.
 */

import Link from "next/link";
import { useState } from "react";
import { Globe2, Inbox, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import {
  useCountryOperations,
  useCreateCountryOperation,
  usePendingActions,
  type CreateCountryOperationBody,
} from "@/hooks/queries/usePlatformOrigin";

function errText(e: unknown): string {
  if (e && typeof e === "object" && "error" in e) {
    return String((e as { error?: { message?: string } }).error?.message ?? "Request failed");
  }
  return e instanceof Error ? e.message : "Request failed";
}

export default function PlatformOriginConsolePage() {
  const operations = useCountryOperations();
  const pending = usePendingActions("PENDING");
  const create = useCreateCountryOperation();

  const [form, setForm] = useState<CreateCountryOperationBody>({
    tenantId: "",
    isoCountryCode: "",
    displayName: "",
    sovereignOrganisationId: "",
  });
  const [created, setCreated] = useState<{ accessRequestId?: string } | null>(null);

  const rows = operations.data?.data ?? [];
  const inbox = pending.data?.data ?? [];

  const isoValid = /^[A-Za-z]{2}$/.test(form.isoCountryCode);
  const canSubmit = form.tenantId.trim() && isoValid && form.displayName.trim() && !create.isPending;

  function submit() {
    setCreated(null);
    create.mutate(
      {
        tenantId: form.tenantId.trim(),
        isoCountryCode: form.isoCountryCode.trim().toUpperCase(),
        displayName: form.displayName.trim(),
        sovereignOrganisationId: form.sovereignOrganisationId?.trim() || undefined,
      },
      {
        onSuccess: (res) => setCreated({ accessRequestId: res.data?.accessRequestId }),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Platform Origin"
        subtitle="Sovereign country operations and two-person platform actions (initiate → approve → execute). Every mutating action requires two distinct PLATFORM_ORIGIN_ADMINISTRATOR approvals."
      >
        <OrganizationPlaneContextBar />

        <div className="mt-6 grid gap-6 lg:grid-cols-2">
          {/* Country operations */}
          <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
            <div className="flex items-center gap-2 border-b border-border bg-background px-4 py-3 text-sm font-medium text-foreground">
              <Globe2 className="h-4 w-4 text-muted-foreground" /> Country operations
            </div>
            {operations.isLoading ? (
              <div className="px-4 py-8 text-sm text-muted-foreground">Loading…</div>
            ) : operations.isError ? (
              <div className="px-4 py-6 text-sm text-red-600">{errText(operations.error)}</div>
            ) : rows.length === 0 ? (
              <div className="px-4 py-8 text-sm text-muted-foreground">No country operations yet.</div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {rows.map((op) => (
                  <li key={op.id} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div>
                      <div className="font-medium text-foreground">{op.displayName ?? op.isoCountryCode ?? "—"}</div>
                      <div className="text-xs text-muted-foreground">
                        {op.isoCountryCode ?? "—"} · {op.status ?? "—"}
                      </div>
                    </div>
                    {op.id && (
                      <Link
                        href={`/platform-origin/${op.id}`}
                        className="text-sm font-medium text-indigo-600 hover:text-primary-hover"
                      >
                        Manage
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          {/* Pending approvals inbox (A-a) */}
          <section className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
            <div className="flex items-center gap-2 border-b border-border bg-background px-4 py-3 text-sm font-medium text-foreground">
              <Inbox className="h-4 w-4 text-muted-foreground" /> Pending approvals
            </div>
            {pending.isLoading ? (
              <div className="px-4 py-8 text-sm text-muted-foreground">Loading…</div>
            ) : pending.isError ? (
              <div className="px-4 py-6 text-sm text-red-600">{errText(pending.error)}</div>
            ) : inbox.length === 0 ? (
              <div className="px-4 py-8 text-sm text-muted-foreground">No pending platform actions.</div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {inbox.map((req) => {
                  const id = req.accessRequestId ?? req.id;
                  return (
                    <li key={id} className="flex items-center justify-between gap-3 px-4 py-3">
                      <div>
                        <div className="font-mono text-xs text-foreground">{id}</div>
                        <div className="text-xs text-muted-foreground">
                          {req.requestType ?? "PLATFORM_ACTION"} · {req.status ?? "PENDING"}
                        </div>
                      </div>
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                        2 approvals required
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>

        {/* Create country operation */}
        <section className="mt-6 rounded-lg border border-border bg-card p-5 shadow-sm">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <ShieldCheck className="h-4 w-4 text-primary-hover" /> Initiate country operation
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            Creates a PENDING platform action. Two distinct PLATFORM_ORIGIN_ADMINISTRATOR approvals are then required
            before it can be executed.
          </p>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <input
              aria-label="Tenant id"
              placeholder="Tenant id (UUID)"
              value={form.tenantId}
              onChange={(e) => setForm((f) => ({ ...f, tenantId: e.target.value }))}
              className="rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              aria-label="ISO country code"
              placeholder="ISO country code (2 letters)"
              value={form.isoCountryCode}
              onChange={(e) => setForm((f) => ({ ...f, isoCountryCode: e.target.value }))}
              className="rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              aria-label="Display name"
              placeholder="Display name"
              value={form.displayName}
              onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
              className="rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
            <input
              aria-label="Sovereign organisation id"
              placeholder="Sovereign organisation id (optional)"
              value={form.sovereignOrganisationId}
              onChange={(e) => setForm((f) => ({ ...f, sovereignOrganisationId: e.target.value }))}
              className="rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </div>
          {form.isoCountryCode.length > 0 && !isoValid && (
            <p className="mt-2 text-xs text-red-600">ISO country code must be exactly 2 letters.</p>
          )}
          {create.isError && <p className="mt-2 text-xs text-red-600">{errText(create.error)}</p>}
          {created && (
            <div className="mt-3 rounded-md border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              Pending action created — <span className="font-mono">{created.accessRequestId}</span>. 2 approvals required.
            </div>
          )}
          <button
            type="button"
            disabled={!canSubmit}
            onClick={submit}
            className="mt-4 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {create.isPending ? "Initiating…" : "Initiate country operation"}
          </button>
        </section>
      </PageShell>
    </AppLayout>
  );
}
