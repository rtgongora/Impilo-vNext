import React from "react";
import { StyleSheet, View, type StyleProp, type ViewStyle } from "react-native";
import { glossFloor, rampBands } from "../tokens/gloss";

/**
 * The living canvas — the light-to-deep teal ramp the gloss direction is built on.
 *
 * **Why bands rather than a real gradient.** React Native has no CSS gradients.
 * `expo-linear-gradient` and `expo-blur` are installed in neither app, and while
 * `react-native-svg` is a dependency of **citizen-app**, it is NOT a dependency of
 * **provider-app** — importing it here would have broken every button and screen in the
 * provider app at runtime, since this package is shared by both. Adding it to
 * provider-app is not a package.json edit either: it is a native module, so it would need
 * an `expo prebuild` and a native rebuild that cannot be verified from this environment.
 *
 * So the ramp is painted as a stack of flat `View`s, each one step of the ramp. With the
 * default band count the steps are ~13dp of a very gradual teal ramp, which reads as a
 * gradient rather than as stripes. It costs no new dependency, behaves identically on
 * both apps, and cannot fail to render.
 *
 * **The ramp is anchored in dp, never in fractions.** Bands are laid out from the top in
 * absolute dp and the floor fills everything below the last one, so growth extends the
 * dark floor instead of sliding the hand-over zone up through the text. That is the exact
 * defect the web pass had to correct, carried over as a property of the component.
 */
export function GlossCanvas({
  children,
  style,
  /** Where the ramp's last stop lands, in dp. Content below simply sits on the floor. */
  rampHeight = 620,
  /** Number of steps used to paint the ramp. More is smoother and costs more views. */
  bands = 48,
  testID = "gloss-canvas",
}: {
  children?: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  rampHeight?: number;
  bands?: number;
  testID?: string;
}) {
  const steps = rampBands(rampHeight, bands);

  return (
    <View style={[styles.root, style]} testID={testID}>
      {/* Floor first: everything below the ramp, and the ground if a band fails to lay out. */}
      <View
        style={[StyleSheet.absoluteFill, { backgroundColor: glossFloor }]}
        pointerEvents="none"
        testID={`${testID}-floor`}
      />
      <View style={StyleSheet.absoluteFill} pointerEvents="none" testID={`${testID}-ramp`}>
        {steps.map((band) => (
          <View
            key={band.top}
            style={{
              position: "absolute",
              top: band.top,
              left: 0,
              right: 0,
              height: band.height,
              backgroundColor: band.color,
            }}
          />
        ))}
      </View>
      <View style={styles.content}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { position: "relative", overflow: "hidden" },
  content: { position: "relative" },
});
