"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Droplet, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRegisterDonor } from "@/hooks/queries/useMadi";

const BLOOD_GROUPS = ["A", "B", "AB", "O"];
const RH_FACTORS = ["POSITIVE", "NEGATIVE"];

export default function DonorRegisterPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const register = useRegisterDonor();
  const [bloodGroup, setBloodGroup] = useState("O");
  const [rhFactor, setRhFactor] = useState("POSITIVE");

  const personCpid = user?.healthId || user?.id || "";

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!personCpid) return;
    register.mutate(
      {
        person_cpid: personCpid,
        blood_group: bloodGroup,
        rh_factor: rhFactor,
        registered_by: user?.displayName ?? user?.email,
      },
      { onSuccess: () => router.push("/madi/donor/profile") },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Become a Blood Donor"
        subtitle="Voluntary registration — your identity is protected"
        icon={<Droplet className="h-6 w-6" />}
      >
        <form onSubmit={handleSubmit} className="max-w-md space-y-4 rounded-2xl border border-border bg-card p-6">
          <p className="text-sm text-muted-foreground">
            Registering links your Health ID to a donor profile. Only share blood group information
            you already know from a prior test; a nurse will confirm at screening.
          </p>
          <label className="block text-sm font-medium text-foreground">
            Blood group
            <select
              value={bloodGroup}
              onChange={(e) => setBloodGroup(e.target.value)}
              className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm bg-card"
            >
              {BLOOD_GROUPS.map((g) => (
                <option key={g} value={g}>{g}</option>
              ))}
            </select>
          </label>
          <label className="block text-sm font-medium text-foreground">
            Rh factor
            <select
              value={rhFactor}
              onChange={(e) => setRhFactor(e.target.value)}
              className="mt-1 w-full rounded-xl border border-border px-3 py-2 text-sm bg-card"
            >
              {RH_FACTORS.map((r) => (
                <option key={r} value={r}>{r === "POSITIVE" ? "Rh+" : "Rh−"}</option>
              ))}
            </select>
          </label>
          <div className="flex gap-3 pt-2">
            <Link href="/madi/donor" className="flex-1 rounded-xl border border-border py-2 text-center text-sm font-medium text-foreground hover:bg-background">
              Cancel
            </Link>
            <button
              type="submit"
              disabled={!personCpid || register.isPending}
              className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-rose-600 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
            >
              {register.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Register
            </button>
          </div>
          {register.isError && (
            <p className="text-sm text-red-600">Registration failed. Please try again.</p>
          )}
        </form>
      </PageShell>
    </AppLayout>
  );
}
