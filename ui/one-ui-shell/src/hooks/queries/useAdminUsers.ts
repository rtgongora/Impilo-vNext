/**
 * Experience UI — Admin Users Query Hooks
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface AdminUserResource {
  id: string;
  type: "admin_user";
  attributes: {
    email: string;
    displayName: string;
    role: string;
    status: string;
    [key: string]: unknown;
  };
}

interface AdminUsersParams {
  search?: string;
  role?: string;
}

type AdminUsersResponse = ApiResponse<AdminUserResource[]>;
type AdminUserResponse = ApiResponse<AdminUserResource>;

export function useAdminUsers(params?: AdminUsersParams) {
  return useQuery<AdminUsersResponse>({
    queryKey: ["admin-users", params],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params?.search) searchParams.set("search", params.search);
      if (params?.role) searchParams.set("role", params.role);

      const qs = searchParams.toString();
      const path = `/internal/v1/admin/users${qs ? `?${qs}` : ""}`;
      return apiClient.get<AdminUsersResponse>(path);
    },
  });
}

export function useAdminUser(id: string) {
  return useQuery<AdminUserResponse>({
    queryKey: ["admin-users", id],
    queryFn: () => apiClient.get<AdminUserResponse>(`/internal/v1/admin/users/${id}`),
    enabled: !!id,
  });
}
