import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";

export const metadata = {
  title: "Accessibility & language — Impilo",
  description: "How Impilo supports accessibility, low-cost devices, assisted registration, and languages.",
};

/**
 * Public accessibility & language information (part of G-CZO-02; deepened by Slice 6).
 * Static, reachable without login, no PII.
 */
export default function AccessibilityPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">Welcome</Link> / Accessibility &amp; language
      </nav>
      <h1 className="mt-2 text-3xl font-bold text-slate-900">Accessibility &amp; language</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Impilo aims to be usable by everyone, including people using assistive technology, low-cost
        phones, or shared devices, and those who need help to register.
      </p>

      <section className="mt-6 grid gap-4 sm:grid-cols-2">
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">Designed for access</h2>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
            <li>High-contrast and larger-text display options in your account settings</li>
            <li>Screen-reader friendly pages and keyboard navigation</li>
            <li>Works on low-cost devices and slower connections</li>
          </ul>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">Help to get started</h2>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
            <li>No smartphone? A health worker can register you at a facility.</li>
            <li>Trusted family members can help, with your consent.</li>
            <li>You never need an insecure workaround to access care.</li>
          </ul>
        </div>
      </section>

      <section className="mt-6 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Languages</h2>
        <p className="mt-1 text-sm text-slate-600">
          Impilo is currently available in English, with Shona and Ndebele support being introduced.
          Display and accessibility preferences can be set once you create an account.
        </p>
        <Link
          href="/auth/register"
          className="mt-3 inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          Create an account →
        </Link>
      </section>
    </PublicShell>
  );
}
