import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Button } from "../components/Button";

export interface UnauthorizedStateProps {
  title?: string;
  message?: string;
  onSignIn?: () => void;
  onSwitchContext?: () => void;
}

export function UnauthorizedState({
  title = "Not authorised",
  message = "You do not have permission to access this service in your current work context.",
  onSignIn,
  onSwitchContext,
}: UnauthorizedStateProps) {
  return (
    <View style={styles.container} accessibilityRole="alert">
      <View style={styles.iconWrap}>
        <Ionicons name="lock-closed-outline" size={32} color="#B91C1C" />
      </View>
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.message}>{message}</Text>
      <View style={styles.actions}>
        {onSwitchContext ? (
          <Button variant="outline" title="Switch context" onPress={onSwitchContext} />
        ) : null}
        {onSignIn ? (
          <Button variant="primary" title="Sign in" onPress={onSignIn} />
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: "center",
    justifyContent: "center",
    padding: 32,
    gap: 12,
  },
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: "#FEE2E2",
    alignItems: "center",
    justifyContent: "center",
  },
  title: {
    fontSize: 18,
    fontWeight: "700",
    color: "#111827",
    textAlign: "center",
  },
  message: {
    fontSize: 14,
    color: "#6B7280",
    textAlign: "center",
    lineHeight: 20,
    maxWidth: 320,
  },
  actions: {
    marginTop: 8,
    gap: 8,
    width: "100%",
    maxWidth: 280,
  },
});
