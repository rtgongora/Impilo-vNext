import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { PublicLanding } from "@/components/public/PublicLanding";

export const metadata = {
  title: "Impilo — Zimbabwe's National Health Operating System",
  description:
    "How can Impilo help you today? Find care, get emergency help, access public services and progressively unlock protected health and professional work.",
};

/**
 * One Impilo entry point.
 *
 * Guests receive the complete need-first public living canvas at the canonical
 * root. Existing sessions continue to their authorised context without a second
 * product boundary or a duplicate public website.
 */
export default function RootPage() {
  const hasSession = cookies().get("exp_has_session")?.value === "1";
  if (hasSession) redirect("/home");
  return <PublicLanding />;
}
