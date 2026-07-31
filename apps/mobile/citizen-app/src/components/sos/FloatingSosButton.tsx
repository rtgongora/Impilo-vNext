import React, { useCallback, useReducer, useState } from "react";
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button, GlassSurface, useTier } from "@impilo/mobile-design-system";
import { captureCurrentLocation } from "@impilo/mobile-ndila";
import { useAuth } from "@impilo/mobile-auth";
import { createSos } from "../../services/emergencyService";
import { TrackEmergencyScreen } from "../../screens/emergency/TrackEmergencyScreen";
import { buildDefaultSosInput, initialSosState, sosReducer } from "./sosFlow";

async function bestEffortLocation(): Promise<{ lat?: number; lng?: number }> {
  try {
    const fix = await captureCurrentLocation({ highAccuracy: true, timeoutMs: 6000 });
    return { lat: fix.coordinate.latitude, lng: fix.coordinate.longitude };
  } catch {
    return {};
  }
}

export interface FloatingSosButtonProps {
  bottomOffset?: number;
  testID?: string;
}

export function FloatingSosButton({ bottomOffset = 96, testID = "citizen-sos-fab" }: FloatingSosButtonProps) {
  const [state, dispatch] = useReducer(sosReducer, initialSosState);
  const [callbackNumber, setCallbackNumber] = useState("");
  const { isAuthenticated: authenticated } = useAuth();
  const tier = useTier();

  const send = useCallback(async () => {
    dispatch({ type: "SEND" });
    try {
      const location = await bestEffortLocation();
      const req = await createSos(
        buildDefaultSosInput({
          ...location,
          authenticated,
          callbackNumber: callbackNumber.trim() || undefined,
        }),
      );
      if (req?.id) {
        dispatch({ type: "SENT", requestId: req.id });
      } else {
        dispatch({
          type: "FAIL",
          error: "Your SOS was sent but no reference came back — please also call for help.",
        });
      }
    } catch (e) {
      dispatch({
        type: "FAIL",
        error: e instanceof Error ? e.message : "Could not send your SOS. Please try again or call for help.",
      });
    }
  }, [authenticated, callbackNumber]);

  const canSend = authenticated || callbackNumber.trim().length > 0;

  return (
    <>
      <View style={[styles.fabWrap, { bottom: bottomOffset }]} pointerEvents="box-none">
        <Pressable
          testID={testID}
          accessibilityRole="button"
          accessibilityLabel="Emergency SOS"
          accessibilityHint="Sends an emergency request; help is dispatched with no payment"
          onPress={() => dispatch({ type: "OPEN" })}
          style={({ pressed }) => (pressed ? styles.pressed : undefined)}
        >
          <GlassSurface tone="dark" glow="danger" tier={tier} style={styles.fab}>
            <Ionicons name="alert" size={26} color="#FFFFFF" />
            <Text style={styles.fabLabel}>SOS</Text>
          </GlassSurface>
        </Pressable>
      </View>

      <Modal
        visible={state.phase !== "idle"}
        transparent
        animationType="fade"
        onRequestClose={() => dispatch({ type: "CLOSE" })}
      >
        <View style={styles.backdrop}>
          {state.phase === "tracking" && state.requestId ? (
            <View style={styles.trackHost} testID="citizen-sos-tracking">
              <TrackEmergencyScreen requestId={state.requestId} />
              <View style={styles.trackClose}>
                <Button title="Close" variant="secondary" onPress={() => dispatch({ type: "CLOSE" })} />
              </View>
            </View>
          ) : (
            <GlassSurface tone="dark" glow="danger" tier={tier} style={styles.sheet} testID="citizen-sos-sheet">
              {state.phase === "sending" ? (
                <View style={styles.center}>
                  <ActivityIndicator color="#FF5A63" />
                  <Text style={styles.sheetBody}>Sending your emergency SOS…</Text>
                </View>
              ) : state.phase === "error" ? (
                <>
                  <Text style={styles.sheetTitle}>Couldn&apos;t send your SOS</Text>
                  <Text style={styles.sheetBody}>{state.error}</Text>
                  <View style={styles.row}>
                    <Button title="Try again" variant="destructive" onPress={send} disabled={!canSend} />
                    <Button title="Cancel" variant="secondary" onPress={() => dispatch({ type: "CANCEL" })} />
                  </View>
                </>
              ) : (
                <>
                  <Text style={styles.sheetTitle}>Send emergency SOS?</Text>
                  <Text style={styles.sheetBody}>
                    Help will be dispatched for you. No payment is ever required for an emergency.
                  </Text>
                  {!authenticated ? (
                    <TextInput
                      style={styles.callbackInput}
                      value={callbackNumber}
                      onChangeText={setCallbackNumber}
                      placeholder="Callback phone (required)"
                      placeholderTextColor="#8A9A93"
                      keyboardType="phone-pad"
                      testID="citizen-sos-callback"
                    />
                  ) : null}
                  <View style={styles.row}>
                    <Button
                      title="Send SOS"
                      variant="destructive"
                      onPress={send}
                      disabled={!canSend}
                      testID="citizen-sos-confirm"
                    />
                    <Button title="Cancel" variant="secondary" onPress={() => dispatch({ type: "CANCEL" })} />
                  </View>
                </>
              )}
            </GlassSurface>
          )}
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  fabWrap: { position: "absolute", left: 18, alignItems: "flex-start" },
  fab: { width: 62, height: 62, borderRadius: 31, alignItems: "center", justifyContent: "center" },
  fabLabel: { color: "#FFFFFF", fontSize: 11, fontWeight: "700", marginTop: 1 },
  pressed: { opacity: 0.85 },
  backdrop: { flex: 1, backgroundColor: "rgba(0,0,0,0.55)", justifyContent: "center", padding: 20 },
  sheet: { padding: 20 },
  sheetTitle: { color: "#EEF4F0", fontSize: 18, fontWeight: "700", marginBottom: 8 },
  sheetBody: { color: "#C7D4CD", fontSize: 14, lineHeight: 20, marginBottom: 16, textAlign: "center" },
  callbackInput: {
    borderWidth: 1,
    borderColor: "#4A5C54",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: "#EEF4F0",
    marginBottom: 16,
    fontSize: 16,
  },
  center: { alignItems: "center", paddingVertical: 8 },
  row: { flexDirection: "row", gap: 12, justifyContent: "center" },
  trackHost: { flex: 1, borderRadius: 16, overflow: "hidden" },
  trackClose: { position: "absolute", top: 12, right: 12 },
});
