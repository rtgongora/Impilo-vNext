/**
 * The regulatory organisations, served from org-registry (NCZ-W1B).
 *
 * This replaces the shell's private copy of the nine councils. That copy was the third in the
 * estate — after varapi V028 and org-registry V007 — and the only one with no way to notice when it
 * drifted: a council renamed, added or retired in the registry would have left the interface
 * confidently displaying the old name, with nothing failing.
 *
 * The trade is a loading state where there was once a synchronous lookup. That is the right trade:
 * a name that is briefly absent is better than a name that is quietly wrong.
 */

import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { apiClient } from "@/lib/api-client";

export interface RegulatoryOrganisation {
  id: string;
  code: string;
  name: string;
  orgType: string;
  /** Derived from orgType in the BFF, so an authority cannot be mislabelled by a curated list. */
  kind: "authority" | "council";
}

interface RegulatoryOrganisationsResponse {
  organizations: RegulatoryOrganisation[];
}

export function useRegulatoryOrganisations() {
  const query = useQuery<RegulatoryOrganisationsResponse>({
    queryKey: ["regulatory-organisations"],
    // The registry's regulator list changes on the timescale of legislation, not of a session.
    staleTime: 15 * 60 * 1000,
    queryFn: async () =>
      apiClient.get<RegulatoryOrganisationsResponse>("/api/v1/regulatory/organizations"),
  });

  const organisations = useMemo(() => query.data?.organizations ?? [], [query.data]);
  const byId = useMemo(() => {
    const index = new Map<string, RegulatoryOrganisation>();
    organisations.forEach((org) => index.set(org.id.toLowerCase(), org));
    return index;
  }, [organisations]);

  return {
    ...query,
    organisations,
    councils: useMemo(() => organisations.filter((o) => o.kind === "council"), [organisations]),
    authority: useMemo(() => organisations.find((o) => o.kind === "authority"), [organisations]),
    /** Undefined while loading, and undefined for an id the registry does not know. */
    organisation: (id: string | null | undefined) =>
      id ? byId.get(id.toLowerCase()) : undefined,
  };
}
