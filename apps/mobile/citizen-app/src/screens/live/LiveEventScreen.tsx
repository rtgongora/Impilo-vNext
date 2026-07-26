import React, { useMemo, useState } from "react";
import { View, Text, ScrollView, StyleSheet, Alert, Linking } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Screen, Header, Button, Badge, TextField, LoadingSpinner, ErrorState, colors } from "@impilo/mobile-design-system";
import {
  fetchEvent,
  joinEvent,
  listMyRegistrations,
  registerForEvent,
  submitFeedback,
  submitQuestion,
  isDonorMobilisationEvent,
} from "../../services/impiloLiveService";
import { appStore } from "../../stores/appStore";
import { EventDiscussionSection } from "./EventDiscussionSection";

interface Props {
  eventId: string;
  onBack: () => void;
}

export function LiveEventScreen({ eventId, onBack }: Props) {
  const queryClient = useQueryClient();
  const [question, setQuestion] = useState("");
  const [feedbackRating, setFeedbackRating] = useState("4");
  const [feedbackComments, setFeedbackComments] = useState("");

  const eventQuery = useQuery({
    queryKey: ["citizen-live-event", eventId],
    queryFn: () => fetchEvent(eventId),
    enabled: Boolean(eventId),
  });

  const registrationsQuery = useQuery({
    queryKey: ["citizen-live-registrations"],
    queryFn: () => listMyRegistrations(),
  });

  const registerMutation = useMutation({
    mutationFn: () => registerForEvent(eventId, { inviteSource: "MOBILE_EVENT_DETAIL" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["citizen-live-registrations"] }),
  });

  const joinMutation = useMutation({
    mutationFn: () => joinEvent(eventId),
    onSuccess: (session) => {
      if (session?.roomUrl) {
        Linking.openURL(session.roomUrl).catch(() => {
          Alert.alert("Join session", "Room link is ready but could not be opened on this device.");
        });
      } else {
        Alert.alert("Joined", "You are connected to the live session.");
      }
    },
    onError: () => Alert.alert("Join failed", "The live room is temporarily unavailable."),
  });

  const questionMutation = useMutation({
    mutationFn: () => submitQuestion(eventId, question.trim()),
    onSuccess: () => {
      setQuestion("");
      Alert.alert("Question submitted", "Your question was sent to the host.");
    },
    onError: () => Alert.alert("Could not submit", "Q&A is unavailable for this session."),
  });

  const feedbackMutation = useMutation({
    mutationFn: () =>
      submitFeedback(eventId, {
        rating: Number(feedbackRating) || undefined,
        comments: feedbackComments.trim() || undefined,
        helpful: true,
      }),
    onSuccess: () => Alert.alert("Thank you", "Your feedback was recorded."),
    onError: () => Alert.alert("Could not submit", "Feedback is unavailable right now."),
  });

  const event = eventQuery.data;
  const isRegistered = useMemo(
    () => (registrationsQuery.data ?? []).some((r) => r.eventId === eventId),
    [registrationsQuery.data, eventId],
  );

  const isLive = event?.status === "LIVE";
  const isEnded = event?.status === "ENDED";
  const showMadiLink = event ? isDonorMobilisationEvent(event) : false;

  if (eventQuery.isLoading) {
    return (
      <Screen>
        <Header title="Live Event" onBack={onBack} />
        <LoadingSpinner label="Loading event…" />
      </Screen>
    );
  }

  if (eventQuery.isError || !event) {
    return (
      <Screen>
        <Header title="Live Event" onBack={onBack} />
        <ErrorState message="Event not found." onRetry={() => eventQuery.refetch()} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Header title={event.title} onBack={onBack} />
      <ScrollView style={styles.container} contentContainerStyle={styles.content}>
        <View style={styles.hero}>
          <View style={styles.badges}>
            <Badge variant={isLive ? "success" : isEnded ? "outline" : "warning"}>{event.status}</Badge>
            <Badge variant="outline">{event.eventType.replace(/_/g, " ")}</Badge>
          </View>
          {event.description ? <Text style={styles.description}>{event.description}</Text> : null}
          <Text style={styles.meta}>
            {event.startTime ? new Date(event.startTime).toLocaleString() : "Schedule TBC"}
            {event.facilityId ? ` · ${event.facilityId}` : ""}
          </Text>
        </View>

        <View style={styles.actions}>
          {isLive || isRegistered ? (
            <Button
              title={joinMutation.isPending ? "Joining…" : isLive ? "Join live" : "Enter room"}
              variant="primary"
              onPress={() => joinMutation.mutate()}
              disabled={joinMutation.isPending}
              testID="live-join-button"
            />
          ) : !isEnded ? (
            <Button
              title={registerMutation.isPending ? "Registering…" : "Register"}
              variant="primary"
              onPress={() => registerMutation.mutate()}
              disabled={registerMutation.isPending}
              testID="live-register-button"
            />
          ) : null}
        </View>

        {showMadiLink ? (
          <View style={styles.madiCard} testID="live-madi-followup">
            <View style={styles.madiHeader}>
              <Ionicons name="heart" size={20} color={colors.ui.error.main} />
              <Text style={styles.madiTitle}>Blood donation follow-up</Text>
            </View>
            <Text style={styles.madiDesc}>
              This session is linked to MADI donor mobilisation. Register as a donor or find a nearby drive after the event.
            </Text>
            <Button
              title="Open Blood Donor (MADI)"
              variant="outline"
              onPress={() => appStore.getState().setPersonalSectionRequest("madi-donor")}
            />
          </View>
        ) : null}

        {event.qnaEnabled !== false && (isLive || isRegistered) ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Ask a question</Text>
            <TextField
              label="Your question"
              value={question}
              onChange={setQuestion}
              placeholder="Type your question for the host…"
              testID="live-question-input"
            />
            <Button
              title={questionMutation.isPending ? "Sending…" : "Submit question"}
              variant="outline"
              onPress={() => questionMutation.mutate()}
              disabled={!question.trim() || questionMutation.isPending}
            />
          </View>
        ) : null}

        {isEnded || isLive ? (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Session feedback</Text>
            <TextField
              label="Rating (1–5)"
              value={feedbackRating}
              onChange={setFeedbackRating}
              keyboardType="numeric"
              testID="live-feedback-rating"
            />
            <TextField
              label="Comments"
              value={feedbackComments}
              onChange={setFeedbackComments}
              placeholder="What was helpful?"
              testID="live-feedback-comments"
            />
            <Button
              title={feedbackMutation.isPending ? "Sending…" : "Submit feedback"}
              variant="ghost"
              onPress={() => feedbackMutation.mutate()}
              disabled={feedbackMutation.isPending}
            />
          </View>
        ) : null}

        <EventDiscussionSection eventId={eventId} title={eventQuery.data?.title} />
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.gray[50] },
  content: { padding: 16, gap: 16, paddingBottom: 32 },
  hero: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: colors.gray[200], gap: 8 },
  badges: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  description: { fontSize: 14, color: colors.gray[700], lineHeight: 20 },
  meta: { fontSize: 12, color: colors.gray[500] },
  actions: { gap: 8 },
  madiCard: {
    backgroundColor: "#FEF2F2",
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: "#FECACA",
    gap: 8,
  },
  madiHeader: { flexDirection: "row", alignItems: "center", gap: 8 },
  madiTitle: { fontSize: 15, fontWeight: "600", color: colors.ui.error.text },
  madiDesc: { fontSize: 13, color: "#7F1D1D", lineHeight: 18 },
  section: { backgroundColor: "#FFFFFF", borderRadius: 12, padding: 14, borderWidth: 1, borderColor: colors.gray[200], gap: 10 },
  sectionTitle: { fontSize: 14, fontWeight: "600", color: colors.gray[900] },
});
