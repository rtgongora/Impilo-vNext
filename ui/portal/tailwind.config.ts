import type { Config } from "tailwindcss";
import impiloPreset from "../shared-ui/tailwind-preset";

const config: Config = {
  presets: [impiloPreset],
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}", "../shared-ui/src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {},
  },
  plugins: [],
};

export default config;
