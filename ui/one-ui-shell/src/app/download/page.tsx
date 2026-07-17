import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { GetAppSurface } from "@/components/download/GetAppSurface";

export const metadata = {
  title: "Get the Impilo apps",
  description:
    "Download Impilo (for citizens) and Impilo Provider (for health workers). The mobile apps are in pre-release — use Impilo on the web today. Official Ministry of Health and Child Care apps.",
};

/**
 * Get-app / download surface (public — guard: none).
 *
 * Honest states only: the mobile apps exist but are NOT yet on the stores, so
 * every store affordance is a "coming soon" with a notify-me interest capture,
 * the web app is always offered as the available-now path, and the QR points to
 * the web app — never a fabricated store deep-link.
 */
export default function DownloadPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        / Get the app
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Take Impilo with you</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Two apps, one National Health Operating System. The Impilo mobile apps are on the way —
          and you can use Impilo on the web today, no install needed.
        </p>
      </section>

      <section className="mt-6">
        <GetAppSurface />
      </section>
    </PublicShell>
  );
}
