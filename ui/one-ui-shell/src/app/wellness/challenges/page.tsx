import { redirect } from "next/navigation";

/**
 * Challenges are REUSED & EXTENDED by the wellness-social layer (share-to-feed, leaderboard, campaign
 * aggregate). This route now permanently repoints to /wellness/social/challenges so there is a single
 * canonical challenges surface and no dead nav.
 */
export default function ChallengesPage() {
  redirect("/wellness/social/challenges");
}
