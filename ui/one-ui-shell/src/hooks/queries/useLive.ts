/**
 * Impilo Live — React Query hooks
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  liveApi,
  type CreateLiveEventPayload,
  type LiveChatPayload,
  type LiveComposerAssistPayload,
  type LiveContextType,
  type LiveJoinRoomPayload,
  type LiveModerationPayload,
  type LivePollPayload,
  type LivePollResponsePayload,
  type LiveQuestionPayload,
  type LiveRegisterPayload,
  type LiveResourcePayload,
  type LiveRoomTokenPayload,
} from "@/lib/live";

export const liveQueryKeys = {
  all: ["live"] as const,
  events: (status?: string) => [...liveQueryKeys.all, "events", status ?? "all"] as const,
  event: (eventId: string) => [...liveQueryKeys.all, "event", eventId] as const,
  discover: (contextType: string) => [...liveQueryKeys.all, "discover", contextType] as const,
  discoverFacility: (facilityId: string) =>
    [...liveQueryKeys.all, "discover-facility", facilityId] as const,
  discoverRole: (userId: string, role: string) =>
    [...liveQueryKeys.all, "discover-role", userId, role] as const,
  registrationsEvent: (eventId: string) =>
    [...liveQueryKeys.all, "registrations", "event", eventId] as const,
  registrationsParticipant: (participantId: string) =>
    [...liveQueryKeys.all, "registrations", "participant", participantId] as const,
  questions: (eventId: string) => [...liveQueryKeys.all, "questions", eventId] as const,
  chat: (eventId: string) => [...liveQueryKeys.all, "chat", eventId] as const,
  polls: (eventId: string) => [...liveQueryKeys.all, "polls", eventId] as const,
  resources: (eventId: string) => [...liveQueryKeys.all, "resources", eventId] as const,
  attendance: (eventId: string) => [...liveQueryKeys.all, "attendance", eventId] as const,
  attendanceParticipant: (eventId: string, participantId: string) =>
    [...liveQueryKeys.all, "attendance", eventId, participantId] as const,
  certificates: (eventId: string) => [...liveQueryKeys.all, "certificates", eventId] as const,
  analytics: (eventId: string) => [...liveQueryKeys.all, "analytics", eventId] as const,
  roomToken: (eventId: string, participantId: string) =>
    [...liveQueryKeys.all, "room-token", eventId, participantId] as const,
  replay: (eventId: string) => [...liveQueryKeys.all, "replay", eventId] as const,
  mediaHealth: (eventId: string) => [...liveQueryKeys.all, "media-health", eventId] as const,
};

function invalidateLive(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: liveQueryKeys.all });
}

export function useLiveParticipant() {
  const user = useAuthStore((s) => s.user);
  const participantId = user?.providerId ?? user?.id ?? "";
  const participantType = user?.providerActivated ? "PROVIDER" : "CITIZEN";
  const role = user?.providerActivated ? "PRESENTER" : "ATTENDEE";
  return { user, participantId, participantType, role };
}

// ---- Events ---------------------------------------------------------------

export function useLiveEvents(status?: string) {
  return useQuery({
    queryKey: liveQueryKeys.events(status),
    queryFn: () => liveApi.listEvents(status),
    staleTime: 15_000,
  });
}

export function useLiveEvent(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.event(eventId),
    queryFn: () => liveApi.getEvent(eventId),
    enabled: Boolean(eventId),
    staleTime: 10_000,
  });
}

export function useCreateLiveEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateLiveEventPayload) => liveApi.createEvent(body),
    onSuccess: () => invalidateLive(qc),
  });
}

export function useScheduleLiveEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => liveApi.scheduleEvent(eventId),
    onSuccess: (_data, eventId) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
      invalidateLive(qc);
    },
  });
}

export function useCancelLiveEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => liveApi.cancelEvent(eventId),
    onSuccess: (_data, eventId) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
      invalidateLive(qc);
    },
  });
}

// ---- Discovery ------------------------------------------------------------

export function useLiveDiscover(contextType: LiveContextType = "CITIZEN") {
  return useQuery({
    queryKey: liveQueryKeys.discover(contextType),
    queryFn: () => liveApi.discoverByContext(contextType),
    staleTime: 20_000,
  });
}

export function useLiveDiscoverByFacility(facilityId?: string) {
  return useQuery({
    queryKey: liveQueryKeys.discoverFacility(facilityId ?? ""),
    queryFn: () => liveApi.discoverByFacility(facilityId!),
    enabled: Boolean(facilityId),
    staleTime: 20_000,
  });
}

// ---- Registrations --------------------------------------------------------

export function useLiveRegistrationsForEvent(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.registrationsEvent(eventId),
    queryFn: () => liveApi.listRegistrationsForEvent(eventId),
    enabled: Boolean(eventId),
  });
}

export function useLiveMyRegistrations() {
  const { participantId } = useLiveParticipant();
  return useQuery({
    queryKey: liveQueryKeys.registrationsParticipant(participantId),
    queryFn: () => liveApi.listRegistrationsForParticipant(participantId),
    enabled: Boolean(participantId),
    staleTime: 15_000,
  });
}

export function useLiveRegister() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, body }: { eventId: string; body: LiveRegisterPayload }) =>
      liveApi.register(eventId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.registrationsEvent(eventId) });
      invalidateLive(qc);
    },
  });
}

export function useLiveSaveBookmark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      participantId,
      saved,
    }: {
      eventId: string;
      participantId: string;
      saved: boolean;
    }) => liveApi.saveBookmark(eventId, participantId, saved),
    onSuccess: () => invalidateLive(qc),
  });
}

// ---- Room -----------------------------------------------------------------

export function useLiveJoinRoom() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, body }: { eventId: string; body: LiveJoinRoomPayload }) =>
      liveApi.joinRoom(eventId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
    },
  });
}

export function useLiveRoomToken(eventId: string, enabled = true) {
  const { participantId, role } = useLiveParticipant();
  const body: LiveRoomTokenPayload = { participantId, role };
  return useQuery({
    queryKey: liveQueryKeys.roomToken(eventId, participantId),
    queryFn: () => liveApi.getRoomToken(eventId, body),
    enabled: Boolean(eventId) && Boolean(participantId) && enabled,
    // A media token must be STABLE for the page lifetime: every refetch mints
    // a new JWT and a changed token prop makes LiveKitRoom disconnect/reconnect
    // (observed as a 30s join/leave churn loop). Explicit refresh flows own
    // renewal; TTLs are template-driven and outlive any session page.
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: 1,
  });
}

export function useLiveStartRoom() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => liveApi.startRoom(eventId),
    onSuccess: (_data, eventId) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
    },
  });
}

export function useLiveEndRoom() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => liveApi.endRoom(eventId),
    onSuccess: (_data, eventId) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
      qc.invalidateQueries({ queryKey: liveQueryKeys.replay(eventId) });
    },
  });
}

export function useLiveReplay(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.replay(eventId),
    queryFn: () => liveApi.getReplay(eventId),
    enabled: Boolean(eventId),
    staleTime: 15_000,
    refetchInterval: (query) =>
      query.state.data?.status === "PROCESSING_REPLAY" ? 5_000 : false,
  });
}

export function useLiveMediaHealth(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.mediaHealth(eventId),
    queryFn: () => liveApi.getMediaHealth(eventId),
    enabled: Boolean(eventId),
    staleTime: 30_000,
  });
}

export function useLiveProcessReplay() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => liveApi.processReplay(eventId),
    onSuccess: (_data, eventId) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.replay(eventId) });
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
    },
  });
}

export function useLivePublishReplay() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => liveApi.publishReplay(eventId),
    onSuccess: (_data, eventId) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.replay(eventId) });
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
    },
  });
}

// ---- Interactions ---------------------------------------------------------

export function useLiveQuestions(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.questions(eventId),
    queryFn: () => liveApi.listQuestions(eventId),
    enabled: Boolean(eventId),
    refetchInterval: 8_000,
  });
}

export function useLiveChat(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.chat(eventId),
    queryFn: () => liveApi.listChat(eventId),
    enabled: Boolean(eventId),
    refetchInterval: 5_000,
  });
}

export function useLivePolls(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.polls(eventId),
    queryFn: () => liveApi.listPolls(eventId),
    enabled: Boolean(eventId),
    refetchInterval: 10_000,
  });
}

export function useLiveResources(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.resources(eventId),
    queryFn: () => liveApi.listResources(eventId),
    enabled: Boolean(eventId),
    staleTime: 30_000,
  });
}

export function useLiveSubmitQuestion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, body }: { eventId: string; body: LiveQuestionPayload }) =>
      liveApi.submitQuestion(eventId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.questions(eventId) });
    },
  });
}

export function useLivePostChat() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, body }: { eventId: string; body: LiveChatPayload }) =>
      liveApi.postChat(eventId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.chat(eventId) });
    },
  });
}

export function useLiveRespondPoll() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      pollId,
      body,
    }: {
      eventId: string;
      pollId: string;
      body: LivePollResponsePayload;
    }) => liveApi.respondPoll(eventId, pollId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.polls(eventId) });
    },
  });
}

export function useLiveCreatePoll() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, body }: { eventId: string; body: LivePollPayload }) =>
      liveApi.createPoll(eventId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.polls(eventId) });
    },
  });
}

export function useLiveActivatePoll() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, pollId }: { eventId: string; pollId: string }) =>
      liveApi.activatePoll(eventId, pollId),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.polls(eventId) });
    },
  });
}

// ---- Moderation -----------------------------------------------------------

export function useLiveModerateChat() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      messageId,
      body,
    }: {
      eventId: string;
      messageId: string;
      body: LiveModerationPayload;
    }) => liveApi.moderateChat(eventId, messageId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.chat(eventId) });
    },
  });
}

export function useLiveModerateQuestion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      questionId,
      body,
    }: {
      eventId: string;
      questionId: string;
      body: LiveModerationPayload;
    }) => liveApi.moderateQuestion(eventId, questionId, body),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.questions(eventId) });
    },
  });
}

export function useLiveSetChatEnabled() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, enabled }: { eventId: string; enabled: boolean }) =>
      liveApi.setChatEnabled(eventId, enabled),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
    },
  });
}

export function useLiveSetQnaEnabled() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, enabled }: { eventId: string; enabled: boolean }) =>
      liveApi.setQnaEnabled(eventId, enabled),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.event(eventId) });
    },
  });
}

// ---- Attendance -----------------------------------------------------------

export function useLiveAttendance(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.attendance(eventId),
    queryFn: () => liveApi.listAttendance(eventId),
    enabled: Boolean(eventId),
  });
}

export function useLiveMyAttendance(eventId: string) {
  const { participantId } = useLiveParticipant();
  return useQuery({
    queryKey: liveQueryKeys.attendanceParticipant(eventId, participantId),
    queryFn: () => liveApi.getAttendance(eventId, participantId),
    enabled: Boolean(eventId) && Boolean(participantId),
  });
}

export function useLiveAttendanceLeave() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ eventId, participantId }: { eventId: string; participantId: string }) =>
      liveApi.attendanceLeave(eventId, participantId),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.attendance(eventId) });
    },
  });
}

export function useLiveTrackMinutes() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      participantId,
      liveMinutes,
      replayMinutes,
    }: {
      eventId: string;
      participantId: string;
      liveMinutes: number;
      replayMinutes: number;
    }) => liveApi.trackAttendanceMinutes(eventId, participantId, liveMinutes, replayMinutes),
    onSuccess: (_data, { eventId, participantId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.attendanceParticipant(eventId, participantId) });
    },
  });
}

// ---- Certificates ---------------------------------------------------------

export function useLiveCertificates(eventId?: string) {
  return useQuery({
    queryKey: liveQueryKeys.certificates(eventId ?? "all"),
    queryFn: () => liveApi.listCertificates(eventId!),
    enabled: Boolean(eventId),
  });
}

export function useLiveIssueCertificate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      eventId,
      participantId,
      type,
    }: {
      eventId: string;
      participantId: string;
      type: "attendance" | "cpd";
    }) =>
      type === "cpd"
        ? liveApi.issueCpdCertificate(eventId, participantId)
        : liveApi.issueAttendanceCertificate(eventId, participantId),
    onSuccess: (_data, { eventId }) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.certificates(eventId) });
    },
  });
}

// ---- Analytics ----------------------------------------------------------

export function useLiveAnalytics(eventId: string) {
  return useQuery({
    queryKey: liveQueryKeys.analytics(eventId),
    queryFn: () => liveApi.getAnalyticsDashboard(eventId),
    enabled: Boolean(eventId),
    refetchInterval: 20_000,
  });
}

export function useLiveCaptureAnalyticsSnapshot() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (eventId: string) => liveApi.captureAnalyticsSnapshot(eventId),
    onSuccess: (_data, eventId) => {
      qc.invalidateQueries({ queryKey: liveQueryKeys.analytics(eventId) });
    },
  });
}

// ---- Composer assist ----------------------------------------------------

export function useLiveComposerAssist() {
  return useMutation({
    mutationFn: (body: LiveComposerAssistPayload) => liveApi.composerAssist(body),
  });
}
