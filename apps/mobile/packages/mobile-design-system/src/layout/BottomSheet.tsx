/**
 * BottomSheet — Modal sheet that slides up from the bottom of the screen.
 */

import React from "react";

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
  if (!isOpen) return null;

  return React.createElement(
    "div",
    {
      "data-testid": testID,
      role: "dialog",
      "aria-modal": true,
      "aria-label": title ?? "Bottom sheet",
      style: {
        position: "fixed",
        inset: 0,
        zIndex: 100,
        display: "flex",
        flexDirection: "column" as const,
        justifyContent: "flex-end",
      },
    },
    // Backdrop
    React.createElement("div", {
      onClick: onClose,
      style: { position: "absolute", inset: 0, backgroundColor: "rgba(0,0,0,0.5)" },
      "aria-hidden": true,
    }),
    // Sheet content
    React.createElement(
      "div",
      {
        style: {
          position: "relative",
          backgroundColor: "#FFFFFF",
          borderTopLeftRadius: 16,
          borderTopRightRadius: 16,
          padding: 16,
          maxHeight: "80vh",
          overflow: "auto",
        },
      },
      // Handle bar
      React.createElement("div", {
        style: {
          width: 36,
          height: 4,
          backgroundColor: "#BDBDBD",
          borderRadius: 2,
          margin: "0 auto 12px",
        },
      }),
      title
        ? React.createElement("h2", { style: { fontSize: 17, fontWeight: 600, marginBottom: 12 } }, title)
        : null,
      children
    )
  );
}
