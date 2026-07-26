/**
 * TelehealthSessionScreen — Active teleconsult session with a governed
 * waiting-room stage (device check → ask to join → provider admits) before
 * the call, and a post-consult stage after it ends.
 *
 * Stages: DETAILS → WAITING_ROOM → IN_CALL → POST_CONSULT
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { Screen, Header, Card, CardHeader, CardBody, Button, Badge, TextField, ErrorState, colors } from "@impilo/mobile-design-system";
import { AdaptiveSessionRoomNative, PreJoinNative, type PreJoinChoices } from "@impilo/mobile-session";
import {
  joinSession,
  endSession,
  withdrawTelehealthConsent,
  fetchSession,
  requestSessionMediaToken,
  refreshSessionMediaToken,
  type MediaTokenRequest,
} from "../../services/telehealthService";
import { useChannel } from "@impilo/mobile-messaging";
import { useAuth } from "@impilo/mobile-auth";
import type { TelehealthSession } from "../../types";

const WAITING_ROOM_POLL_MS = 5000;

type SessionStage = "DETAILS" | "WAITING_ROOM" | "IN_CALL" | "POST_CONSULT";

interface TelehealthSessionScreenProps {
  session: TelehealthSession;
  onBack: () => void;
}

export function TelehealthSessionScreen({ session: initialSession, onBack }: TelehealthSessionScreenProps) {
  const auth = useAuth();
  const [session, setSession] = useState<TelehealthSession>(initialSession);
  const [stage, setStage] = useState<SessionStage>(
    initialSession.status === "COMPLETED" ? "POST_CONSULT" : "DETAILS"
  );
  const [mediaRoomUrl, setMediaRoomUrl] = useState<string | null>(null);
  const [mediaToken, setMediaToken] = useState<string | null>(null);
  const [sessionChannel, setSessionChannel] = useState<string | null>(null);
  const [isJoining, setIsJoining] = useState(false);
  const [isRequestingToken, setIsRequestingToken] = useState(false);
  const [waitingForAdmission, setWaitingForAdmission] = useState(false);
  const [deniedMessage, setDeniedMessage] = useState<string | null>(null);
  const [audioOnly, setAudioOnly] = useState(false);
  const [isEnding, setIsEnding] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [endNotes, setEndNotes] = useState("");
  const [showEndForm, setShowEndForm] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [supportNotice, setSupportNotice] = useState<string | null>(null);
  const [videoEnabled, setVideoEnabled] = useState(true);
  const [micMuted, setMicMuted] = useState(false);
  const requestInFlight = useRef(false);

  const displayName =
    [auth.user?.given_name, auth.user?.family_name].filter(Boolean).join(" ") ||
    auth.user?.preferred_username ||
    "Impilo client";

  const mediaTokenRequest = useCallback((): MediaTokenRequest => ({
    displayName,
    role: "PATIENT",
    ...(audioOnly ? { mediaProfile: "AUDIO_ONLY" as const } : {}),
  }), [displayName, audioOnly]);

  // Real-time channel for session events
  const channel = useChannel(sessionChannel ?? `telehealth-${session.id}`, {
    onMessage: (event) => {
      if (event.type === "telemedicine.session.provider_late") {
        setSupportNotice("Your provider is running late. Please stay in the waiting room.");
      } else if (event.type === "telemedicine.session.video_failed") {
        setSupportNotice("Video connection failed. Open Impilo audio fallback or request support.");
      } else if (event.type === "telemedicine.session.audio_failed") {
        setSupportNotice("Audio connection failed. Use secure chat or request helpdesk support.");
      } else if (event.type === "telemedicine.session.completed" || event.type === "session_ended") {
        setSupportNotice("Your teleconsultation has ended. Follow-up steps are available in Impilo.");
        setSession((prev) => ({ ...prev, status: "COMPLETED" }));
        setMediaToken(null);
        setWaitingForAdmission(false);
        setStage("POST_CONSULT");
      }
    },
  });

  // Elapsed timer while in the call
  useEffect(() => {
    if (stage === "IN_CALL" && mediaToken) {
      const interval = setInterval(() => {
        setElapsed((prev) => prev + 1);
      }, 1000);
      return () => clearInterval(interval);
    }
  }, [stage, mediaToken]);

  const applyMediaTokenResult = useCallback(
    (result: { status: string; roomUrl?: string; token?: string; reason?: string }) => {
      if (result.status === "READY" && result.token) {
        setMediaRoomUrl(result.roomUrl ?? session.roomUrl ?? null);
        setMediaToken(result.token);
        setWaitingForAdmission(false);
        setDeniedMessage(null);
        setStage("IN_CALL");
        return;
      }
      if (result.status === "DENIED") {
        setWaitingForAdmission(false);
        setDeniedMessage(
          result.reason
            ? `Your provider could not admit you to this consult: ${result.reason}. Please rebook or contact your care team — nothing is lost.`
            : "Your provider could not admit you to this consult right now. Please rebook or contact your care team — nothing is lost."
        );
        return;
      }
      // WAITING — stay in the waiting room and keep polling.
      setWaitingForAdmission(true);
    },
    [session.roomUrl]
  );

  const requestToken = useCallback(async () => {
    if (requestInFlight.current) return;
    requestInFlight.current = true;
    setIsRequestingToken(true);
    setError(null);
    try {
      const result = await requestSessionMediaToken(session.id, mediaTokenRequest());
      applyMediaTokenResult(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      requestInFlight.current = false;
      setIsRequestingToken(false);
    }
  }, [session.id, mediaTokenRequest, applyMediaTokenResult]);

  // Waiting-room poll: while the provider has not admitted us, re-request every 5s.
  useEffect(() => {
    if (stage !== "WAITING_ROOM" || !waitingForAdmission) return;
    const interval = setInterval(() => {
      void requestToken();
    }, WAITING_ROOM_POLL_MS);
    return () => clearInterval(interval);
  }, [stage, waitingForAdmission, requestToken]);

  const handleJoin = useCallback(async () => {
    setIsJoining(true);
    setError(null);
    try {
      const result = await joinSession(session.id);
      setSession(result.session);
      if (result.channel) setSessionChannel(result.channel);
      setStage("WAITING_ROOM");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsJoining(false);
    }
  }, [session.id]);

  const handlePreJoinConfirm = useCallback(
    (choices: PreJoinChoices) => {
      setMicMuted(!choices.micEnabled);
      setVideoEnabled(choices.cameraEnabled && !audioOnly);
      void requestToken();
    },
    [audioOnly, requestToken]
  );

  const handleRefreshMedia = useCallback(async () => {
    setError(null);
    try {
      const result = await refreshSessionMediaToken(session.id, mediaTokenRequest());
      if (result.status === "READY" && result.token) {
        setMediaRoomUrl(result.roomUrl ?? mediaRoomUrl);
        setMediaToken(result.token);
        setSupportNotice("Media connection refreshed.");
      } else {
        setSupportNotice("Could not refresh the media connection yet. Please try again shortly.");
      }
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [session.id, mediaTokenRequest, mediaRoomUrl]);

  // TM-B18 parity (B5-4): withdrawal is recorded server-side FIRST (WITHDRAWN + ASYNC floor),
  // then local media drops immediately — the fastest possible publish-stop on device.
  const handleWithdrawConsent = useCallback(async () => {
    setError(null);
    try {
      await withdrawTelehealthConsent(session.id);
      setMediaToken(null);
      const updated = await fetchSession(session.id);
      setSession(updated);
      setStage("POST_CONSULT");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    }
  }, [session.id]);

  const handleEnd = useCallback(async () => {
    setIsEnding(true);
    setError(null);
    try {
      await endSession(session.id, endNotes || undefined);
      const updated = await fetchSession(session.id);
      setSession(updated);
      setMediaToken(null);
      setShowEndForm(false);
      setStage("POST_CONSULT");
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsEnding(false);
    }
  }, [session.id, endNotes]);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  };

  return (
    <Screen>
      <Header
        title="Teleconsultation"
        leftElement={
          <Button
            title={"← Back"}
            variant="ghost"
            size="sm"
            onPress={onBack}
            testID="session-back"
          />
        }
      />
      <ScrollView testID="telehealth-session-screen" style={styles.scrollView} contentContainerStyle={styles.container}>
        {error ? (
          <ErrorState title="Error" message={error.message} onRetry={() => setError(null)} />
        ) : null}

        {/* Session info */}
        <Card>
          <CardHeader title="Session Details" />
          <CardBody>
            <View style={styles.sessionDetails}>
              <View style={styles.badgeRow}>
                <Text style={styles.boldText}>{session.sessionType}</Text>
                <Badge
                  variant={session.status === "IN_PROGRESS" ? "default" : session.status === "COMPLETED" ? "secondary" : "outline"}
                >
                  {session.status}
                </Badge>
              </View>
              <Text style={styles.infoText}>{`Provider: Dr. ${session.providerName}`}</Text>
              <Text style={styles.infoTextSecondary}>
                {`Scheduled: ${new Date(session.scheduledAt).toLocaleString()}`}
              </Text>
              {stage === "IN_CALL" && mediaToken ? (
                <Text style={styles.durationText}>
                  {`Duration: ${formatTime(elapsed)}`}
                </Text>
              ) : null}
              {channel.isConnected ? (
                <Text style={styles.connectedText}>{"● Connected to session channel"}</Text>
              ) : null}
            </View>
          </CardBody>
        </Card>
        {supportNotice ? (
          <Card>
            <CardHeader title="Telemedicine Support" />
            <CardBody>
              <Text style={styles.infoText}>{supportNotice}</Text>
            </CardBody>
          </Card>
        ) : null}

        {/* Waiting room: device check + plain-language guidance before the call */}
        {stage === "WAITING_ROOM" ? (
          <Card>
            <CardHeader title="Waiting Room" />
            <CardBody>
              <View testID="telehealth-waiting-room" style={styles.waitingRoom}>
                <PreJoinNative
                  key={audioOnly ? "audio-only" : "audio-video"}
                  title="Check your camera and microphone"
                  joinLabel={isRequestingToken ? "Asking to join..." : waitingForAdmission ? "Ask again" : "Ask to join"}
                  joinDisabled={isRequestingToken || !!deniedMessage}
                  initialCameraEnabled={!audioOnly}
                  onJoin={handlePreJoinConfirm}
                />
                <Button
                  title={audioOnly ? "Audio only: on" : "Audio only: off"}
                  variant="outline"
                  onPress={() => {
                    setAudioOnly((prev) => !prev);
                  }}
                  testID="telehealth-audio-only-toggle"
                />
                <Text style={styles.waitingCopyTitle}>Who joins this consult</Text>
                <Text style={styles.waitingCopy}>
                  {`Only you and Dr. ${session.providerName} (plus anyone your provider explicitly invites) can join this secure session.`}
                </Text>
                <Text style={styles.waitingCopyTitle}>What happens next</Text>
                <Text style={styles.waitingCopy}>
                  When you ask to join, your provider sees you in their waiting room and admits you when they are ready. You do not need to do anything else — keep this screen open.
                </Text>
                <Text style={styles.consentLine}>
                  By joining you consent to this governed teleconsultation. No recording happens without your explicit consent, and your data stays inside Impilo.
                </Text>
                {waitingForAdmission && !deniedMessage ? (
                  <View style={styles.waitingStatus} testID="telehealth-waiting-status">
                    <Text style={styles.waitingStatusText}>
                      {"You're in the waiting room. Your provider will admit you shortly — hang tight."}
                    </Text>
                  </View>
                ) : null}
                {deniedMessage ? (
                  <View style={styles.deniedBox} testID="telehealth-denied">
                    <Text style={styles.deniedText}>{deniedMessage}</Text>
                  </View>
                ) : null}
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* Active call */}
        {stage === "IN_CALL" && mediaToken ? (
          <Card>
            <CardBody>
              <View testID="telehealth-in-call" style={styles.activeSessionArea}>
                <AdaptiveSessionRoomNative
                  serverUrl={mediaRoomUrl ?? session.roomUrl}
                  token={mediaToken}
                  layout="consult"
                  audioOnly={audioOnly}
                  videoEnabled={videoEnabled}
                  micMuted={micMuted}
                  showReconnectBanner
                  onConnected={() => setSupportNotice("LiveKit media connected through Impilo RTC Gateway.")}
                  onDisconnected={() => setSupportNotice("LiveKit media disconnected. Your teleconsult remains open.")}
                  onError={(message) => setSupportNotice(message)}
                />
              </View>

              {/* Session controls */}
              <View style={styles.controlsRow}>
                <Button
                  title={audioOnly ? "Audio only: on" : "Audio only: off"}
                  variant="outline"
                  onPress={() => setAudioOnly((prev) => !prev)}
                  testID="telehealth-audio-only-toggle-call"
                />
                <Button
                  title={videoEnabled ? "Disable Video" : "Enable Video"}
                  variant="outline"
                  onPress={() => setVideoEnabled((prev) => !prev)}
                  testID="toggle-video-btn"
                />
                <Button
                  title={micMuted ? "Unmute Mic" : "Mute Mic"}
                  variant="outline"
                  onPress={() => setMicMuted((prev) => !prev)}
                  testID="toggle-mic-btn"
                />
                <Button
                  title="Refresh Media"
                  variant="outline"
                  onPress={handleRefreshMedia}
                  testID="refresh-media-btn"
                />
                <Button
                  title="End Session"
                  variant="primary"
                  onPress={() => setShowEndForm(true)}
                  testID="end-session-btn"
                />
                {/* TM-B18 parity (B5-4): consent withdrawal — records WITHDRAWN server-side,
                    floors media to ASYNC, then drops local media immediately. Visit continues
                    by secure message; care is never lost. */}
                <Button
                  title="Withdraw Consent"
                  variant="outline"
                  onPress={handleWithdrawConsent}
                  testID="withdraw-consent-btn"
                />
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* End session form */}
        {showEndForm ? (
          <Card>
            <CardHeader title="End Session" />
            <CardBody>
              <View style={styles.formContainer}>
                <TextField
                  label="Notes (optional)"
                  value={endNotes}
                  onChange={setEndNotes}
                  placeholder="Any post-session notes"
                  testID="end-session-notes"
                />
                <View style={styles.buttonRow}>
                  <Button
                    title={isEnding ? "Ending..." : "Confirm End"}
                    variant="primary"
                    onPress={handleEnd}
                    disabled={isEnding}
                    testID="confirm-end-session"
                  />
                  <Button
                    title="Continue"
                    variant="ghost"
                    onPress={() => setShowEndForm(false)}
                  />
                </View>
              </View>
            </CardBody>
          </Card>
        ) : null}

        {/* Pre-session: Join button */}
        {stage === "DETAILS" && (session.status === "SCHEDULED" || session.status === "IN_PROGRESS") ? (
          <View style={styles.joinContainer}>
            <Button
              title={isJoining ? "Joining..." : "Join Session"}
              variant="primary"
              size="lg"
              onPress={handleJoin}
              disabled={isJoining}
              testID="join-session"
            />
          </View>
        ) : null}

        {/* Post-consult: thank-you + follow-up */}
        {stage === "POST_CONSULT" ? (
          <Card>
            <CardHeader title="Thank you" />
            <CardBody>
              <View testID="telehealth-post-consult" style={styles.postConsult}>
                <Text style={styles.postConsultTitle}>Your teleconsultation has ended.</Text>
                <Text style={styles.summaryText}>
                  {`Started: ${session.startedAt ? new Date(session.startedAt).toLocaleString() : "N/A"}`}
                </Text>
                <Text style={styles.summaryText}>
                  {`Ended: ${session.endedAt ? new Date(session.endedAt).toLocaleString() : "N/A"}`}
                </Text>
                <Text style={styles.waitingCopyTitle}>Follow-up</Text>
                <Text style={styles.summaryText} testID="telehealth-followup">
                  {session.notes
                    ? `Notes from this session: ${session.notes}`
                    : "Any prescriptions, referrals, or follow-up appointments from this consult will appear in your Impilo timeline shortly."}
                </Text>
                <Button title="Back to Telehealth" variant="outline" onPress={onBack} testID="post-consult-back" />
              </View>
            </CardBody>
          </Card>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  scrollView: {
    flex: 1,
  },
  container: {
    padding: 16,
    gap: 16,
  },
  sessionDetails: {
    gap: 8,
  },
  badgeRow: {
    flexDirection: "row",
    gap: 8,
    alignItems: "center",
  },
  boldText: {
    fontWeight: "700",
  },
  infoText: {
    fontSize: 14,
  },
  infoTextSecondary: {
    fontSize: 14,
    color: colors.gray[500],
  },
  durationText: {
    fontSize: 20,
    fontWeight: "700",
    color: "#2563EB",
    marginVertical: 8,
  },
  connectedText: {
    fontSize: 12,
    color: "#009739",
  },
  waitingRoom: {
    gap: 12,
  },
  waitingCopyTitle: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.gray[900],
    marginTop: 4,
  },
  waitingCopy: {
    fontSize: 13,
    color: colors.gray[700],
  },
  consentLine: {
    fontSize: 12,
    color: colors.gray[500],
    fontStyle: "italic",
  },
  waitingStatus: {
    backgroundColor: "#EFF6FF",
    borderRadius: 8,
    padding: 12,
  },
  waitingStatusText: {
    fontSize: 13,
    color: "#1E40AF",
  },
  deniedBox: {
    backgroundColor: colors.ui.warning.light,
    borderRadius: 8,
    padding: 12,
  },
  deniedText: {
    fontSize: 13,
    color: colors.ui.warning.text,
  },
  activeSessionArea: {
    borderRadius: 12,
    overflow: "hidden",
    marginBottom: 16,
  },
  controlsRow: {
    flexDirection: "row",
    gap: 8,
    justifyContent: "center",
    flexWrap: "wrap",
  },
  formContainer: {
    gap: 12,
  },
  buttonRow: {
    flexDirection: "row",
    gap: 8,
  },
  joinContainer: {
    alignItems: "center",
  },
  postConsult: {
    gap: 8,
  },
  postConsultTitle: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.gray[900],
  },
  summaryText: {
    fontSize: 14,
    marginVertical: 4,
  },
});
