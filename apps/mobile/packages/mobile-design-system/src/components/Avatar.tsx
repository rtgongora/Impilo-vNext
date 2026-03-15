/**
 * Avatar — User/patient profile image with fallback initials.
 */

import React from "react";
import { colors } from "../tokens/colors";

export type AvatarSize = "xs" | "sm" | "md" | "lg" | "xl";

export interface AvatarProps {
  name: string;
  imageUrl?: string;
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

export function Avatar({ name, imageUrl, size = "md", testID }: AvatarProps) {
  const dimension = AVATAR_SIZES[size];

  if (imageUrl) {
    return React.createElement("img", {
      src: imageUrl,
      alt: name,
      "data-testid": testID,
      style: {
        width: dimension,
        height: dimension,
        borderRadius: "50%",
        objectFit: "cover",
      },
    });
  }

  return React.createElement(
    "div",
    {
      "data-testid": testID,
      "aria-label": name,
      role: "img",
      style: {
        width: dimension,
        height: dimension,
        borderRadius: "50%",
        backgroundColor: hashColor(name),
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "#FFFFFF",
        fontWeight: 600,
        fontSize: dimension * 0.4,
      },
    },
    getInitials(name)
  );
}
