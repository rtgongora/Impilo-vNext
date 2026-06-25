import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";

export const metadata = {
  title: "Find care — Impilo",
  description: "Major public referral hospitals, how to visit a facility, and how to access the full facility finder.",
};

/**
 * Public find-care orientation (part of G-CZO-02). This is reference information only —
 * major public referral hospitals are public knowledge, not patient data. The complete,
 * searchable facility directory and appointment booking require an account, so this page
 * orients newcomers and routes them to get started. No authenticated calls; no PII.
 */
const REFERRAL_HOSPITALS = [
  { name: "Parirenyatwa Group of Hospitals", city: "Harare" },
  { name: "Harare Central Hospital", city: "Harare" },
  { name: "Chitungwiza Central Hospital", city: "Chitungwiza" },
  { name: "Mpilo Central Hospital", city: "Bulawayo" },
  { name: "United Bulawayo Hospitals", city: "Bulawayo" },
  { name: "Mutare Provincial Hospital", city: "Mutare" },
  { name: "Gweru Provincial Hospital", city: "Gweru" },
  { name: "Masvingo Provincial Hospital", city: "Masvingo" },
];

export default function FindCarePage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">Welcome</Link> / Find care
      </nav>
      <h1 className="mt-2 text-3xl font-bold text-slate-900">Find care near you</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Below are major public referral hospitals. To search the full facility directory, see clinic
        hours, and book appointments, create an account or sign in.
      </p>

      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          href="/auth/register"
          className="rounded-lg bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700"
        >
          Create an account to use the facility finder
        </Link>
        <Link
          href="/welcome/emergency"
          className="rounded-lg border border-red-300 bg-white px-5 py-2.5 font-semibold text-red-700 hover:bg-red-50"
        >
          Emergency information
        </Link>
      </div>

      <section className="mt-8">
        <h2 className="font-semibold text-slate-900">Major public referral hospitals</h2>
        <ul className="mt-3 grid gap-3 sm:grid-cols-2">
          {REFERRAL_HOSPITALS.map((h) => (
            <li key={h.name} className="rounded-xl border border-slate-200 bg-white p-4">
              <p className="font-medium text-slate-900">{h.name}</p>
              <p className="text-sm text-slate-500">{h.city}</p>
            </li>
          ))}
        </ul>
        <p className="mt-3 text-xs text-slate-500">
          This is a reference list of major referral hospitals, not the complete national directory.
          Sign in for the full, searchable facility finder.
        </p>
      </section>

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Visiting a facility</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>Bring an identity document if you have one — it helps verify your Health ID faster.</li>
          <li>No Health ID yet? Staff can register you and issue a temporary Health ID on arrival.</li>
          <li>You control consent: you decide who can see your records.</li>
        </ul>
      </section>
    </PublicShell>
  );
}
