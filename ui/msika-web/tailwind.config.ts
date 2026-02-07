import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: "#0f766e", light: "#14b8a6", dark: "#134e4a" },
        surface: { DEFAULT: "#f8fafc", alt: "#f1f5f9" },
      },
    },
  },
  plugins: [],
};
export default config;
