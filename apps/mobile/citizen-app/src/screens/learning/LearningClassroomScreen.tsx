/**
 * LearningClassroomScreen — live Fundo classroom for the citizen learner.
 *
 * Stages: SESSION_INFO → IN_CLASS → AFTER
 *
 * Media rides the shared Impilo Live room flow (join → scoped token) through
 * AdaptiveSessionRoomNative. Classroom rendering note: the shared session room
 * has "stage" and "grid" layouts; on mobile a classroom is rendered with the
 * "grid" layout (all camera tiles as flex-wrap tiles) — an acceptable
 * classroom approximation on a phone-sized surface.
 */

import React, { useCallback, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import {
  Screen,
  Header,
  Card,
  CardHeader,
  CardBody,
  Button,
  Badge,
  ErrorState,
} from "@impilo/mobile-design-system";
import { AdaptiveSessionRoomNative } from "@impilo/mobile-session";
import {
  joinLiveClassroom,
  isJoinableLiveSession,
  type ClassroomMediaCredentials,
  type LearningSession,
} from "../../services/learningSessionsService";

type ClassroomStage = "SESSION_INFO" | "IN_CLASS" | "AFTER";

export interface LearningClassroomScreenProps {
  session: LearningSession;
  onBack: () => void;
}

export function LearningClassroomScreen({ session, onBack }: LearningClassroomScreenProps) {
  const [stage, setStage] = useState<ClassroomStage>("SESSION_INFO");
  const [media, setMedia] = useState<ClassroomMediaCredentials | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [audioOnly, setAudioOnly] = useState(false);
  const [micMuted, setMicMuted] = useState(true);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<Error | null>(null);

  const joinable = isJoinableLiveSession(session);

  const handleJoin = useCallback(async () => {
    if (!session.liveEventId) return;
    setIsJoining(true);
    setError(null);
    try {
      const credentials = await joinLiveClassroom(session.liveEventId);
      setMedia(credentials);
      setStage("IN_CLASS");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsJoining(false);
    }
  }, [session.liveEventId]);

  const handleLeave = useCallback(() => {
    setMedia(null);
    setStage("AFTER");
  }, []);

  return (
    <Screen>
      <Header
        title="Live Classroom"
        leftElement={
          <Button title={"← Back"} variant="ghost" size="sm" onPress={onBack} testID="learning-classroom-back" />
        }
      />
      <ScrollView testID="learning-classroom-screen" style={styles.scroll} contentContainerStyle={styles.container}>
        {error ? <ErrorState title="Classroom error" message={error.message} onRetry={() => setError(null)} /> : null}

        {stage === "SESSION_INFO" ? (
          <Card>
            <CardHeader title="Session" />
            <CardBody>
              <View testID="learning-classroom-info" style={styles.section}>
                <View style={styles.badgeRow}>
                  <Text style={styles.title}>{session.title}</Text>
                  <Badge variant={session.sessionMode === "LIVE" ? "default" : "outline"}>{session.sessionMode}</Badge>
                </View>
                <Text style={styles.meta}>
                  {session.startsAt
                    ? `Starts: ${new Date(session.startsAt).toLocaleString()}`
                    : "Schedule to be confirmed"}
                </Text>
                {session.endsAt ? (
                  <Text style={styles.meta}>{`Ends: ${new Date(session.endsAt).toLocaleString()}`}</Text>
                ) : null}
                {session.facilitator ? (
                  <Text style={styles.meta}>{`Facilitator: ${session.facilitator}`}</Text>
                ) : null}
                {session.objectives.length > 0 ? (
                  <View style={styles.section}>
                    <Text style={styles.sectionTitle}>Objectives</Text>
                    {session.objectives.map((objective) => (
                      <Text key={objective} style={styles.meta}>{`• ${objective}`}</Text>
                    ))}
                  </View>
                ) : null}
                {joinable ? (
                  <Button
                    title={isJoining ? "Joining classroom..." : "Join live classroom"}
                    variant="primary"
                    size="lg"
                    onPress={handleJoin}
                    disabled={isJoining}
                    testID="learning-classroom-join"
                  />
                ) : (
                  <Text style={styles.meta}>
                    This session is not live-joinable from mobile yet — attend as scheduled, or check back when the
                    session goes live.
                  </Text>
                )}
              </View>
            </CardBody>
          </Card>
        ) : null}

        {stage === "IN_CLASS" && media ? (
          <Card>
            <CardBody>
              <View testID="learning-classroom-in-class" style={styles.section}>
                <View style={styles.roomArea}>
                  {/* Classroom ≈ grid on mobile: all participant tiles, flex-wrapped. */}
                  <AdaptiveSessionRoomNative
                    serverUrl={media.serverUrl}
                    token={media.token}
                    layout="grid"
                    audioOnly={audioOnly}
                    videoEnabled={!audioOnly}
                    micMuted={micMuted}
                    showReconnectBanner
                    onConnected={() => setNotice("Connected to the governed classroom through Impilo RTC Gateway.")}
                    onDisconnected={() => setNotice("Classroom media disconnected. You can rejoin from the session page.")}
                    onError={(message) => setNotice(message)}
                    unavailableMessage="Live classroom media is blocked until Impilo Live returns a governed server URL and scoped token."
                  />
                </View>
                <View style={styles.controlsRow}>
                  <Button
                    title={audioOnly ? "Audio only: on" : "Audio only: off"}
                    variant="outline"
                    onPress={() => setAudioOnly((prev) => !prev)}
                    testID="learning-classroom-audio-only-toggle"
                  />
                  <Button
                    title={micMuted ? "Unmute Mic" : "Mute Mic"}
                    variant="outline"
                    onPress={() => setMicMuted((prev) => !prev)}
                    testID="learning-classroom-mic-toggle"
                  />
                  <Button title="Leave class" variant="primary" onPress={handleLeave} testID="learning-classroom-leave" />
                </View>
                {notice ? <Text style={styles.notice}>{notice}</Text> : null}
              </View>
            </CardBody>
          </Card>
        ) : null}

        {stage === "AFTER" ? (
          <Card>
            <CardHeader title="Thanks for attending" />
            <CardBody>
              <View testID="learning-classroom-after" style={styles.section}>
                <Text style={styles.title}>{session.title}</Text>
                <Text style={styles.meta}>
                  Your attendance was recorded when you joined this live classroom. Course progress and any
                  CPD/certificate outcomes are processed by Fundo and will appear in your learning record.
                </Text>
                <Button title="Back to learning" variant="outline" onPress={onBack} testID="learning-classroom-done" />
              </View>
            </CardBody>
          </Card>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  container: { padding: 16, gap: 16 },
  section: { gap: 8 },
  badgeRow: { flexDirection: "row", gap: 8, alignItems: "center", flexWrap: "wrap" },
  title: { fontSize: 15, fontWeight: "700", color: "#111827" },
  sectionTitle: { fontSize: 13, fontWeight: "700", color: "#111827", marginTop: 4 },
  meta: { fontSize: 13, color: "#374151" },
  roomArea: { borderRadius: 12, overflow: "hidden" },
  controlsRow: { flexDirection: "row", gap: 8, justifyContent: "center", flexWrap: "wrap" },
  notice: { fontSize: 12, color: "#6B7280", textAlign: "center" },
});
