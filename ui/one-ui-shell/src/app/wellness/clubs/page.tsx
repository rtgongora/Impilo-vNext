import { redirect } from "next/navigation";

/**
 * Clubs are SUPERSEDED by the wellness-social groups layer (simba_group, group_kind='CLUB').
 * This route now permanently repoints to /wellness/social/groups so there is no dead nav and no
 * parallel ownership — the sovereign social layer is the single source of truth.
 */
export default function ClubsPage() {
  redirect("/wellness/social/groups");
}
