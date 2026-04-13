"use client";

/**
 * Create provider — admin form.
 * Route: /registry/providers/new
 */

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Loader2, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateProvider,
  type ProviderAdminPayload,
  type ProviderSex,
} from "@/hooks/queries/useProviderAdmin";

const inputClass =
  "w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400";

export default function NewProviderPage() {
  const router = useRouter();
  const createMutation = useCreateProvider();
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

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
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

        <form
          onSubmit={handleSubmit}
          className="max-w-2xl space-y-4 bg-white rounded-lg border border-gray-200 p-6"
        >
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Given name
              </label>
              <input
                type="text"
                required
                value={form.givenName}
                onChange={(e) => setForm({ ...form, givenName: e.target.value })}
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Family name
              </label>
              <input
                type="text"
                required
                value={form.familyName}
                onChange={(e) => setForm({ ...form, familyName: e.target.value })}
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Profession
              </label>
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
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Date of birth
              </label>
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
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Council code
              </label>
              <input
                type="text"
                required
                value={form.councilCode}
                onChange={(e) => setForm({ ...form, councilCode: e.target.value })}
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Registration number
              </label>
              <input
                type="text"
                required
                value={form.registrationNumber}
                onChange={(e) => setForm({ ...form, registrationNumber: e.target.value })}
                className={inputClass}
              />
            </div>
          </div>

          {createMutation.isError && (
            <p className="text-sm text-red-600">Could not create provider. Check the form and try again.</p>
          )}

          <div className="flex items-center gap-3 pt-2">
            <button
              type="submit"
              disabled={createMutation.isPending}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium bg-impilo-500 text-white rounded-lg hover:bg-impilo-600 disabled:opacity-50 transition-colors"
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
      </PageShell>
    </AppLayout>
  );
}
