/**
 * BottomSheet — Modal sheet that slides up from the bottom of the screen.
 */

import React from "react";
import { View, Text, Pressable, ScrollView, Modal, StyleSheet } from "react-native";
import { useOptionalTheme } from "../theme/ThemeProvider";

export interface BottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  snapPoints?: number[];
  testID?: string;
}

export function BottomSheet({
  isOpen,
  onClose,
  title,
  children,
  testID,
}: BottomSheetProps) {
  const { theme } = useOptionalTheme();
  if (!isOpen) return null;

  return (
    <Modal
      visible={isOpen}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <View testID={testID} style={styles.overlay}>
        {/* Backdrop */}
        <Pressable
          onPress={onClose}
          accessibilityLabel="Close bottom sheet"
          style={[styles.backdrop, { backgroundColor: theme.colors.overlay }]}
        />
        {/* Sheet content */}
        <View
          accessibilityRole="summary"
          accessibilityLabel={title ?? "Bottom sheet"}
          style={[styles.sheet, { backgroundColor: theme.colors.elevatedSurface }]}
        >
          {/* Handle bar */}
          <View style={styles.handleContainer}>
            <View style={[styles.handle, { backgroundColor: theme.colors.navigationBorder }]} />
          </View>
          {title ? <Text style={[styles.title, { color: theme.colors.text }]}>{title}</Text> : null}
          <ScrollView style={styles.content}>
            {children}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: "flex-end",
  },
  backdrop: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: "rgba(0,0,0,0.5)",
  },
  sheet: {
    backgroundColor: "#FFFFFF",
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 16,
    maxHeight: "80%",
  },
  handleContainer: {
    alignItems: "center",
    marginBottom: 12,
  },
  handle: {
    width: 36,
    height: 4,
    backgroundColor: "#BDBDBD",
    borderRadius: 2,
  },
  title: {
    fontSize: 17,
    fontWeight: "600",
    marginBottom: 12,
  },
  content: {
    flexShrink: 1,
  },
});
