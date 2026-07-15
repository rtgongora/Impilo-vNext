import { redirect } from "next/navigation";

/**
 * Legacy mobile share path — /give/[token].
 * Card "help pay my bill" links historically used /give/{shareToken}; the canonical landing is
 * /share/fund/[token] (same bill-contribution BFF). Keep this redirect so already-shared links work.
 */
export default function GiveTokenRedirect({ params }: { params: { token: string } }) {
  const token = encodeURIComponent(params.token ?? "");
  redirect(`/share/fund/${token}`);
}
