/**
 * LearningClassroomScreen — live Fundo classroom for the provider
 * (facilitator-capable) app.
 *
 * Stages: SESSION_INFO → IN_CLASS → AFTER
 *
 * Media rides the shared Impilo Live room flow (join → scoped token) through
 * AdaptiveSessionRoomNative. Classroom rendering note: the shared session room
 * has "stage" and "grid" layouts; on mobile a classroom is rendered with the
 * "grid" layout (all camera tiles as flex-wrap tiles) — an acceptable
 * classroom approximation on a phone-sized surface.
 *
 * Facilitator affordances: join as PRESENTER, attendance count line (from the
 * shared learning attendance endpoint), and an honest end-class note — leaving
 * on mobile exits this device only; the class itself is ended from the live
 * event host controls.
 */

import React, { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Button, Badge, ErrorState, colors } from "@impilo/mobile-design-system";
import { AdaptiveSessionRoomNative } from "@impilo/mobile-session";
import {
  fetchSessionAttendance,
  isJoinableLiveSession,
  joinLiveClassroom,
  type ClassroomMediaCredentials,
  type LearningSession,
} from "../../services/learningSessionsService";

const ATTENDANCE_REFRESH_MS = 30_000;

type ClassroomStage = "SESSION_INFO" | "IN_CLASS" | "AFTER";

export interface LearningClassroomScreenProps {
  session: LearningSession;
  onBack: () => void;
}

export function LearningClassroomScreen({ session, onBack }: LearningClassroomScreenProps) {
  const [stage, setStage] = useState<ClassroomStage>("SESSION_INFO");
  const [media, setMedia] = useState<ClassroomMediaCredentials | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [asFacilitator, setAsFacilitator] = useState(true);
  const [audioOnly, setAudioOnly] = useState(false);
  const [micMuted, setMicMuted] = useState(false);
  const [attendanceCount, setAttendanceCount] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<Error | null>(null);

  const joinable = isJoinableLiveSession(session);

  const refreshAttendance = useCallback(async () => {
    try {
      const attendance = await fetchSessionAttendance(session.id);
      setAttendanceCount(attendance.count);
    } catch {
      // Attendance is a best-effort facilitator affordance; keep the last value.
    }
  }, [session.id]);

  // Attendance count line: on entry and periodically while in class.
  useEffect(() => {
    if (stage !== "SESSION_INFO" && stage !== "IN_CLASS") return;
    void refreshAttendance();
    if (stage !== "IN_CLASS") return;
    const interval = setInterval(() => {
      void refreshAttendance();
    }, ATTENDANCE_REFRESH_MS);
    return () => clearInterval(interval);
  }, [stage, refreshAttendance]);

  const handleJoin = useCallback(async () => {
    if (!session.liveEventId) return;
    setIsJoining(true);
    setError(null);
    try {
      const credentials = await joinLiveClassroom(session.liveEventId, { asFacilitator });
      setMedia(credentials);
      setStage("IN_CLASS");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsJoining(false);
    }
  }, [session.liveEventId, asFacilitator]);

  const handleLeave = useCallback(() => {
    setMedia(null);
    setStage("AFTER");
  }, []);

  return (
    <Screen>
      <Header
        title="Live Classroom"
        leftElement={
          <Button title={"← Back"} variant="ghost" size="sm" onPress={onBack} testID="provider-classroom-back" />
        }
      />
      <ScrollView testID="provider-classroom-screen" style={styles.scroll} contentContainerStyle={styles.container}>
        {error ? <ErrorState title="Classroom error" message={error.message} onRetry={() => setError(null)} /> : null}

        {stage === "SESSION_INFO" ? (
          <Card>
            <CardHeader title="Session" />
            <CardBody>
              <View testID="provider-classroom-info" style={styles.section}>
                <View style={styles.badgeRow}>
                  <Text style={styles.title}>{session.title}</Text>
                  <Badge variant={session.sessionMode === "LIVE" ? "primary" : "secondary"}>
                    {session.sessionMode}
                  </Badge>
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
                <Text style={styles.meta} testID="provider-classroom-attendance">
                  {attendanceCount === null
                    ? "Attendance: not available yet"
                    : `Attendance recorded: ${attendanceCount}`}
                </Text>
                {joinable ? (
                  <View style={styles.section}>
                    <Button
                      title={asFacilitator ? "Joining as: Facilitator" : "Joining as: Attendee"}
                      variant="outline"
                      onPress={() => setAsFacilitator((prev) => !prev)}
                      testID="provider-classroom-role-toggle"
                    />
                    <Button
                      title={isJoining ? "Joining classroom..." : "Join live classroom"}
                      variant="primary"
                      size="lg"
                      onPress={handleJoin}
                      disabled={isJoining}
                      testID="provider-classroom-join"
                    />
                  </View>
                ) : (
                  <Text style={styles.meta}>
                    This session is not live-joinable from mobile yet — it is either in-person or the live event has
                    not been scheduled.
                  </Text>
                )}
              </View>
            </CardBody>
          </Card>
        ) : null}

        {stage === "IN_CLASS" && media ? (
          <Card>
            <CardBody>
              <View testID="provider-classroom-in-class" style={styles.section}>
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
                <Text style={styles.meta} testID="provider-classroom-attendance-live">
                  {attendanceCount === null
                    ? "Attendance: not available yet"
                    : `Attendance recorded: ${attendanceCount}`}
                </Text>
                <View style={styles.controlsRow}>
                  <Button
                    title={audioOnly ? "Audio only: on" : "Audio only: off"}
                    variant="outline"
                    onPress={() => setAudioOnly((prev) => !prev)}
                    testID="provider-classroom-audio-only-toggle"
                  />
                  <Button
                    title={micMuted ? "Unmute Mic" : "Mute Mic"}
                    variant="outline"
                    onPress={() => setMicMuted((prev) => !prev)}
                    testID="provider-classroom-mic-toggle"
                  />
                  <Button title="Leave class" variant="primary" onPress={handleLeave} testID="provider-classroom-leave" />
                </View>
                <Text style={styles.notice}>
                  Leaving exits this device only. Ending the class for everyone is done from the live event host
                  controls (web studio), which also closes attendance capture.
                </Text>
                {notice ? <Text style={styles.notice}>{notice}</Text> : null}
              </View>
            </CardBody>
          </Card>
        ) : null}

        {stage === "AFTER" ? (
          <Card>
            <CardHeader title="Class session closed on this device" />
            <CardBody>
              <View testID="provider-classroom-after" style={styles.section}>
                <Text style={styles.title}>{session.title}</Text>
                <Text style={styles.meta} testID="provider-classroom-attendance-after">
                  {attendanceCount === null
                    ? "Attendance was recorded for joined participants."
                    : `Attendance recorded for ${attendanceCount} participant(s).`}
                </Text>
                <Text style={styles.meta}>
                  Attendance was captured when participants joined. CPD and certificate outcomes are processed by
                  Fundo from the live event record.
                </Text>
                <Button title="Back to learning" variant="outline" onPress={onBack} testID="provider-classroom-done" />
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
  title: { fontSize: 15, fontWeight: "700", color: colors.gray[900] },
  sectionTitle: { fontSize: 13, fontWeight: "700", color: colors.gray[900], marginTop: 4 },
  meta: { fontSize: 13, color: colors.gray[700] },
  roomArea: { borderRadius: 12, overflow: "hidden" },
  controlsRow: { flexDirection: "row", gap: 8, justifyContent: "center", flexWrap: "wrap" },
  notice: { fontSize: 12, color: colors.gray[500], textAlign: "center" },
});
