/**
 * SkeletonLoader — Placeholder skeleton for loading states.
 */

import React, { useEffect, useRef } from "react";
import { View, Animated, StyleSheet } from "react-native";

export interface SkeletonLoaderProps {
  width?: number | string;
  height?: number | string;
  borderRadius?: number;
  variant?: "text" | "rectangular" | "circular";
  lines?: number;
  testID?: string;
}

function SkeletonBlock({
  width,
  height,
  borderRadius,
}: {
  width: number | string;
  height: number | string;
  borderRadius: number | string;
}) {
  const opacity = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    const animation = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, {
          toValue: 1,
          duration: 750,
          useNativeDriver: true,
        }),
        Animated.timing(opacity, {
          toValue: 0.3,
          duration: 750,
          useNativeDriver: true,
        }),
      ])
    );
    animation.start();
    return () => animation.stop();
  }, [opacity]);

  return (
    <Animated.View
      style={{
        width: width as number,
        height: height as number,
        borderRadius: borderRadius as number,
        backgroundColor: "#E0E0E0",
        opacity,
      }}
    />
  );
}

export function SkeletonLoader({
  width = "100%" as number | string,
  height = 16,
  borderRadius = 4,
  variant = "text",
  lines = 1,
  testID,
}: SkeletonLoaderProps) {
  const br = variant === "circular" ? 9999 : borderRadius;
  const h = variant === "circular" ? width : height;

  if (lines === 1) {
    return (
      <View testID={testID} accessibilityLabel="Loading">
        <SkeletonBlock width={width} height={h} borderRadius={br} />
      </View>
    );
  }

  return (
    <View testID={testID} accessibilityLabel="Loading">
      {Array.from({ length: lines }, (_, i) => (
        <View key={i} style={i < lines - 1 ? { marginBottom: 8 } : undefined}>
          <SkeletonBlock
            width={i === lines - 1 ? "60%" : width}
            height={h}
            borderRadius={br}
          />
        </View>
      ))}
    </View>
  );
}
