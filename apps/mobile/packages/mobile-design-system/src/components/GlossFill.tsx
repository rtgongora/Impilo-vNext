import React from "react";
import { StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";
import { glossSheen, mix } from "../tokens/gloss";

/**
 * Fills a container with the gloss gradient — a lighter-to-base ramp plus the inner top
 * highlight that makes a filled surface read as lit rather than painted.
 *
 * Same band technique as {@link GlossCanvas} and for the same reason: React Native has no
 * gradient primitive, and no gradient library is usable across both apps
 * (`react-native-svg` is a citizen-app dependency only, while this package is shared with
 * provider-app). Bands need no dependency and cannot fail to render.
 *
 * The caller keeps its own solid `backgroundColor` underneath, so the surface is still the
 * right colour if this layer never lays out — the gradient is decoration, not information.
 *
 * The parent must set `overflow: "hidden"` for the fill to respect its border radius, and
 * the content must sit in a `position: "relative"` sibling so it stacks above this layer.
 */
export function GlossFill({
  from,
  to,
  bands = 10,
  sheen = true,
  style,
  testID,
}: {
  /** Top stop — usually `glossButtonStops(accent).from`. */
  from: string;
  /** Bottom stop — the surface's own base colour. */
  to: string;
  bands?: number;
  /** The 1dp inner top highlight. */
  sheen?: boolean;
  style?: StyleProp<ViewStyle>;
  testID?: string;
}) {
  if (from === to) return null;

  return (
    <>
      <View style={[StyleSheet.absoluteFill, style]} pointerEvents="none" testID={testID}>
        {Array.from({ length: bands }, (_, i) => (
          <View
            key={i}
            style={{ flex: 1, backgroundColor: mix(from, to, (i + 0.5) / bands) }}
          />
        ))}
      </View>
      {sheen ? <View style={styles.sheen} pointerEvents="none" /> : null}
    </>
  );
}

const styles = StyleSheet.create({
  sheen: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    height: 1,
    backgroundColor: glossSheen,
  },
});
