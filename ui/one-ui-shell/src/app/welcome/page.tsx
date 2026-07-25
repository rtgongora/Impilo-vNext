import { PublicLanding } from "@/components/public/PublicLanding";

export const metadata = {
  title: "Impilo — Zimbabwe's National Health Operating System",
  description:
    "Find care, access public health services, get trusted information, manage your health or work securely across Zimbabwe's health system.",
};

/** Backward-compatible public entry. The canonical root renders the same living canvas. */
export default function WelcomePage() {
  return <PublicLanding />;
}
