"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
/**
 * Create provider — admin form.
 * Route: /registry/providers/new
 */

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Link2, Loader2, Save, UserPlus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateProvider,
  useCreateProviderFromHealthId,
  type ProviderAdminPayload,
  type ProviderSex,
} from "@/hooks/queries/useProviderAdmin";

const inputClass =
  "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500";

function errorMessage(err: unknown): string {
  if (err && typeof err === "object" && "error" in err) {
    const e = (err as { error?: { message?: string } }).error;
    if (e?.message) return e.message;
  }
  return "Request failed. Check fields and try again.";
}

export default function NewProviderPage() {
  const router = useRouter();
  const createMutation = useCreateProvider();
  const createAnchored = useCreateProviderFromHealthId();
  const [mode, setMode] = useState<"standalone" | "anchored">("standalone");
  const [form, setForm] = useState({
    givenName: "",
    familyName: "",
    profession: "",
    cadre: "",
    dateOfBirth: "",
    sex: "MALE" as ProviderSex,
    councilCode: "",
    registrationNumber: "",
  });
  const [impiloHealthId, setImpiloHealthId] = useState("");
  const [anchoredResult, setAnchoredResult] = useState<Record<string, unknown> | null>(null);

  function handleSubmitStandalone(e: React.FormEvent) {
    e.preventDefault();
    setAnchoredResult(null);
    const payload: ProviderAdminPayload = {
      givenName: form.givenName.trim(),
      familyName: form.familyName.trim(),
      profession: form.profession.trim(),
      cadre: form.cadre.trim(),
      dateOfBirth: form.dateOfBirth,
      sex: form.sex,
      councilCode: form.councilCode.trim(),
      registrationNumber: form.registrationNumber.trim(),
    };
    createMutation.mutate(payload, {
      onSuccess: () => {
        router.push("/registry/providers");
      },
    });
  }

  function handleSubmitAnchored(e: React.FormEvent) {
    e.preventDefault();
    setAnchoredResult(null);
    const hid = impiloHealthId.trim();
    if (!hid) {
      return;
    }
    const payload = {
      impiloHealthId: hid,
      givenName: form.givenName.trim(),
      familyName: form.familyName.trim(),
      profession: form.profession.trim(),
      cadre: form.cadre.trim(),
      dateOfBirth: form.dateOfBirth,
      sex: form.sex,
      councilCode: form.councilCode.trim(),
      registrationNumber: form.registrationNumber.trim(),
    };
    createAnchored.mutate(payload, {
      onSuccess: (res) => {
        const data = res.data as Record<string, unknown> | undefined;
        setAnchoredResult(data ?? {});
      },
    });
  }

  return (
    <AppLayout>
      <PageShell title="New Provider" subtitle="Register a healthcare provider">
        <div className="mb-4">
          <Link
            href="/registry/providers"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to providers
          </Link>
        </div>

        <div className="mb-6 inline-flex rounded-lg border border-gray-200 bg-gray-50 p-1 text-xs font-medium">
          <button
            type="button"
            onClick={() => setMode("standalone")}
            className={`inline-flex items-center gap-1.5 rounded-md px-3 py-2 transition-colors ${
              mode === "standalone" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"
            }`}
          >
            <UserPlus className="h-3.5 w-3.5" />
            Direct (admin)
          </button>
          <button
            type="button"
            onClick={() => setMode("anchored")}
            className={`inline-flex items-center gap-1.5 rounded-md px-3 py-2 transition-colors ${
              mode === "anchored" ? "bg-white text-gray-900 shadow-sm" : "text-gray-600 hover:text-gray-900"
            }`}
          >
            <Link2 className="h-3.5 w-3.5" />
            Impilo Health ID anchor
          </button>
        </div>

        {mode === "standalone" ? (
          <form
            onSubmit={handleSubmitStandalone}
            className="max-w-2xl space-y-4 bg-white rounded-lg border border-gray-200 p-6"
          >
            <ProviderFields form={form} setForm={setForm} />

            {createMutation.isError && (
              <p className="text-sm text-red-600">{errorMessage(createMutation.error)}</p>
            )}

            <div className="flex items-center gap-3 pt-2">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
              >
                {createMutation.isPending ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Saving...
                  </>
                ) : (
                  <>
                    <Save className="w-4 h-4" />
                    Create provider
                  </>
                )}
              </button>
            </div>
          </form>
        ) : (
          <form
            onSubmit={handleSubmitAnchored}
            className="max-w-2xl space-y-4 bg-white rounded-lg border border-gray-200 p-6"
          >
            <p className="text-sm text-gray-600">
              The person must already exist in the Vito client registry. The BFF validates the anchor, then creates the
              Varapi profile with the same fields as a direct registration.
            </p>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Impilo Health ID (required)</label>
              <input
                type="text"
                required
                value={impiloHealthId}
                onChange={(e) => setImpiloHealthId(e.target.value)}
                className={inputClass}
                placeholder="Client registry person identifier"
              />
            </div>
            <ProviderFields form={form} setForm={setForm} />

            {createAnchored.isError && (
              <p className="text-sm text-red-600">{errorMessage(createAnchored.error)}</p>
            )}

            {anchoredResult != null && Object.keys(anchoredResult).length > 0 && (
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                <p className="text-xs font-medium text-emerald-900 mb-2">Varapi response</p>
                <QueryResultPanel title="Anchored result" data={anchoredResult} />
                <button
                  type="button"
                  className="mt-3 text-xs font-medium text-emerald-800 underline hover:text-emerald-950"
                  onClick={() => router.push("/registry/providers")}
                >
                  Go to provider list
                </button>
              </div>
            )}

            <div className="flex items-center gap-3 pt-2">
              <button
                type="submit"
                disabled={createAnchored.isPending}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 transition-colors"
              >
                {createAnchored.isPending ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Creating…
                  </>
                ) : (
                  <>
                    <Link2 className="w-4 h-4" />
                    Create provider (anchored)
                  </>
                )}
              </button>
            </div>
          </form>
        )}
      </PageShell>
    </AppLayout>
  );
}

function ProviderFields({
  form,
  setForm,
}: {
  form: {
    givenName: string;
    familyName: string;
    profession: string;
    cadre: string;
    dateOfBirth: string;
    sex: ProviderSex;
    councilCode: string;
    registrationNumber: string;
  };
  setForm: React.Dispatch<React.SetStateAction<typeof form>>;
}) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Given name</label>
        <input
          type="text"
          required
          value={form.givenName}
          onChange={(e) => setForm({ ...form, givenName: e.target.value })}
          className={inputClass}
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Family name</label>
        <input
          type="text"
          required
          value={form.familyName}
          onChange={(e) => setForm({ ...form, familyName: e.target.value })}
          className={inputClass}
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Profession</label>
        <input
          type="text"
          required
          value={form.profession}
          onChange={(e) => setForm({ ...form, profession: e.target.value })}
          className={inputClass}
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Cadre</label>
        <input
          type="text"
          required
          value={form.cadre}
          onChange={(e) => setForm({ ...form, cadre: e.target.value })}
          className={inputClass}
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Date of birth</label>
        <input
          type="date"
          required
          value={form.dateOfBirth}
          onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
          className={inputClass}
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Sex</label>
        <select
          value={form.sex}
          onChange={(e) => setForm({ ...form, sex: e.target.value as ProviderSex })}
          className={inputClass}
        >
          <option value="MALE">Male</option>
          <option value="FEMALE">Female</option>
          <option value="OTHER">Other</option>
        </select>
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Council code</label>
        <input
          type="text"
          required
          value={form.councilCode}
          onChange={(e) => setForm({ ...form, councilCode: e.target.value })}
          className={inputClass}
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Registration number</label>
        <input
          type="text"
          required
          value={form.registrationNumber}
          onChange={(e) => setForm({ ...form, registrationNumber: e.target.value })}
          className={inputClass}
        />
      </div>
    </div>
  );
}
