"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

/**
 * MEETING session data layer (W5) over the experience-bff `/internal/v1/khuluma/**` meeting
 * family. A meeting is a Khuluma MEETING conversation composed over an Impilo Live event whose
 * media room is an rtc-gateway MEETING-template session with a KNOCK lobby: join POSTs return
 * `status` READY (token minted) | WAITING (knocking — poll) | DENIED | UNAVAILABLE.
 */

const BASE = "/internal/v1/khuluma";

export type MeetingJoinStatus = "READY" | "WAITING" | "DENIED" | "UNAVAILABLE";

export interface MeetingJoinResponse {
  conversationId: string;
  eventId: string | null;
  status: MeetingJoinStatus;
  role: string | null;
  rtcSessionId: string | null;
  mediaAvailable: boolean;
  roomUrl: string | null;
  accessToken: string | null;
  provider: string | null;
  error: string | null;
}

export interface MeetingAdmission {
  actorId: string;
  actorType: string;
  displayName: string | null;
  role: string | null;
  status: "WAITING" | "ADMITTED" | "DENIED";
  requestedAt: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  deniedReason: string | null;
  joinedAt: string | null;
  leftAt: string | null;
}

export interface MeetingActionItem {
  actionItemId: string;
  conversationId: string;
  description: string;
  assigneeId: string | null;
  assigneeDisplayName: string | null;
  dueDate: string | null;
  status: "OPEN" | "IN_PROGRESS" | "DONE" | "CANCELLED";
  createdBy: string;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface MeetingDetail {
  conversationId: string;
  eventId: string | null;
  title: string | null;
  eventStatus: string | null;
  scheduledAt: string | null;
  endTime: string | null;
  hostId: string;
  cohostIds: string[];
  attendance: MeetingAdmission[];
}

export function meetingKeys() {
  return {
    join: (id: string) => ["meeting", "join", id] as const,
    detail: (id: string) => ["meeting", "detail", id] as const,
    lobby: (id: string) => ["meeting", "lobby", id] as const,
    actionItems: (id: string) => ["meeting", "action-items", id] as const,
  };
}

/**
 * Lobby-aware join. The POST is idempotent server-side (rtc-gateway lobby gate), so it is safe
 * to model as a query: while WAITING it re-knocks every 4s until a host admits (READY) or
 * denies. Realtime `meeting.admission.*` events can invalidate it for a snappier flip.
 */
export function useMeetingJoin(conversationId: string | null) {
  return useQuery<MeetingJoinResponse>({
    queryKey: meetingKeys().join(conversationId ?? "none"),
    queryFn: () => apiClient.post(`${BASE}/conversations/${conversationId}/meeting/join`, {}),
    enabled: !!conversationId,
    refetchInterval: (query) =>
      query.state.data?.status === "WAITING" ? 4000 : false,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}

export function useMeetingDetail(conversationId: string | null, opts?: { refetchInterval?: number | false }) {
  return useQuery<MeetingDetail>({
    queryKey: meetingKeys().detail(conversationId ?? "none"),
    queryFn: () => apiClient.get(`${BASE}/conversations/${conversationId}/meeting`),
    enabled: !!conversationId,
    refetchInterval: opts?.refetchInterval ?? false,
  });
}

/** Host/cohost admit queue + roster (403 for members — gate rendering on isModerator). */
export function useMeetingLobby(conversationId: string | null, enabled: boolean) {
  return useQuery<MeetingAdmission[]>({
    queryKey: meetingKeys().lobby(conversationId ?? "none"),
    queryFn: () => apiClient.get(`${BASE}/conversations/${conversationId}/meeting/lobby`),
    enabled: !!conversationId && enabled,
    refetchInterval: 4000, // liveness without a realtime socket
  });
}

export function useLobbyDecision(conversationId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ actorId, admit, reason }: { actorId: string; admit: boolean; reason?: string }) =>
      apiClient.post<MeetingAdmission>(
        `${BASE}/conversations/${conversationId}/meeting/lobby/${admit ? "admit" : "deny"}`,
        admit ? { actorId } : { actorId, reason },
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: meetingKeys().lobby(conversationId) });
      qc.invalidateQueries({ queryKey: meetingKeys().detail(conversationId) });
    },
  });
}

export function useCohostActions(conversationId: string) {
  const qc = useQueryClient();
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: meetingKeys().detail(conversationId) });
    void qc.invalidateQueries({ queryKey: ["khuluma", "conversation", conversationId] });
  };
  return {
    assign: (actorId: string) =>
      apiClient.post(`${BASE}/conversations/${conversationId}/meeting/cohosts`, { actorId }).then(invalidate),
    revoke: (actorId: string) =>
      apiClient.delete(`${BASE}/conversations/${conversationId}/meeting/cohosts/${actorId}`).then(invalidate),
  };
}

export function useRaiseHand(conversationId: string) {
  return useMutation({
    mutationFn: (raised: boolean) =>
      apiClient.post(`${BASE}/conversations/${conversationId}/meeting/hand`, { raised }),
  });
}

export function useSendReaction(conversationId: string) {
  return useMutation({
    mutationFn: (reaction: string) =>
      apiClient.post(`${BASE}/conversations/${conversationId}/meeting/reactions`, { reaction }),
  });
}

export function useMeetingActionItems(conversationId: string | null) {
  return useQuery<MeetingActionItem[]>({
    queryKey: meetingKeys().actionItems(conversationId ?? "none"),
    queryFn: () => apiClient.get(`${BASE}/conversations/${conversationId}/meeting/action-items`),
    enabled: !!conversationId,
  });
}

export function useActionItemMutations(conversationId: string) {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: meetingKeys().actionItems(conversationId) });
  const create = useMutation({
    mutationFn: (body: { description: string; assigneeId?: string; assigneeDisplayName?: string; dueDate?: string }) =>
      apiClient.post<MeetingActionItem>(`${BASE}/conversations/${conversationId}/meeting/action-items`, body),
    onSuccess: invalidate,
  });
  const update = useMutation({
    mutationFn: ({ actionItemId, ...body }: { actionItemId: string; status?: string; description?: string }) =>
      apiClient.patch<MeetingActionItem>(
        `${BASE}/conversations/${conversationId}/meeting/action-items/${actionItemId}`,
        body,
      ),
    onSuccess: invalidate,
  });
  return { create, update };
}

export interface MeetingInvite {
  token: string;
  expiresAt: string;
  role: string;
}

export function mintMeetingInvite(conversationId: string, role?: string, ttlSeconds?: number) {
  return apiClient.post<MeetingInvite>(
    `${BASE}/conversations/${conversationId}/meeting/invite-links`,
    { role, ttlSeconds },
  );
}

export function resolveMeetingInvite(token: string) {
  return apiClient.post<{ conversationId: string; eventId: string | null }>(
    `${BASE}/meetings/invites/resolve`,
    { token },
  );
}

/** Browser-side invite URL for a minted token — resolves through /meet/join. */
export function meetingInviteUrl(token: string): string {
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  return `${origin}/meet/join?token=${encodeURIComponent(token)}`;
}
