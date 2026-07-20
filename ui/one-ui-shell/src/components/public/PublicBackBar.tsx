"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ArrowLeft, Home } from "lucide-react";
import { useI18n } from "@/lib/i18n/useI18n";

/**
 * Continuity strip for public sub-pages: an obvious way back and a direct route
 * home, on EVERY public route below /welcome. Replaces the per-page ad-hoc
 * "Welcome / Emergency" breadcrumbs that stranded users (no back affordance,
 * no persistent way to the front door).
 *
 * Back uses browser history when the user arrived from a prior journey and
 * falls back to /welcome for deep links (fresh tab, shared URL) so it never
 * dead-ends.
 */
export function PublicBackBar() {
  const pathname = usePathname();
  const router = useRouter();
  const { t } = useI18n();

  // Only sub-pages need the strip; the front door is the origin.
  if (!pathname || pathname === "/welcome" || pathname === "/welcome/") return null;

  function goBack() {
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back();
    } else {
      router.push("/welcome");
    }
  }

  return (
    <div className="border-b border-slate-200 bg-white/70">
      <div className="mx-auto flex max-w-5xl items-center gap-1 px-4 py-1.5 text-sm">
        <button
          type="button"
          onClick={goBack}
          data-testid="public-back"
          className="inline-flex min-h-[40px] items-center gap-1.5 rounded-md px-2.5 py-1.5 font-medium text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          {t("public.chrome.back")}
        </button>
        <Link
          href="/welcome"
          data-testid="public-home"
          className="inline-flex min-h-[40px] items-center gap-1.5 rounded-md px-2.5 py-1.5 font-medium text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
        >
          <Home className="h-4 w-4" aria-hidden />
          {t("public.chrome.home")}
        </Link>
      </div>
    </div>
  );
}
