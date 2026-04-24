/**
 * Minimal typings so `tsc --noEmit` succeeds in CI when Expo's package layout
 * does not surface `@expo/vector-icons` type entrypoints to the workspace root.
 */
declare module "@expo/vector-icons" {
  import type { ComponentType } from "react";
  import type { StyleProp, TextStyle } from "react-native";

  export interface IconProps {
    name: string;
    size?: number;
    color?: string;
    style?: StyleProp<TextStyle>;
  }

  export const Ionicons: ComponentType<IconProps>;
}
