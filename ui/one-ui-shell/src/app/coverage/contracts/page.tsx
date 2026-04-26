"use client";

/**
 * Provider contract & network management — payer administrators.
 * Route: /coverage/contracts
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import {
  ArrowLeft,
  Briefcase,
  ChevronDown,
  ChevronRight,
  Loader2,
  Network,
  Plus,
  RotateCcw,
  UserMinus,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAddNetworkMember,
  useCreateContract,
  useCreateNetwork,
  useNetworkMembers,
  useProviderContracts,
  useProviderNetworks,
  useReinstateContract,
  useRemoveNetworkMember,
  useSuspendContract,
  type ProviderContractFilters,
  type ProviderNetworkFilters,
} from "@/hooks/queries/useProviderContracts";

type Tab = "contracts" | "networks";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

const CONTRACT_TYPES = [
  "FEE_FOR_SERVICE",
  "CAPITATION",
  "CASE_RATE",
  "GLOBAL_BUDGET",
] as const;

const NETWORK_TYPES = ["PREFERRED", "EXCLUSIVE", "OPEN"] as const;

export default function ProviderContractsAdminPage() {
  const [tab, setTab] = useState<Tab>("contracts");

  return (
    <AppLayout>
      <PageShell
        title="Provider contracts & networks"
        subtitle="Manage payer–provider contracts and preferred provider networks"
        icon={<Briefcase className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/coverage"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to coverage operations
          </Link>
        </div>

        <div className="flex gap-1 mb-6 border-b border-gray-200">
          <button
            type="button"
            onClick={() => setTab("contracts")}
            className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              tab === "contracts"
                ? "border-violet-600 text-violet-600"
                : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            <Briefcase className="h-4 w-4" /> Provider contracts
          </button>
          <button
            type="button"
            onClick={() => setTab("networks")}
            className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors ${
              tab === "networks"
                ? "border-violet-600 text-violet-600"
                : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            <Network className="h-4 w-4" /> Provider networks
          </button>
        </div>

        {tab === "contracts" && <ContractsTab />}
        {tab === "networks" && <NetworksTab />}
      </PageShell>
    </AppLayout>
  );
}

function ContractsTab() {
  const [draft, setDraft] = useState<ProviderContractFilters>({});
  const [applied, setApplied] = useState<ProviderContractFilters>({});
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({
    providerId: "",
    payerId: "",
    planCode: "",
    contractType: "FEE_FOR_SERVICE",
    reimbursementMode: "FEE_FOR_SERVICE",
    effectiveFrom: "",
    effectiveTo: "",
  });

  const q = useProviderContracts(applied);
  const create = useCreateContract();
  const suspend = useSuspendContract();
  const reinstate = useReinstateContract();

  const rows = useMemo(() => (q.data ?? []).map(asRecord), [q.data]);

  const submitCreate = () => {
    if (!form.providerId.trim() || !form.payerId.trim() || !form.effectiveFrom.trim()) return;
    const body: Record<string, unknown> = {
      providerId: form.providerId.trim(),
      payerId: form.payerId.trim(),
      contractType: form.contractType,
      reimbursementMode: form.reimbursementMode.trim() || "FEE_FOR_SERVICE",
      effectiveFrom: form.effectiveFrom.trim(),
    };
    if (form.planCode.trim()) body.planCode = form.planCode.trim();
    if (form.effectiveTo.trim()) body.effectiveTo = form.effectiveTo.trim();
    create.mutate(body, {
      onSuccess: () => {
        setShowForm(false);
        setForm({
          providerId: "",
          payerId: "",
          planCode: "",
          contractType: "FEE_FOR_SERVICE",
          reimbursementMode: "FEE_FOR_SERVICE",
          effectiveFrom: "",
          effectiveTo: "",
        });
      },
    });
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-semibold text-gray-900">Contracts</h2>
        <button
          type="button"
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white hover:bg-violet-700"
        >
          <Plus className="h-4 w-4" /> New contract
        </button>
      </div>

      <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Status</label>
          <select
            className="w-full rounded-lg border border-gray-300 px-2 py-2 text-sm"
            value={draft.status ?? ""}
            onChange={(e) => setDraft({ ...draft, status: e.target.value || undefined })}
          >
            <option value="">All</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Provider ID</label>
          <input
            className="w-full rounded-lg border border-gray-300 px-2 py-2 text-sm"
            value={draft.provider_id ?? ""}
            onChange={(e) => setDraft({ ...draft, provider_id: e.target.value || undefined })}
            placeholder="Filter"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Payer ID</label>
          <input
            className="w-full rounded-lg border border-gray-300 px-2 py-2 text-sm"
            value={draft.payer_id ?? ""}
            onChange={(e) => setDraft({ ...draft, payer_id: e.target.value || undefined })}
            placeholder="Filter"
          />
        </div>
        <div className="flex items-end">
          <button
            type="button"
            onClick={() => setApplied({ ...draft })}
            className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-800 hover:bg-gray-100"
          >
            Apply filters
          </button>
        </div>
      </div>

      {showForm && (
        <div className="rounded-xl border border-violet-200 bg-violet-50/40 p-4 space-y-3">
          <h3 className="text-sm font-semibold text-gray-900">New contract</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <input
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              placeholder="Provider ID *"
              value={form.providerId}
              onChange={(e) => setForm({ ...form, providerId: e.target.value })}
            />
            <input
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              placeholder="Payer ID *"
              value={form.payerId}
              onChange={(e) => setForm({ ...form, payerId: e.target.value })}
            />
            <input
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              placeholder="Plan code"
              value={form.planCode}
              onChange={(e) => setForm({ ...form, planCode: e.target.value })}
            />
            <select
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              value={form.contractType}
              onChange={(e) => setForm({ ...form, contractType: e.target.value })}
            >
              {CONTRACT_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
            <input
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              placeholder="Reimbursement mode"
              value={form.reimbursementMode}
              onChange={(e) => setForm({ ...form, reimbursementMode: e.target.value })}
            />
            <input
              type="date"
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              value={form.effectiveFrom}
              onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })}
            />
            <input
              type="date"
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              value={form.effectiveTo}
              onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })}
            />
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={create.isPending}
              onClick={submitCreate}
              className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
            >
              {create.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : null} Save
            </button>
          </div>
          {create.isError && <p className="text-xs text-red-600">Create failed. Check required fields and dates.</p>}
        </div>
      )}

      {q.isLoading && (
        <div className="flex items-center gap-2 text-sm text-gray-600 py-8">
          <Loader2 className="h-5 w-5 animate-spin" /> Loading contracts…
        </div>
      )}
      {q.isError && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">Could not load contracts.</p>
      )}
      {!q.isLoading && !q.isError && rows.length === 0 && (
        <p className="text-sm text-gray-500">No contracts match the current filters.</p>
      )}
      {!q.isLoading && !q.isError && rows.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-gray-50 text-left text-gray-600">
                <th className="px-3 py-2">Provider</th>
                <th className="px-3 py-2">Payer</th>
                <th className="px-3 py-2">Type</th>
                <th className="px-3 py-2">Reimbursement</th>
                <th className="px-3 py-2">Effective</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {rows.map((row, i) => {
                const id = readStr(row, "contractId", "contract_id");
                const st = readStr(row, "status") || "ACTIVE";
                const active = st === "ACTIVE";
                return (
                  <tr key={id || i}>
                    <td className="px-3 py-2 font-mono text-xs">{readStr(row, "providerId", "provider_id") || "—"}</td>
                    <td className="px-3 py-2 font-mono text-xs">{readStr(row, "payerId", "payer_id") || "—"}</td>
                    <td className="px-3 py-2">{readStr(row, "contractType", "contract_type") || "—"}</td>
                    <td className="px-3 py-2">{readStr(row, "reimbursementMode", "reimbursement_mode") || "—"}</td>
                    <td className="px-3 py-2 whitespace-nowrap text-gray-600">
                      {readStr(row, "effectiveFrom", "effective_from")} → {readStr(row, "effectiveTo", "effective_to") || "—"}
                    </td>
                    <td className="px-3 py-2">
                      <span
                        className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                          active ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"
                        }`}
                      >
                        {st}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-right">
                      {active ? (
                        <button
                          type="button"
                          disabled={!id || suspend.isPending}
                          onClick={() => id && suspend.mutate({ id })}
                          className="inline-flex items-center gap-1 text-xs font-medium text-amber-800 hover:text-amber-950 disabled:opacity-40"
                        >
                          Suspend
                        </button>
                      ) : (
                        <button
                          type="button"
                          disabled={!id || reinstate.isPending}
                          onClick={() => id && reinstate.mutate(id)}
                          className="inline-flex items-center gap-1 text-xs font-medium text-green-800 hover:text-green-950 disabled:opacity-40"
                        >
                          <RotateCcw className="h-3 w-3" /> Reinstate
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function NetworksTab() {
  const [draft, setDraft] = useState<ProviderNetworkFilters>({});
  const [applied, setApplied] = useState<ProviderNetworkFilters>({});
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: "", payerId: "", networkType: "PREFERRED" });
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [addForNetwork, setAddForNetwork] = useState<string | null>(null);
  const [addForm, setAddForm] = useState({ providerId: "", contractId: "" });

  const q = useProviderNetworks(applied);
  const membersQ = useNetworkMembers(expandedId);
  const create = useCreateNetwork();
  const addMember = useAddNetworkMember();
  const removeMember = useRemoveNetworkMember();

  const rows = useMemo(() => (q.data ?? []).map(asRecord), [q.data]);
  const memberRows = useMemo(() => (membersQ.data ?? []).map(asRecord), [membersQ.data]);

  const submitNetwork = () => {
    if (!form.name.trim() || !form.payerId.trim()) return;
    create.mutate(
      { name: form.name.trim(), payerId: form.payerId.trim(), networkType: form.networkType },
      {
        onSuccess: () => {
          setShowForm(false);
          setForm({ name: "", payerId: "", networkType: "PREFERRED" });
        },
      },
    );
  };

  const submitMember = (networkId: string) => {
    if (!addForm.providerId.trim()) return;
    const body: Record<string, unknown> = { providerId: addForm.providerId.trim() };
    if (addForm.contractId.trim()) body.contractId = addForm.contractId.trim();
    addMember.mutate(
      { networkId, body },
      {
        onSuccess: () => {
          setAddForm({ providerId: "", contractId: "" });
          setAddForNetwork(null);
        },
      },
    );
  };

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-semibold text-gray-900">Networks</h2>
        <button
          type="button"
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white hover:bg-violet-700"
        >
          <Plus className="h-4 w-4" /> New network
        </button>
      </div>

      <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Payer ID</label>
          <input
            className="w-full rounded-lg border border-gray-300 px-2 py-2 text-sm"
            value={draft.payer_id ?? ""}
            onChange={(e) => setDraft({ ...draft, payer_id: e.target.value || undefined })}
            placeholder="Filter"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Status</label>
          <select
            className="w-full rounded-lg border border-gray-300 px-2 py-2 text-sm"
            value={draft.status ?? ""}
            onChange={(e) => setDraft({ ...draft, status: e.target.value || undefined })}
          >
            <option value="">All</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
          </select>
        </div>
        <div className="flex items-end">
          <button
            type="button"
            onClick={() => setApplied({ ...draft })}
            className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-800 hover:bg-gray-100"
          >
            Apply filters
          </button>
        </div>
      </div>

      {showForm && (
        <div className="rounded-xl border border-violet-200 bg-violet-50/40 p-4 space-y-3">
          <h3 className="text-sm font-semibold text-gray-900">New network</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <input
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              placeholder="Network name *"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
            <input
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              placeholder="Payer ID *"
              value={form.payerId}
              onChange={(e) => setForm({ ...form, payerId: e.target.value })}
            />
            <select
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
              value={form.networkType}
              onChange={(e) => setForm({ ...form, networkType: e.target.value })}
            >
              {NETWORK_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={() => setShowForm(false)} className="rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm">
              Cancel
            </button>
            <button
              type="button"
              disabled={create.isPending}
              onClick={submitNetwork}
              className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
            >
              {create.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : null} Save
            </button>
          </div>
          {create.isError && <p className="text-xs text-red-600">Create failed.</p>}
        </div>
      )}

      {q.isLoading && (
        <div className="flex items-center gap-2 text-sm text-gray-600 py-8">
          <Loader2 className="h-5 w-5 animate-spin" /> Loading networks…
        </div>
      )}
      {q.isError && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">Could not load networks.</p>
      )}
      {!q.isLoading && !q.isError && rows.length === 0 && (
        <p className="text-sm text-gray-500">No networks match the current filters.</p>
      )}
      {!q.isLoading && !q.isError && rows.length > 0 && (
        <div className="space-y-2">
          {rows.map((row, i) => {
            const nid = readStr(row, "networkId", "network_id");
            const expanded = expandedId === nid;
            const st = readStr(row, "status") || "ACTIVE";
            const count = Number(row.memberCount ?? row.member_count ?? 0);
            return (
              <div key={nid || i} className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
                <div className="flex flex-wrap items-center gap-3 px-4 py-3 border-b border-gray-100 bg-slate-50/80">
                  <button
                    type="button"
                    onClick={() => setExpandedId(expanded ? null : nid || null)}
                    className="p-1 rounded hover:bg-gray-200 text-gray-600"
                    aria-expanded={expanded}
                  >
                    {expanded ? <ChevronDown className="h-5 w-5" /> : <ChevronRight className="h-5 w-5" />}
                  </button>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-gray-900">{readStr(row, "name") || "—"}</p>
                    <p className="text-xs text-gray-500 font-mono">
                      {readStr(row, "payerId", "payer_id")} · {readStr(row, "networkType", "network_type") || "—"}
                    </p>
                  </div>
                  <span
                    className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
                      st === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"
                    }`}
                  >
                    {st}
                  </span>
                  <span className="text-xs text-gray-600 whitespace-nowrap">{count} members</span>
                  <button
                    type="button"
                    onClick={() => setAddForNetwork(addForNetwork === nid ? null : nid)}
                    className="inline-flex items-center gap-1 rounded-lg border border-violet-300 bg-violet-50 px-2 py-1 text-xs font-medium text-violet-800 hover:bg-violet-100"
                  >
                    <Plus className="h-3 w-3" /> Add provider
                  </button>
                </div>
                {addForNetwork === nid && (
                  <div className="px-4 py-3 bg-violet-50/50 border-b border-violet-100 flex flex-wrap gap-2 items-end">
                    <input
                      className="flex-1 min-w-[140px] rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      placeholder="Provider ID *"
                      value={addForm.providerId}
                      onChange={(e) => setAddForm({ ...addForm, providerId: e.target.value })}
                    />
                    <input
                      className="flex-1 min-w-[180px] rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      placeholder="Contract UUID (optional)"
                      value={addForm.contractId}
                      onChange={(e) => setAddForm({ ...addForm, contractId: e.target.value })}
                    />
                    <button
                      type="button"
                      disabled={!nid || addMember.isPending}
                      onClick={() => nid && submitMember(nid)}
                      className="rounded-lg bg-violet-600 px-3 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                    >
                      {addMember.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : "Add"}
                    </button>
                  </div>
                )}
                {expanded && nid && (
                  <div className="p-4">
                    {membersQ.isLoading && (
                      <div className="flex items-center gap-2 text-sm text-gray-500">
                        <Loader2 className="h-4 w-4 animate-spin" /> Loading members…
                      </div>
                    )}
                    {membersQ.isError && <p className="text-xs text-red-600">Could not load network members.</p>}
                    {!membersQ.isLoading && !membersQ.isError && memberRows.length === 0 && (
                      <p className="text-sm text-gray-500">No providers in this network yet.</p>
                    )}
                    {!membersQ.isLoading && memberRows.length > 0 && (
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b text-left text-gray-600">
                            <th className="pb-2 pr-2">Provider</th>
                            <th className="pb-2 pr-2">Contract</th>
                            <th className="pb-2 pr-2">Joined</th>
                            <th className="pb-2 text-right">Actions</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                          {memberRows.map((m, j) => {
                            const pid = readStr(m, "providerId", "provider_id");
                            return (
                              <tr key={readStr(m, "id") || j}>
                                <td className="py-2 pr-2 font-mono text-xs">{pid || "—"}</td>
                                <td className="py-2 pr-2 font-mono text-xs">
                                  {readStr(m, "contractId", "contract_id") || "—"}
                                </td>
                                <td className="py-2 pr-2 text-gray-600 text-xs whitespace-nowrap">
                                  {readStr(m, "joinedAt", "joined_at")
                                    ? new Date(readStr(m, "joinedAt", "joined_at")).toLocaleString()
                                    : "—"}
                                </td>
                                <td className="py-2 text-right">
                                  <button
                                    type="button"
                                    disabled={removeMember.isPending}
                                    onClick={() => nid && pid && removeMember.mutate({ networkId: nid, providerId: pid })}
                                    className="inline-flex items-center gap-1 text-xs font-medium text-red-700 hover:text-red-900"
                                  >
                                    <UserMinus className="h-3 w-3" /> Remove
                                  </button>
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
