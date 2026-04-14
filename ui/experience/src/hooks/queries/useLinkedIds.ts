/**
 * Post-login Identity Resolution — Health OS §5–§6
 *
 * After login, queries the BFF to discover which IDs (Provider ID,
 * Staff ID, Caregiver ID) are linked to the person's Health ID.
 * This drives provider activation banners and shell adaptation.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

export interface LinkedIdsAttributes {
  providerId?: string;
  providerStatus?: string;
  licenceValid?: boolean;
  staffId?: string;
  facilityId?: string;
  caregiverId?: string;
}

export interface LinkedIdsResource {
  id: string;
  type: "linked-ids";
  attributes: LinkedIdsAttributes;
}

type LinkedIdsResponse = ApiResponse<LinkedIdsResource>;

export function useLinkedIds() {
  const { user, isAuthenticated, setLinkedIds } = useAuthStore();

  return useQuery<LinkedIdsResponse>({
    queryKey: ["linked-ids", user?.id],
    queryFn: async () => {
      const res = await apiClient.get<LinkedIdsResponse>("/internal/v1/identity/linked-ids");
      // Hydrate the auth store with discovered linked IDs
      const attrs = res?.data?.attributes;
      if (attrs) {
        setLinkedIds({
          providerId: attrs.providerId,
          staffId: attrs.staffId,
          caregiverId: attrs.caregiverId,
        });
      }
      return res;
    },
    enabled: isAuthenticated && !!user?.id,
    staleTime: 5 * 60 * 1000, // 5 minutes
    refetchOnWindowFocus: false,
  });
}
