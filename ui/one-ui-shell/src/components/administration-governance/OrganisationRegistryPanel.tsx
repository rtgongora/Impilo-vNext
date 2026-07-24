"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import {
  buildOrganisationCreatePayload,
  createOrganisation,
  listOrganisations,
  verifyOrganisation,
} from "@/lib/admin-governance/api/organisationsApi";
import { isActionResponse, isPendingBackend } from "@/lib/admin-governance/api/client";
import type { AdminGovernanceActionResponse, LookupEnvelope, OrganisationRecord } from "@/lib/admin-governance/types";
import { useUploadOrganizationLogo } from "@/hooks/queries/useOrgOnboarding";

function orgId(record: OrganisationRecord): string {
  return record.organisationId ?? record.id ?? "";
}

export function OrganisationRegistryPanel() {
  const pathname = usePathname();
  const isNew = pathname.endsWith("/new");
  const orgMatch = pathname.match(/\/organisations\/([^/]+)/);
  const selectedOrgId = orgMatch && orgMatch[1] !== "new" ? orgMatch[1] : null;

  const [items, setItems] = useState<OrganisationRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [pendingMessage, setPendingMessage] = useState<string | null>(null);
  const [form, setForm] = useState<Record<string, string>>({
    organisationCode: "",
    organisationType: "public_hospital",
    legalName: "",
    tradingName: "",
    country: "ZW",
    registrationNumber: "",
  });
  const [actionResult, setActionResult] = useState<AdminGovernanceActionResponse | null>(null);
  const logoUpload = useUploadOrganizationLogo();

  useEffect(() => {
    async function load() {
      setLoading(true);
      const response = await listOrganisations();
      if (isActionResponse(response) && isPendingBackend(response)) {
        setPendingMessage(response.friendlyMessage ?? null);
        setItems([]);
      } else {
        const envelope = response as LookupEnvelope<{ items: OrganisationRecord[] }>;
        setPendingMessage(envelope.integrationStatus === "pending_backend" ? envelope.friendlyMessage ?? null : null);
        setItems(envelope.data?.items ?? []);
      }
      setLoading(false);
    }
    void load();
  }, []);

  const selected = useMemo(
    () => items.find((item) => orgId(item) === selectedOrgId),
    [items, selectedOrgId],
  );

  async function handleCreate() {
    const result = await createOrganisation(buildOrganisationCreatePayload(form));
    setActionResult(result);
    if (result.status === "completed") {
      const refreshed = await listOrganisations();
      if (!isActionResponse(refreshed)) {
        setItems((refreshed as LookupEnvelope<{ items: OrganisationRecord[] }>).data?.items ?? []);
      }
    }
  }

  async function handleVerify() {
    if (!selectedOrgId) return;
    const result = await verifyOrganisation(selectedOrgId);
    setActionResult(result);
  }

  async function handleLogo(file: File) {
    if (!selectedOrgId) return;
    try {
      await logoUpload.mutateAsync({ organizationId: selectedOrgId, file });
    } catch {
      // Mutation state renders the fail-closed user message below.
    }
  }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div>
        <h3 className="text-base font-semibold text-foreground">Organisation Registry</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Organisation records are sourced from workforce-governance. No fake persistence is shown when downstream is unavailable.
        </p>
      </div>

      {pendingMessage ? (
        <div className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">{pendingMessage}</div>
      ) : null}

      {isNew ? (
        <div className="grid gap-3 md:grid-cols-2">
          {[
            ["organisationCode", "Organisation code"],
            ["organisationType", "Organisation type"],
            ["legalName", "Legal name"],
            ["tradingName", "Trading name"],
            ["country", "Country"],
            ["registrationNumber", "Registration number"],
          ].map(([name, label]) => (
            <label key={name} className="text-sm text-foreground">
              {label}
              <input
                className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                value={form[name] ?? ""}
                onChange={(event) => setForm((prev) => ({ ...prev, [name]: event.target.value }))}
              />
            </label>
          ))}
          <button
            type="button"
            onClick={() => void handleCreate()}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 md:col-span-2 md:w-fit"
          >
            Register organisation
          </button>
        </div>
      ) : (
        <>
          {loading ? <p className="text-sm text-muted-foreground">Loading organisations…</p> : null}
          {!loading && items.length === 0 ? (
            <p className="text-sm text-muted-foreground">No organisations returned for your scope.</p>
          ) : (
            <ul className="space-y-2">
              {items.map((item) => {
                const id = orgId(item);
                return (
                  <li key={id} className="rounded-lg border border-border px-3 py-2 text-sm">
                    <Link href={`/work/administration-governance/organisations/${id}`} className="font-medium text-primary-hover hover:underline">
                      {item.legalName ?? item.name ?? id}
                    </Link>
                    <p className="text-xs text-muted-foreground">
                      {item.organisationType ?? "organisation"} · {item.status ?? item.lifecycleStatus ?? "unknown status"}
                    </p>
                  </li>
                );
              })}
            </ul>
          )}
          {selected ? (
            <div className="rounded-xl border border-border bg-background px-4 py-3 text-sm">
              <p className="font-medium text-foreground">{selected.legalName ?? selected.name}</p>
              <p className="text-muted-foreground">Status: {selected.status ?? selected.lifecycleStatus ?? "unknown"}</p>
              <button
                type="button"
                onClick={() => void handleVerify()}
                className="mt-3 rounded-lg border border-info/25 bg-card px-3 py-1.5 text-xs font-medium text-primary-hover"
              >
                Verify organisation
              </button>
              <label className="mt-3 block text-xs font-medium text-foreground">
                Organisation logo
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  disabled={logoUpload.isPending}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    if (file) void handleLogo(file);
                  }}
                  className="mt-1 block w-full text-xs"
                />
              </label>
              <p className="mt-1 text-xs text-muted-foreground">
                PNG, JPEG, or WebP, up to 2 MB. Stored in the sovereign document store.
              </p>
              {logoUpload.isSuccess ? (
                <p role="status" className="mt-2 text-xs text-success-foreground">Logo uploaded.</p>
              ) : null}
              {logoUpload.isError ? (
                <p role="alert" className="mt-2 text-xs text-danger-foreground">Logo upload failed. No branding change was saved.</p>
              ) : null}
            </div>
          ) : null}
        </>
      )}

      {actionResult ? <GovernanceActionResult result={actionResult} /> : null}
    </section>
  );
}
