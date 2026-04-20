/**
 * Card — Container component with optional header, body, and footer.
 */

import React from "react";
import { View, Text, Pressable, StyleSheet, type StyleProp, type ViewStyle } from "react-native";

export interface CardProps {
  children: React.ReactNode;
  variant?: "elevated" | "outlined" | "filled";
  padding?: "none" | "sm" | "md" | "lg";
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
  testID?: string;
  accessibilityLabel?: string;
}

const PADDING_MAP = {
  none: 0,
  sm: 8,
  md: 16,
  lg: 24,
};

export function Card({
  children,
  variant = "elevated",
  padding = "md",
  onPress,
  style,
  testID,
  accessibilityLabel,
}: CardProps) {
  const content = (
    <View style={[styles.card, { padding: PADDING_MAP[padding] }, style]}>
      {children}
    </View>
  );

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        testID={testID}
        accessibilityLabel={accessibilityLabel}
        accessibilityRole="button"
      >
        {content}
      </Pressable>
    );
  }

  return (
    <View testID={testID} accessibilityLabel={accessibilityLabel}>
      {content}
    </View>
  );
}

export interface CardHeaderProps {
  title?: string;
  children?: React.ReactNode;
  subtitle?: string;
  action?: React.ReactNode;
  /**
   * Back-compat alias used by older screens.
   */
  rightElement?: React.ReactNode;
}

export function CardHeader({ title, children, subtitle, action, rightElement }: CardHeaderProps) {
  const resolvedTitle = title ?? (typeof children === "string" ? children : "");
  const resolvedAction = action ?? rightElement;
  return (
    <View style={styles.header}>
      <View>
        <Text style={styles.headerTitle}>{resolvedTitle}</Text>
        {subtitle ? <Text style={styles.headerSubtitle}>{subtitle}</Text> : null}
      </View>
      {resolvedAction ?? null}
    </View>
  );
}

export interface CardBodyProps {
  children: React.ReactNode;
}

export function CardBody({ children }: CardBodyProps) {
  return <View>{children}</View>;
}

export interface CardFooterProps {
  children: React.ReactNode;
}

export function CardFooter({ children }: CardFooterProps) {
  return <View>{children}</View>;
}

const styles = StyleSheet.create({
  card: {},
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  headerTitle: {
    fontSize: 17,
    fontWeight: "600",
  },
  headerSubtitle: {
    fontSize: 14,
    color: "#757575",
  },
});
