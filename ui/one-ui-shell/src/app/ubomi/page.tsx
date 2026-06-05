"use client";
import { useState } from "react";
import { Baby, Loader2, Search, Skull } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { PageShell } from "@/components/PageShell";
import { useUbomiRegistry } from "@/hooks/queries/useUbomiRegistry";
import {
  useApproveUbomiBirth,
  useCertifyUbomiDeath,
  useSubmitUbomiBirth,
  useSubmitUbomiDeath,
  useUbomiBirths,
  useUbomiDeaths,
  useUbomiVerification,
} from "@/hooks/queries/useUbomi";
import { useFacilityStore } from "@/hooks/useFacilityStore";

type Tab = "births" | "deaths" | "verify";

export default function UbomiShellPage() {
  const { data: status, isLoading: statusLoading } = useUbomiRegistry();
  const facility = useFacilityStore((s) => s.facility);
  const [tab, setTab] = useState<Tab>("births");
  const [regNo, setRegNo] = useState("");

  const birthsQ = useUbomiBirths();
  const deathsQ = useUbomiDeaths();
  const verifyQ = useUbomiVerification(regNo);
  const submitBirth = useSubmitUbomiBirth();
  const approveBirth = useApproveUbomiBirth();
  const submitDeath = useSubmitUbomiDeath();
  const certifyDeath = useCertifyUbomiDeath();

  const available = status?.available === true;

  const [birthForm, setBirthForm] = useState({
    childGivenNames: "",
    childSurname: "",
    sex: "FEMALE",
    dateOfBirth: new Date().toISOString().slice(0, 10),
    placeOfBirthType: "FACILITY",
  });

  const [deathForm, setDeathForm] = useState({
    deceasedGivenNames: "",
    deceasedSurname: "",
    dateOfDeath: new Date().toISOString().slice(0, 10),
  });

  return (
    <AppLayout>
      <PageShell title="UBOMI civil registry" subtitle="Birth and death notification (CRVS) via Experience BFF">
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-2">
            <FeatureMaturityBadge
              status={available ? "live" : statusLoading ? "partial" : "not_wired"}
              detail={status?.message}
            />
          </div>

          {!available && !statusLoading ? (
            <p className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
              {status?.message ?? "UBOMI service is not reachable."}
            </p>
          ) : null}

          <div className="flex gap-2 border-b border-slate-200">
            {(["births", "deaths", "verify"] as Tab[]).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setTab(t)}
                className={`px-4 py-2 text-sm font-medium capitalize ${
                  tab === t ? "border-b-2 border-teal-600 text-teal-800" : "text-slate-500"
                }`}
              >
                {t}
              </button>
            ))}
          </div>

          {tab === "births" ? (
            <section className="space-y-4">
              <div className="rounded-xl border border-slate-200 bg-white p-4">
                <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
                  <Baby className="h-4 w-4 text-teal-700" />
                  Submit birth notification
                </h3>
                {!facility?.id ? (
                  <p className="text-sm text-amber-800">Select a facility in the header before submitting.</p>
                ) : (
                  <form
                    className="grid gap-3 sm:grid-cols-2"
                    onSubmit={(e) => {
                      e.preventDefault();
                      submitBirth.mutate({
                        ...birthForm,
                        facilityId: facility.id,
                        notificationNumber: `BN-${Date.now()}`,
                      });
                    }}
                  >
                    <input
                      className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                      placeholder="Child given names"
                      value={birthForm.childGivenNames}
                      onChange={(e) => setBirthForm((p) => ({ ...p, childGivenNames: e.target.value }))}
                      required
                    />
                    <input
                      className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                      placeholder="Child surname"
                      value={birthForm.childSurname}
                      onChange={(e) => setBirthForm((p) => ({ ...p, childSurname: e.target.value }))}
                      required
                    />
                    <input
                      type="date"
                      className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                      value={birthForm.dateOfBirth}
                      onChange={(e) => setBirthForm((p) => ({ ...p, dateOfBirth: e.target.value }))}
                    />
                    <select
                      className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                      value={birthForm.sex}
                      onChange={(e) => setBirthForm((p) => ({ ...p, sex: e.target.value }))}
                    >
                      <option value="FEMALE">Female</option>
                      <option value="MALE">Male</option>
                      <option value="OTHER">Other</option>
                    </select>
                    <button
                      type="submit"
                      disabled={submitBirth.isPending}
                      className="sm:col-span-2 rounded-lg bg-teal-700 px-4 py-2 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-50"
                    >
                      {submitBirth.isPending ? "Submitting…" : "Submit birth notification"}
                    </button>
                  </form>
                )}
              </div>

              {birthsQ.isLoading ? (
                <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
              ) : (
                <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
                  {birthsQ.data?.length === 0 ? (
                    <li className="p-4 text-sm text-slate-500">No birth notifications yet.</li>
                  ) : (
                    birthsQ.data?.map((row) => (
                      <li key={String(row.id)} className="flex items-center justify-between gap-3 p-4">
                        <div>
                          <p className="text-sm font-medium text-slate-900">
                            {row.childGivenNames} {row.childSurname}
                          </p>
                          <p className="text-xs text-slate-500">
                            #{row.notificationNumber ?? row.id} · {row.status ?? "—"}
                          </p>
                        </div>
                        {row.status === "SUBMITTED" && row.id != null ? (
                          <button
                            type="button"
                            onClick={() => approveBirth.mutate(Number(row.id))}
                            disabled={approveBirth.isPending}
                            className="rounded-lg border border-teal-200 px-3 py-1 text-xs font-medium text-teal-800 hover:bg-teal-50"
                          >
                            Approve
                          </button>
                        ) : null}
                      </li>
                    ))
                  )}
                </ul>
              )}
            </section>
          ) : null}

          {tab === "deaths" ? (
            <section className="space-y-4">
              <div className="rounded-xl border border-slate-200 bg-white p-4">
                <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-900">
                  <Skull className="h-4 w-4 text-slate-600" />
                  Submit death notification
                </h3>
                <form
                  className="grid gap-3 sm:grid-cols-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    submitDeath.mutate({
                      ...deathForm,
                      facilityId: facility?.id,
                      notificationNumber: `DN-${Date.now()}`,
                    });
                  }}
                >
                  <input
                    className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    placeholder="Deceased given names"
                    value={deathForm.deceasedGivenNames}
                    onChange={(e) => setDeathForm((p) => ({ ...p, deceasedGivenNames: e.target.value }))}
                  />
                  <input
                    className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    placeholder="Deceased surname"
                    value={deathForm.deceasedSurname}
                    onChange={(e) => setDeathForm((p) => ({ ...p, deceasedSurname: e.target.value }))}
                  />
                  <input
                    type="date"
                    className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
                    value={deathForm.dateOfDeath}
                    onChange={(e) => setDeathForm((p) => ({ ...p, dateOfDeath: e.target.value }))}
                  />
                  <button
                    type="submit"
                    disabled={submitDeath.isPending}
                    className="sm:col-span-2 rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-900 disabled:opacity-50"
                  >
                    {submitDeath.isPending ? "Submitting…" : "Submit death notification"}
                  </button>
                </form>
              </div>
              <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
                {deathsQ.data?.map((row) => (
                  <li key={String(row.id)} className="flex items-center justify-between gap-3 p-4">
                    <div>
                      <p className="text-sm font-medium text-slate-900">
                        {String(row.deceasedName ?? `${row.deceasedGivenNames ?? ""} ${row.deceasedSurname ?? ""}`)}
                      </p>
                      <p className="text-xs text-slate-500">#{row.notificationNumber ?? row.id} · {row.status}</p>
                    </div>
                    {row.status === "SUBMITTED" && row.id != null ? (
                      <button
                        type="button"
                        onClick={() => certifyDeath.mutate(Number(row.id))}
                        className="rounded-lg border border-slate-300 px-3 py-1 text-xs font-medium"
                      >
                        Certify
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            </section>
          ) : null}

          {tab === "verify" ? (
            <section className="rounded-xl border border-slate-200 bg-white p-4">
              <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
                <Search className="h-4 w-4" />
                Civil registration number
              </label>
              <input
                className="mt-2 w-full max-w-md rounded-lg border border-slate-300 px-3 py-2 text-sm"
                value={regNo}
                onChange={(e) => setRegNo(e.target.value)}
                placeholder="Enter registration number"
              />
              {verifyQ.isFetching ? <p className="mt-3 text-sm text-slate-500">Looking up…</p> : null}
              {verifyQ.isError ? (
                <p className="mt-3 text-sm text-red-700">Verification lookup failed.</p>
              ) : null}
              {verifyQ.data?.data ? (
                <dl className="mt-4 grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2 text-sm">
                  {Object.entries(verifyQ.data.data as Record<string, unknown>).map(([key, value]) => (
                    <div key={key}>
                      <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">{key}</dt>
                      <dd className="mt-0.5 text-slate-900">{String(value ?? "—")}</dd>
                    </div>
                  ))}
                </dl>
              ) : regNo.trim() && !verifyQ.isFetching ? (
                <p className="mt-3 text-sm text-slate-500">No registration found for that number.</p>
              ) : null}
            </section>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
