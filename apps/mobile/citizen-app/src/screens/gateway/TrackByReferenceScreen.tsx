/**
 * TrackByReferenceScreen — guest emergency tracking (no sign-in). A person who raised an SOS
 * anonymously has only the human reference from their receipt; this screen takes that reference
 * and shows PII-free live status via the public gateway lane (delegating to TrackEmergencyScreen
 * in reference mode).
 */
import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Header, Card, CardBody, TextField, Button, colors } from "@impilo/mobile-design-system";
import { TrackEmergencyScreen } from "../emergency/TrackEmergencyScreen";

const GREEN = "#059669";

export function TrackByReferenceScreen({ onBack }: { onBack?: () => void }) {
  const [input, setInput] = useState("");
  const [reference, setReference] = useState<string | null>(null);

  if (reference) {
    return (
      <View style={styles.host} testID="track-by-reference-status">
        <TrackEmergencyScreen reference={reference} />
        <View style={styles.changeRow}>
          <Button title="Track a different reference" variant="secondary" size="sm" onPress={() => setReference(null)} />
        </View>
      </View>
    );
  }

  return (
    <Screen>
      <Header
        title="Track an emergency"
        subtitle="Enter the reference from your receipt"
        leftElement={
          onBack ? (
            <Pressable onPress={onBack} hitSlop={8} accessibilityLabel="Back">
              <Ionicons name="arrow-back" size={22} color={GREEN} />
            </Pressable>
          ) : undefined
        }
      />
      <ScrollView testID="track-by-reference-screen" contentContainerStyle={styles.body}>
        <Card>
          <CardBody>
            <Text style={styles.help}>
              If you asked for emergency help without an account, enter the reference you were given
              (for example SOS-1A2B3C) to see the latest status. No sign-in needed.
            </Text>
            <TextField
              testID="track-by-reference-input"
              value={input}
              onChangeText={setInput}
              placeholder="e.g. SOS-1A2B3C"
              autoCapitalize="characters"
            />
            <View style={styles.actionRow}>
              <Button
                title="Track request"
                onPress={() => setReference(input.trim())}
                variant="primary"
                size="lg"
                fullWidth
                disabled={!input.trim()}
                testID="track-by-reference-go"
              />
            </View>
          </CardBody>
        </Card>
        <Text style={styles.footer}>
          If this is a life-threatening emergency, call 999 or 112 now — do not wait for the callback.
        </Text>
      </ScrollView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  host: { flex: 1 },
  changeRow: { padding: 16 },
  body: { padding: 16, gap: 12 },
  help: { fontSize: 13, color: colors.gray[700], lineHeight: 19, marginBottom: 12 },
  actionRow: { marginTop: 16 },
  footer: { fontSize: 12, color: "#B91C1C", fontWeight: "600", lineHeight: 18 },
});
