/**
 * @see provider-app: same minimal typings for CI `tsc --noEmit` without Expo type entrypoints.
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
