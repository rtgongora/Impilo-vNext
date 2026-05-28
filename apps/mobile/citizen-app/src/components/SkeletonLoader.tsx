import React, { useEffect, useRef } from "react";
import { Animated, StyleSheet, View, type ViewStyle } from "react-native";

interface SkeletonProps {
  width?: number | string;
  height?: number;
  borderRadius?: number;
  style?: ViewStyle;
}

function SkeletonBox({ width = "100%", height = 16, borderRadius = 8, style }: SkeletonProps) {
  const opacity = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    const pulse = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 1, duration: 700, useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 0.3, duration: 700, useNativeDriver: true }),
      ]),
    );
    pulse.start();
    return () => pulse.stop();
  }, [opacity]);

  return (
    <Animated.View
      style={[
        { width: width as never, height, borderRadius, backgroundColor: "#E5E7EB" },
        { opacity },
        style,
      ]}
    />
  );
}

export function SkeletonCard() {
  return (
    <View style={styles.card}>
      <SkeletonBox height={14} width="45%" borderRadius={6} style={styles.mb8} />
      <SkeletonBox height={10} width="70%" borderRadius={6} style={styles.mb6} />
      <SkeletonBox height={10} width="55%" borderRadius={6} />
    </View>
  );
}

export function SkeletonListRow() {
  return (
    <View style={styles.row}>
      <SkeletonBox width={40} height={40} borderRadius={20} />
      <View style={styles.rowText}>
        <SkeletonBox height={13} width="60%" borderRadius={6} style={styles.mb6} />
        <SkeletonBox height={10} width="40%" borderRadius={6} />
      </View>
    </View>
  );
}

export function SkeletonBanner() {
  return (
    <View style={styles.banner}>
      <SkeletonBox height={56} borderRadius={16} />
    </View>
  );
}

export function SkeletonQuickActions() {
  return (
    <View style={styles.quickRow}>
      {Array.from({ length: 5 }).map((_, i) => (
        <View key={i} style={styles.quickItem}>
          <SkeletonBox width={44} height={44} borderRadius={22} style={styles.mb8} />
          <SkeletonBox width={44} height={10} borderRadius={6} />
        </View>
      ))}
    </View>
  );
}

export function SkeletonHomeContent() {
  return (
    <View style={styles.homeContent}>
      <SkeletonBanner />
      <SkeletonCard />
      <SkeletonQuickActions />
      <SkeletonCard />
      <SkeletonCard />
    </View>
  );
}

export function SkeletonConversationList() {
  return (
    <View style={{ gap: 10 }}>
      {Array.from({ length: 5 }).map((_, i) => (
        <View key={i} style={styles.convRow}>
          <SkeletonBox width={44} height={44} borderRadius={22} />
          <View style={styles.rowText}>
            <SkeletonBox height={13} width="55%" borderRadius={6} style={styles.mb6} />
            <SkeletonBox height={10} width="80%" borderRadius={6} style={styles.mb6} />
            <SkeletonBox height={10} width="30%" borderRadius={6} />
          </View>
        </View>
      ))}
    </View>
  );
}

export function SkeletonServiceList() {
  return (
    <View style={{ gap: 12 }}>
      {Array.from({ length: 4 }).map((_, i) => (
        <View key={i} style={styles.card}>
          <View style={styles.serviceTop}>
            <View style={styles.rowText}>
              <SkeletonBox height={14} width="65%" borderRadius={6} style={styles.mb8} />
              <SkeletonBox height={10} width="40%" borderRadius={6} style={styles.mb6} />
            </View>
            <SkeletonBox width={60} height={32} borderRadius={12} />
          </View>
          <SkeletonBox height={10} width="90%" borderRadius={6} style={styles.mb6} />
          <SkeletonBox height={10} width="70%" borderRadius={6} />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    padding: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  convRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 14,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  rowText: { flex: 1 },
  banner: { borderRadius: 16 },
  quickRow: {
    flexDirection: "row",
    gap: 10,
  },
  quickItem: {
    width: 80,
    alignItems: "center",
    backgroundColor: "#FFFFFF",
    borderRadius: 14,
    padding: 12,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  homeContent: { gap: 16 },
  serviceTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 8 },
  mb8: { marginBottom: 8 },
  mb6: { marginBottom: 6 },
});
