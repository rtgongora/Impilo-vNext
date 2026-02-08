import type { Config } from "tailwindcss";
const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx}", "../shared-ui/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: { primary: "#1F7A3A", "accent-yellow": "#F2C300", "accent-red": "#D62828", "accent-black": "#111111" },
        success: "#16A34A", warning: "#F59E0B", danger: "#DC2626", info: "#2563EB",
        neutral: { 0: "#FFFFFF", 50: "#F8FAFC", 100: "#F1F5F9", 200: "#E2E8F0", 300: "#CBD5E1", 400: "#94A3B8", 500: "#64748B", 600: "#475569", 700: "#334155", 800: "#1E293B", 900: "#0F172A" },
      },
      fontFamily: { sans: ["Inter", "system-ui", "sans-serif"] },
      borderRadius: { DEFAULT: "12px", lg: "16px" },
      boxShadow: { subtle: "0 1px 2px rgba(0,0,0,0.06)" },
    },
  },
  plugins: [],
};
export default config;
