import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";

export interface PrimaryActionCardProps {
  title: string;
  subtitle: string;
  icon: string;
  color: string;
  backgroundColor: string;
  onPress?: () => void;
  testID?: string;
}

export function PrimaryActionCard({
  title,
  subtitle,
  icon,
  color,
  backgroundColor,
  onPress,
  testID,
}: PrimaryActionCardProps) {
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      disabled={!onPress}
      style={({ pressed }) => [styles.card, { backgroundColor }, pressed && styles.pressed]}
    >
      <View style={[styles.iconWrap, { backgroundColor: `${color}22` }]}>
        <Ionicons name={icon as never} size={24} color={color} />
      </View>
      <View style={styles.content}>
        <Text style={styles.title}>{title}</Text>
        <Text style={styles.subtitle}>{subtitle}</Text>
      </View>
      {onPress ? <Ionicons name="chevron-forward" size={20} color={color} /> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    padding: 16,
    borderRadius: 16,
    marginBottom: 10,
  },
  pressed: {
    opacity: 0.9,
  },
  iconWrap: {
    width: 48,
    height: 48,
    borderRadius: 14,
    alignItems: "center",
    justifyContent: "center",
  },
  content: {
    flex: 1,
    gap: 2,
  },
  title: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
  },
  subtitle: {
    fontSize: 13,
    color: "#4B5563",
    lineHeight: 18,
  },
});
