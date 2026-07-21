import { redirect } from "next/navigation";

/**
 * Legacy citizen coverage home. The canonical health-financing dashboard is now
 * "My Ruvimbo" at /ruvimbo/member. Keep this redirect so existing deep links and the
 * post-enrolment navigation (/coverage/enroll → success) still land correctly.
 */
export default function CoverageMemberRedirect() {
  redirect("/ruvimbo/member");
}
