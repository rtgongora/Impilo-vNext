import { vi } from "vitest";

// Minimal React Native shim for Vitest/Vite.
// Prevents Vite from trying to import React Native internal files (e.g. Image) during tests.
vi.mock("react-native", async () => {
  const React = await import("react");

  const createComponent =
    (tag: string) =>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ({ children, testID, ...props }: any) => {
      const domProps = { ...props } as Record<string, unknown>;
      if (testID) domProps["data-testid"] = testID;
      return React.createElement(tag, domProps, children);
    };

  return {
    View: createComponent("div"),
    Text: createComponent("span"),
    ScrollView: createComponent("div"),
    FlatList: createComponent("div"),
    TouchableOpacity: createComponent("button"),
    Pressable: createComponent("button"),
    TextInput: createComponent("input"),
    Image: createComponent("img"),
    ActivityIndicator: createComponent("div"),
    StyleSheet: { create: (s: unknown) => s },
    Platform: { OS: "ios", select: (m: Record<string, unknown>) => (m.ios ?? m.default) },
  };
});

