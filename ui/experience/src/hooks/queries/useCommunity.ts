/**
 * Experience UI — Community Groups Query Hooks
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface CommunityGroup {
  id: string;
  type: "community-group";
  attributes: {
    name: string;
    description: string;
    groupType: string;
    category: string;
    memberCount: number;
    isPublic: boolean;
    status: string;
    [key: string]: unknown;
  };
}

export interface DiscussionPost {
  id: string;
  type: "discussion-post";
  attributes: {
    title: string;
    content: string;
    authorId: string;
    likesCount: number;
    commentsCount: number;
    pinned: boolean;
    createdAt: string;
    [key: string]: unknown;
  };
}

export function useCommunityGroups(category?: string) {
  return useQuery<ApiResponse<CommunityGroup[]>>({
    queryKey: ["community-groups", category],
    queryFn: () => {
      const params = category ? `?category=${encodeURIComponent(category)}` : "";
      return apiClient.get(`/internal/v1/community/groups${params}`);
    },
  });
}

export function useGroupPosts(groupId: string) {
  return useQuery<ApiResponse<DiscussionPost[]>>({
    queryKey: ["community-posts", groupId],
    queryFn: () => apiClient.get(`/internal/v1/community/groups/${groupId}/posts`),
    enabled: !!groupId,
  });
}

export function useJoinGroup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ groupId, memberId }: { groupId: string; memberId: string }) =>
      apiClient.post(`/internal/v1/community/groups/${groupId}/join`, { memberId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["community-groups"] });
    },
  });
}
