import { cookies } from "next/headers";
import { redirect } from "next/navigation";

/**
 * Root entry. Authenticated users go to their dashboard; everyone else lands on the
 * public L0 welcome page (G-CZO-02) — Impilo no longer bounces guests straight into the
 * auth gate. Session presence is the same exp_has_session cookie the middleware checks.
 */
export default function RootPage() {
  const hasSession = cookies().get("exp_has_session")?.value === "1";
  redirect(hasSession ? "/home" : "/welcome");
}
