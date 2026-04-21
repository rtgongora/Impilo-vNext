/**
 * Card — Container component with elevation, rounded corners, and optional header/body/footer.
 */

import React from "react";
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  type StyleProp,
  type ViewStyle,
} from "react-native";

export interface CardProps {
  children: React.ReactNode;
  variant?: "elevated" | "outlined" | "filled";
  padding?: "none" | "sm" | "md" | "lg";
  onPress?: () => void;
  style?: StyleProp<ViewStyle>;
  testID?: string;
  accessibilityLabel?: string;
}

const PADDING_MAP = { none: 0, sm: 8, md: 16, lg: 24 };

const VARIANT_STYLES: Record<CardProps["variant"] & string, object> = {
  elevated: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 3,
  },
  outlined: {
    backgroundColor: "#FFFFFF",
    borderRadius: 16,
    borderWidth: 1,
    borderColor: "#E5E7EB",
  },
  filled: {
    backgroundColor: "#F9FAFB",
    borderRadius: 16,
  },
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
  const containerStyle = [
    styles.card,
    VARIANT_STYLES[variant],
    { padding: PADDING_MAP[padding] },
    style,
  ];

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        testID={testID}
        accessibilityLabel={accessibilityLabel}
        accessibilityRole="button"
        style={({ pressed }) => [
          ...containerStyle,
          { opacity: pressed ? 0.9 : 1 },
        ]}
      >
        {children}
      </Pressable>
    );
  }

  return (
    <View testID={testID} accessibilityLabel={accessibilityLabel} style={containerStyle}>
      {children}
    </View>
  );
}

export interface CardHeaderProps {
  title?: string;
  children?: React.ReactNode;
  subtitle?: string;
  action?: React.ReactNode;
  rightElement?: React.ReactNode;
}

export function CardHeader({ title, children, subtitle, action, rightElement }: CardHeaderProps) {
  const resolvedTitle = title ?? (typeof children === "string" ? children : "");
  const resolvedAction = action ?? rightElement;
  return (
    <View style={styles.header}>
      <View style={styles.headerText}>
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
  return <View style={styles.body}>{children}</View>;
}

export interface CardFooterProps {
  children: React.ReactNode;
}

export function CardFooter({ children }: CardFooterProps) {
  return <View style={styles.footer}>{children}</View>;
}

const styles = StyleSheet.create({
  card: {
    marginBottom: 12,
    overflow: "hidden",
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 12,
  },
  headerText: {
    flex: 1,
  },
  headerTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#111827",
    letterSpacing: 0.1,
  },
  headerSubtitle: {
    fontSize: 13,
    color: "#6B7280",
    marginTop: 2,
  },
  body: {
    gap: 8,
  },
  footer: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: "#F3F4F6",
  },
});
