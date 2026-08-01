export type Experience = "citizen" | "provider";

export const experiencePalettes = {
  citizen: { background: "#F6FBF7", canvas: "#F6FBF7", elevatedSurface: "#FFFFFF", navigationSurface: "#F0FAF3", navigationBorder: "#CFE5D5", navigationMuted: "#52675A", focus: "#006B36" },
  provider: { background: "#F2F8F8", canvas: "#F2F8F8", elevatedSurface: "#FFFFFF", navigationSurface: "#E9F5F4", navigationBorder: "#C7DDDB", navigationMuted: "#4B6463", focus: "#0B5E59" },
} as const;
