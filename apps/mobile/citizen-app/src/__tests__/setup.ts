import { vi } from "vitest";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Minimal React Native shim for Vitest/Vite.
// Prevents Vite from trying to import React Native internal files (e.g. Image) during tests.
vi.mock("react-native", async () => {
  const React = await import("react");

  const createComponent =
    (tag: string) =>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ({ children, testID, contentContainerStyle: _contentContainerStyle, refreshControl: _refreshControl, ...props }: any) => {
      const domProps = { ...props } as Record<string, unknown>;
      if (testID) domProps["data-testid"] = testID;
      return React.createElement(tag, domProps, children);
    };

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const TextInput = ({ testID, onChangeText, value, placeholder, multiline: _multiline, ...props }: any) => {
    const domProps = {
      ...props,
      value,
      placeholder,
      "data-testid": testID,
      onChange: (event: { target: { value: string } }) => onChangeText?.(event.target.value),
      onInput: (event: { target: { value: string } }) => onChangeText?.(event.target.value),
    };
    return React.createElement("input", domProps);
  };

  return {
    View: createComponent("div"),
    Text: createComponent("span"),
    ScrollView: createComponent("div"),
    FlatList: createComponent("div"),
    TouchableOpacity: createComponent("button"),
    Pressable: createComponent("button"),
    RefreshControl: createComponent("div"),
    TextInput,
    Image: createComponent("img"),
    ActivityIndicator: createComponent("div"),
    StyleSheet: { create: (s: unknown) => s },
    Platform: { OS: "ios", select: (m: Record<string, unknown>) => (m.ios ?? m.default) },
  };
});

// Expo icon package pulls runtime-specific internals in Node test environments.
// Provide a stable test double so screen tests can render icon usages.
vi.mock("@expo/vector-icons", async () => {
  const React = await import("react");

  const Icon = ({
    testID,
    name,
  }: {
    testID?: string;
    name?: string;
  }) => React.createElement("span", { "data-testid": testID ?? "mock-icon", "data-icon-name": name ?? "" });

  return {
    Ionicons: Icon,
    AntDesign: Icon,
  };
});

vi.mock("expo-web-browser", () => ({
  openBrowserAsync: vi.fn(),
}));

vi.mock("@livekit/react-native", async () => {
  const React = await import("react");

  return {
    registerGlobals: vi.fn(),
    LiveKitRoom: ({ children }: { children: React.ReactNode }) => React.createElement("div", {}, children),
    VideoTrack: ({ testID }: { testID?: string }) =>
      React.createElement("div", { "data-testid": testID ?? "mock-livekit-video-track" }),
    useConnectionState: () => "connected",
    useRoomContext: () => ({
      localParticipant: {
        setMicrophoneEnabled: vi.fn(),
        setCameraEnabled: vi.fn(),
      },
    }),
    useTracks: () => [],
  };
});
