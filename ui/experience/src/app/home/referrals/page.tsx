import { redirect } from "next/navigation";

export default function HomeReferralsRedirectPage() {
  redirect("/queue/incoming-referrals");
}
