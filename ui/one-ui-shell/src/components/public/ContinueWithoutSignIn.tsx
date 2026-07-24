import Link from "next/link";

export function safePublicHref(href: string): string {
  return href === "/welcome" || href.startsWith("/welcome/") ? href : "/welcome";
}

export function ContinueWithoutSignIn({
  href,
  label = "Continue without signing in",
  className = "",
}: {
  href: string;
  label?: string;
  className?: string;
}) {
  return (
    <Link
      href={safePublicHref(href)}
      data-testid="continue-without-sign-in"
      className={`inline-flex min-h-10 items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 ${className}`}
    >
      {label}
    </Link>
  );
}
