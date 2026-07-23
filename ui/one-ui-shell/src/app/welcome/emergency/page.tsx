import { PublicShell } from "@/components/public/PublicShell";
import { EmergencyExperience } from "@/components/public/emergency/EmergencyExperience";
import { EmergencyGuidanceCards } from "@/components/public/emergency/EmergencyGuidanceCards";
import { BloodDonationAppeals } from "@/components/public/emergency/BloodDonationAppeals";

export const metadata = {
  title: "Emergency help — Impilo",
  description:
    "Verified emergency numbers, Nompilo guided emergency triage, nearest emergency care and assistance callback — no account needed.",
};

/**
 * Public Emergency journey (G-CZO-02 / gateway doctrine §7). Reachable without
 * login, never gated. The interactive responder (EmergencyExperience) leads;
 * danger-sign cards and public-health guidance follow as scannable cards, not
 * text walls. Emergency numbers come from src/config/emergency.ts ONLY.
 *
 * Back/home continuity comes from the PublicShell back bar; the OS shell chrome
 * (taskbar, Nompilo command layer) is suppressed on this route even for
 * authenticated users (shell-visibility.ts) — the triage panel IS Nompilo here.
 */
export default function EmergencyPage() {
  return (
    <PublicShell>
      <EmergencyExperience />
      <EmergencyGuidanceCards />
      <BloodDonationAppeals />
    </PublicShell>
  );
}
