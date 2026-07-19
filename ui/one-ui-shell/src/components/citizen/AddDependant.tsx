"use client";

/**
 * Add a child / dependant (CJ14). The guardian registers a minor as a person in their own
 * right — the child gets their own Health ID — and a time-bound guardian relationship is
 * created, expiring at the age of majority. The guardian is the authenticated actor
 * (never entered here); a person at/over the age of majority is rejected server-side.
 */

import { useState } from "react";
import { UserPlus, CheckCircle2, AlertCircle } from "lucide-react";
import { useAddDependant, dependantErrorMessage } from "@/hooks/queries/useDependants";

export function AddDependant() {
  const add = useAddDependant();
  const [open, setOpen] = useState(false);
  const [givenName, setGivenName] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [sex, setSex] = useState("");
  const [error, setError] = useState<string | null>(null);

  const result = add.data?.data;
  const canSubmit = givenName.trim() && familyName.trim() && dateOfBirth.trim() && !add.isPending;

  async function submit() {
    setError(null);
    try {
      await add.mutateAsync({
        givenName: givenName.trim(),
        familyName: familyName.trim(),
        dateOfBirth: dateOfBirth.trim(),
        sex: sex || undefined,
      });
    } catch (err) {
      setError(dependantErrorMessage(err));
    }
  }

  if (result) {
    const d = result.dependant ?? {};
    const expires = result.guardianship?.expiresAt;
    return (
      <section className="rounded-lg border border-border bg-card p-4">
        <div className="flex items-start gap-3">
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600" />
          <div>
            <h2 className="text-sm font-medium text-foreground">
              {d.givenName} {d.familyName} has been added
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              They now have their own health record. You can act for them
              {expires ? <> until {new Date(expires).toLocaleDateString()}</> : null}, after which
              they&apos;ll be able to claim their own account.
            </p>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-start gap-3">
        <UserPlus className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
        <div className="flex-1">
          <h2 className="text-sm font-medium text-foreground">Add a child or dependant</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Register a child in your care. They&apos;ll get their own health record, and you&apos;ll
            manage it as their guardian until they come of age.
          </p>

          {!open ? (
            <button
              type="button"
              onClick={() => setOpen(true)}
              className="mt-3 text-sm font-medium text-primary underline hover:no-underline"
            >
              Add a dependant
            </button>
          ) : (
            <div className="mt-3 space-y-3">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <label className="text-sm">
                  <span className="mb-1 block text-xs font-medium text-muted-foreground">First name</span>
                  <input
                    value={givenName}
                    onChange={(e) => setGivenName(e.target.value)}
                    className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm"
                  />
                </label>
                <label className="text-sm">
                  <span className="mb-1 block text-xs font-medium text-muted-foreground">Last name</span>
                  <input
                    value={familyName}
                    onChange={(e) => setFamilyName(e.target.value)}
                    className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm"
                  />
                </label>
                <label className="text-sm">
                  <span className="mb-1 block text-xs font-medium text-muted-foreground">Date of birth</span>
                  <input
                    type="date"
                    value={dateOfBirth}
                    onChange={(e) => setDateOfBirth(e.target.value)}
                    className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm"
                  />
                </label>
                <label className="text-sm">
                  <span className="mb-1 block text-xs font-medium text-muted-foreground">Sex (optional)</span>
                  <select
                    value={sex}
                    onChange={(e) => setSex(e.target.value)}
                    className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm"
                  >
                    <option value="">Prefer not to say</option>
                    <option value="M">Male</option>
                    <option value="F">Female</option>
                  </select>
                </label>
              </div>

              {error && (
                <p className="flex items-center gap-1 text-sm text-danger">
                  <AlertCircle className="h-4 w-4" /> {error}
                </p>
              )}

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => void submit()}
                  disabled={!canSubmit}
                  className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                >
                  {add.isPending ? "Adding…" : "Add dependant"}
                </button>
                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  disabled={add.isPending}
                  className="rounded-md border border-border px-3 py-1.5 text-sm text-foreground"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
