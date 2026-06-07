"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Building2, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRegisterBloodBank } from "@/hooks/queries/useMadi";
import { getStoredBloodBankId, setStoredBloodBankId } from "@/lib/madi-session";

const LINKS = [
  { href: "/madi/blood-bank/stock", label: "Stock balances" },
  { href: "/madi/blood-bank/orders", label: "Bank orders" },
  { href: "/madi/blood-bank/crossmatch", label: "Crossmatch" },
  { href: "/madi/blood-bank/issue", label: "Issue units" },
];

export default function BloodBankHubPage() {
  const facility = useFacilityStore((s) => s.facility);
  const register = useRegisterBloodBank();
  const [bloodBankId, setBloodBankId] = useState("");
  const [bankName, setBankName] = useState("");

  useEffect(() => {
    if (facility?.id) setBloodBankId(getStoredBloodBankId(facility.id));
  }, [facility?.id]);

  function saveId() {
    if (facility?.id && bloodBankId.trim()) {
      setStoredBloodBankId(facility.id, bloodBankId.trim());
    }
  }

  function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    if (!facility?.id) return;
    register.mutate(
      { facility_id: facility.id, bank_name: bankName, bank_type: "FACILITY" },
      {
        onSuccess: (bank) => {
          const id = String((bank as { bloodBankId?: string }).bloodBankId ?? (bank as { id?: string }).id ?? "");
          if (id) {
            setBloodBankId(id);
            setStoredBloodBankId(facility.id, id);
          }
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Local Blood Bank" subtitle="Facility blood bank workspace" icon={<Building2 className="h-6 w-6" />}>
        {!facility?.id && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 mb-4">
            Select a facility work context to manage the blood bank.
          </div>
        )}

        <div className="rounded-2xl border border-gray-200 bg-white p-5 mb-6 space-y-3">
          <p className="text-sm text-gray-600">Set the blood bank UUID for this facility session.</p>
          <div className="flex flex-wrap gap-2">
            <input
              value={bloodBankId}
              onChange={(e) => setBloodBankId(e.target.value)}
              placeholder="Blood bank UUID"
              className="flex-1 min-w-[240px] rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono"
            />
            <button type="button" onClick={saveId} className="rounded-xl bg-gray-800 px-4 py-2 text-sm font-medium text-white hover:bg-gray-900">
              Save for session
            </button>
          </div>
        </div>

        {!bloodBankId && facility?.id && (
          <form onSubmit={handleRegister} className="rounded-2xl border border-dashed border-gray-300 p-6 mb-6 space-y-3">
            <h3 className="font-medium text-gray-900">Register a new blood bank</h3>
            <input required value={bankName} onChange={(e) => setBankName(e.target.value)} placeholder="Bank name" className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm" />
            <button type="submit" disabled={register.isPending} className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50">
              {register.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Register bank
            </button>
          </form>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {LINKS.map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className={`rounded-xl border p-4 text-sm font-medium transition-colors ${bloodBankId ? "border-gray-200 bg-white hover:border-rose-300 text-gray-900" : "border-gray-100 bg-gray-50 text-gray-400 pointer-events-none"}`}
            >
              {label}
            </Link>
          ))}
        </div>

        {!bloodBankId && (
          <p className="mt-4 text-xs text-gray-500">Save or register a blood bank ID to unlock sub-workspaces.</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
