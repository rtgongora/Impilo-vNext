import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";

export const metadata = {
  title: "Emergency & public health — Impilo",
  description: "Emergency numbers and current public-health guidance. Reachable without an account.",
};

/**
 * Public emergency & public-health information (part of G-CZO-02). Static public-safety
 * content, reachable without login. No personal/health data.
 */
export default function EmergencyPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">Welcome</Link> / Emergency
      </nav>

      <div className="mt-3 rounded-2xl border border-red-300 bg-red-50 p-6">
        <h1 className="text-2xl font-bold text-red-800">If life is at risk, get help now</h1>
        <p className="mt-2 text-red-900">
          For a medical emergency, call emergency services or go to the nearest hospital emergency
          department immediately. Do not wait to create an account.
        </p>
        <dl className="mt-4 grid gap-3 sm:grid-cols-3">
          <div className="rounded-lg bg-white p-4">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Ambulance / Police / Fire</dt>
            <dd className="mt-1 text-2xl font-bold text-slate-900">999</dd>
          </div>
          <div className="rounded-lg bg-white p-4">
            <dt className="text-xs uppercase tracking-wide text-slate-500">Alternative emergency</dt>
            <dd className="mt-1 text-2xl font-bold text-slate-900">112</dd>
          </div>
          <div className="rounded-lg bg-white p-4">
            <dt className="text-xs uppercase tracking-wide text-slate-500">National hotline</dt>
            <dd className="mt-1 text-lg font-semibold text-slate-900">2019 (toll-free)</dd>
          </div>
        </dl>
      </div>

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">When to seek urgent care</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>Difficulty breathing, chest pain, or severe bleeding</li>
          <li>Sudden weakness, confusion, or difficulty speaking</li>
          <li>Severe dehydration, high fever in an infant, or a serious injury</li>
          <li>Pregnancy emergencies or reduced baby movement</li>
        </ul>
      </section>

      <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Public-health guidance</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>Wash hands with soap and use safe drinking water to prevent cholera and diarrhoea.</li>
          <li>Keep children up to date with routine immunisations at your nearest clinic.</li>
          <li>Seek early testing and treatment for malaria, TB and other common illnesses.</li>
          <li>Follow current Ministry of Health advisories during outbreaks.</li>
        </ul>
        <Link
          href="/welcome/find-care"
          className="mt-4 inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          Find a facility →
        </Link>
      </section>
    </PublicShell>
  );
}
