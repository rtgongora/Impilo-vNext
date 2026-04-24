import Link from "next/link";

/**
 * Product-style shell entry. OIDC sign-in only; no diagnostics or manual identity.
 */
export default function HomePage() {
  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900">
      <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center px-6 py-16">
        <p className="text-sm font-semibold uppercase tracking-wide text-brand-primary">
          Impilo vNext
        </p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight text-neutral-900 md:text-4xl">
          Impilo One UI Shell
        </h1>
        <p className="mt-5 text-base leading-relaxed text-neutral-600">
          This is the shared entry for Impilo clinical and operational experiences.
          Sign in with your <strong className="font-medium text-neutral-800">Impilo identity</strong>{" "}
          (Keycloak) to continue. Your session is established only after a successful
          sign-in—we never show you as signed in before that.
        </p>
        <div className="mt-10 flex flex-col gap-3 sm:flex-row sm:items-center">
          <a
            href="/api/auth/login"
            className="inline-flex items-center justify-center rounded-[12px] bg-brand-primary px-6 py-3 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary/90 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:ring-offset-2"
          >
            Sign in
          </a>
          <span className="text-sm text-neutral-500">
            You will be redirected to Keycloak to authenticate.
          </span>
        </div>
        <p className="mt-14 border-t border-neutral-200 pt-8 text-xs text-neutral-500">
          <Link
            href="/dev/runtime"
            className="font-medium text-brand-primary hover:underline"
          >
            Developer runtime diagnostics
          </Link>
          <span className="text-neutral-400"> — </span>
          trust headers, TSHEPO policy checks, and local-only tooling. Not part of the
          normal user journey.
        </p>
      </main>
    </div>
  );
}
