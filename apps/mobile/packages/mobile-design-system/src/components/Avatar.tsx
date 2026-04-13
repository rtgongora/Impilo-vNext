/**
 * Avatar — User/patient profile image with fallback initials.
 */

import React from "react";
import { View, Text, Image, StyleSheet } from "react-native";
import { colors } from "../tokens/colors";

export type AvatarSize = "xs" | "sm" | "md" | "lg" | "xl";

export interface AvatarProps {
  name: string;
  imageUrl?: string;
  /**
   * Back-compat alias for `imageUrl` (older app screens).
   * Prefer `imageUrl`.
   */
  src?: string;
  size?: AvatarSize;
  testID?: string;
}

const AVATAR_SIZES: Record<AvatarSize, number> = {
  xs: 24,
  sm: 32,
  md: 40,
  lg: 56,
  xl: 80,
};

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}

function hashColor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  const palette = [
    colors.primary[500],
    colors.secondary[500],
    colors.clinical.vitals,
    colors.clinical.prescription,
    colors.clinical.labResult,
    colors.clinical.referral,
  ];
  return palette[Math.abs(hash) % palette.length];
}

export function Avatar({ name, imageUrl, src, size = "md", testID }: AvatarProps) {
  const dimension = AVATAR_SIZES[size];
  const resolvedImageUrl = imageUrl ?? src;

  if (resolvedImageUrl) {
    return (
      <Image
        source={{ uri: resolvedImageUrl }}
        accessibilityLabel={name}
        testID={testID}
        style={{
          width: dimension,
          height: dimension,
          borderRadius: dimension / 2,
        }}
      />
    );
  }

  return (
    <View
      testID={testID}
      accessibilityLabel={name}
      accessibilityRole="image"
      style={{
        width: dimension,
        height: dimension,
        borderRadius: dimension / 2,
        backgroundColor: hashColor(name),
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Text
        style={{
          color: "#FFFFFF",
          fontWeight: "600",
          fontSize: dimension * 0.4,
        }}
      >
        {getInitials(name)}
      </Text>
    </View>
  );
}
