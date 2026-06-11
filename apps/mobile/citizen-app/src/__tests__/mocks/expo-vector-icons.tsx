import React from "react";

type IconProps = {
  testID?: string;
  name?: string;
  size?: number;
  color?: string;
};

function MockIcon({ testID, name }: IconProps) {
  return React.createElement("span", {
    "data-testid": testID ?? "mock-icon",
    "data-icon-name": name ?? "",
  });
}

export const Ionicons = MockIcon;
export const AntDesign = MockIcon;
export const MaterialIcons = MockIcon;
export const FontAwesome = MockIcon;
