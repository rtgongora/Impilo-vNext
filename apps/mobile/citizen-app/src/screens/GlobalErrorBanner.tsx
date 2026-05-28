import React, { useEffect, useRef } from "react";
import { Animated, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useAppStore } from "../stores/appStore";

const ERROR_BG   = "#FEF2F2";
const ERROR_BORDER = "#FECACA";
const ERROR_ICON  = "#DC2626";
const ERROR_TEXT  = "#991B1B";

export function GlobalErrorBanner() {
  const { globalError, setGlobalError } = useAppStore();
  const slideAnim = useRef(new Animated.Value(-80)).current;
  const prevError = useRef(globalError);

  useEffect(() => {
    if (globalError && !prevError.current) {
      Animated.spring(slideAnim, {
        toValue: 0,
        tension: 100,
        friction: 12,
        useNativeDriver: true,
      }).start();
    } else if (!globalError && prevError.current) {
      Animated.timing(slideAnim, {
        toValue: -80,
        duration: 220,
        useNativeDriver: true,
      }).start();
    }
    prevError.current = globalError;
  }, [globalError, slideAnim]);

  if (!globalError) return null;

  return (
    <Animated.View
      testID="global-error-banner"
      accessibilityRole="alert"
      style={[styles.container, { transform: [{ translateY: slideAnim }] }]}
    >
      <View style={styles.iconWrap}>
        <Ionicons name="alert-circle" size={20} color={ERROR_ICON} />
      </View>
      <View style={styles.textBlock}>
        <Text style={styles.code}>{globalError.code}</Text>
        <Text style={styles.message} numberOfLines={2}>{globalError.message}</Text>
      </View>
      <Pressable
        onPress={() => setGlobalError(null)}
        testID="dismiss-error"
        hitSlop={12}
        style={styles.dismiss}
      >
        <Ionicons name="close" size={18} color={ERROR_ICON} />
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingVertical: 10,
    paddingHorizontal: 14,
    backgroundColor: ERROR_BG,
    borderBottomWidth: 1,
    borderBottomColor: ERROR_BORDER,
  },
  iconWrap: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: "#FEE2E2",
    alignItems: "center",
    justifyContent: "center",
  },
  textBlock: { flex: 1 },
  code: {
    fontSize: 11,
    fontWeight: "700",
    color: ERROR_ICON,
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginBottom: 1,
  },
  message: { fontSize: 13, color: ERROR_TEXT, lineHeight: 17 },
  dismiss: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: "#FEE2E2",
    alignItems: "center",
    justifyContent: "center",
  },
});
