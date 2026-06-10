import { useQuery } from "@tanstack/react-query";
import { getBootstrapStatus } from "@/lib/admin-governance/api/bootstrapApi";

export function useBootstrapStatus() {
  return useQuery({
    queryKey: ["bootstrap", "status"],
    queryFn: getBootstrapStatus,
    staleTime: 10_000,
    retry: false,
  });
}
