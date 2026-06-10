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

  useEffect(() => {
    async function load() {
      setLoading(true);
      const response = await listOrganisations();
      if (isActionResponse(response) && isPendingBackend(response)) {
        setPendingMessage(response.friendlyMessage);
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

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <h3 className="text-base font-semibold text-slate-900">Organisation Registry</h3>
        <p className="mt-1 text-sm text-slate-600">
          Organisation records are sourced from workforce-governance. No fake persistence is shown when downstream is unavailable.
        </p>
      </div>

      {pendingMessage ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">{pendingMessage}</div>
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
            <label key={name} className="text-sm text-slate-700">
              {label}
              <input
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                value={form[name] ?? ""}
                onChange={(event) => setForm((prev) => ({ ...prev, [name]: event.target.value }))}
              />
            </label>
          ))}
          <button
            type="button"
            onClick={() => void handleCreate()}
            className="rounded-lg bg-indigo-700 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 md:col-span-2 md:w-fit"
          >
            Register organisation
          </button>
        </div>
      ) : (
        <>
          {loading ? <p className="text-sm text-slate-600">Loading organisations…</p> : null}
          {!loading && items.length === 0 ? (
            <p className="text-sm text-slate-600">No organisations returned for your scope.</p>
          ) : (
            <ul className="space-y-2">
              {items.map((item) => {
                const id = orgId(item);
                return (
                  <li key={id} className="rounded-lg border border-slate-100 px-3 py-2 text-sm">
                    <Link href={`/work/administration-governance/organisations/${id}`} className="font-medium text-indigo-700 hover:underline">
                      {item.legalName ?? item.name ?? id}
                    </Link>
                    <p className="text-xs text-slate-500">
                      {item.organisationType ?? "organisation"} · {item.status ?? item.lifecycleStatus ?? "unknown status"}
                    </p>
                  </li>
                );
              })}
            </ul>
          )}
          {selected ? (
            <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm">
              <p className="font-medium text-slate-900">{selected.legalName ?? selected.name}</p>
              <p className="text-slate-600">Status: {selected.status ?? selected.lifecycleStatus ?? "unknown"}</p>
              <button
                type="button"
                onClick={() => void handleVerify()}
                className="mt-3 rounded-lg border border-indigo-200 bg-white px-3 py-1.5 text-xs font-medium text-indigo-800"
              >
                Verify organisation
              </button>
            </div>
          ) : null}
        </>
      )}

      {actionResult ? <GovernanceActionResult result={actionResult} /> : null}
    </section>
  );
}
