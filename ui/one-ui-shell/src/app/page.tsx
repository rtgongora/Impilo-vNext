export const dynamic = "force-dynamic";

import { PublicLanding } from "@/components/public/PublicLanding";

export const metadata = {
  title: "Impilo — Zimbabwe's National Health Operating System",
  description:
    "How can Impilo help you today? Find care, get emergency help, access public services and progressively unlock protected health and professional work.",
};

/**
 * One Impilo entry point.
 *
 * Serves the need-first public living canvas at the canonical root `/`.
 * Guests and returning users can freely explore public services or enter protected work.
 */
export default function RootPage() {
  return <PublicLanding />;
}
