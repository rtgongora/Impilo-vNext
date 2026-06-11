import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Card, CardBody } from "./Card";
import { ServiceStatusBadge, type ServiceWiringBadgeStatus } from "./ServiceStatusBadge";

export interface ServiceCardProps {
  name: string;
  description: string;
  icon?: React.ReactNode;
  wiringStatus: ServiceWiringBadgeStatus;
  onPress?: () => void;
  disabled?: boolean;
  actionLabel?: string;
  accentColor?: string;
  testID?: string;
}

export function ServiceCard({
  name,
  description,
  icon,
  wiringStatus,
  onPress,
  disabled,
  actionLabel,
  accentColor = "#059669",
  testID,
}: ServiceCardProps) {
  const isBlocked = wiringStatus === "blocked" || wiringStatus === "backendMissing" || disabled;
  const label =
    actionLabel ??
    (wiringStatus === "deepLinked"
      ? "Open in web"
      : isBlocked
        ? "Unavailable"
        : "Open");

  return (
    <Pressable
      testID={testID}
      onPress={isBlocked ? undefined : onPress}
      disabled={isBlocked || !onPress}
      style={({ pressed }) => [pressed && !isBlocked && styles.pressed]}
    >
      <Card style={styles.card}>
        <CardBody>
          <View style={styles.row}>
            <View style={[styles.iconWrap, { backgroundColor: `${accentColor}18` }]}>
              {icon ?? <Text style={[styles.iconFallback, { color: accentColor }]}>●</Text>}
            </View>
            <View style={styles.content}>
              <View style={styles.titleRow}>
                <Text style={styles.title}>{name}</Text>
                <ServiceStatusBadge status={wiringStatus} compact />
              </View>
              <Text style={styles.description} numberOfLines={2}>
                {description}
              </Text>
              <Text style={[styles.action, isBlocked && styles.actionMuted]}>{label}</Text>
            </View>
            {!isBlocked && onPress ? (
              <Text style={styles.chevron}>›</Text>
            ) : null}
          </View>
        </CardBody>
      </Card>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: 10,
  },
  pressed: {
    opacity: 0.92,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  iconWrap: {
    width: 44,
    height: 44,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  content: {
    flex: 1,
    gap: 4,
  },
  titleRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 8,
  },
  title: {
    fontSize: 16,
    fontWeight: "600",
    color: "#111827",
    flex: 1,
  },
  description: {
    fontSize: 13,
    color: "#6B7280",
    lineHeight: 18,
  },
  action: {
    fontSize: 12,
    fontWeight: "600",
    color: "#059669",
    marginTop: 2,
  },
  actionMuted: {
    color: "#9CA3AF",
  },
  iconFallback: {
    fontSize: 18,
    fontWeight: "700",
  },
  chevron: {
    fontSize: 22,
    color: "#9CA3AF",
    fontWeight: "300",
  },
});
