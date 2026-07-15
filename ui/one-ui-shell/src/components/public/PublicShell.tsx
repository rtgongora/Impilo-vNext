import Link from "next/link";
import type { ReactNode } from "react";
import { EmergencyHelpButton } from "./EmergencyHelpButton";
import { PublicAccessibilityMenu } from "./PublicAccessibilityMenu";

/**
 * Public L0 shell — the chrome for unauthenticated, pre-account pages (welcome,
 * find-care, emergency). Carries NO personal/health data and makes no authenticated
 * calls: every link points either to another public page or into the /auth flow.
 *
 * This is the guest entry the Health OS previously lacked (G-CZO-02): an ordinary
 * person can understand what Impilo is and how to get started before signing in.
 */
export function PublicShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <Link href="/welcome" className="flex items-center gap-2 font-semibold text-slate-900">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-emerald-600 text-white">i</span>
            <span className="text-lg">Impilo</span>
            <span className="hidden text-sm font-normal text-slate-500 sm:inline">Health Operating System</span>
          </Link>
          <nav className="flex items-center gap-2 text-sm">
            <PublicAccessibilityMenu />
            <Link
              href="/auth/login"
              className="rounded-md px-3 py-1.5 font-medium text-slate-700 hover:bg-slate-100"
            >
              Sign in
            </Link>
            <Link
              href="/auth/register/contact"
              className="rounded-md bg-emerald-600 px-3 py-1.5 font-medium text-white hover:bg-emerald-700"
            >
              Create account
            </Link>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">{children}</main>

      {/* Doctrine §7: persistent, clearly labelled Emergency Help on every public page. */}
      <EmergencyHelpButton />

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl flex-col gap-2 px-4 py-6 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between">
          <p>© Ministry of Health &amp; Child Care · Impilo</p>
          <nav className="flex flex-wrap gap-4">
            <Link href="/welcome/find-care" className="hover:text-slate-900">Find care</Link>
            <Link href="/welcome/emergency" className="hover:text-slate-900">Emergency</Link>
            <Link href="/privacy" className="hover:text-slate-900">Privacy</Link>
            <Link href="/terms" className="hover:text-slate-900">Terms</Link>
            <Link href="/welcome/accessibility" className="hover:text-slate-900">Accessibility &amp; language</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
