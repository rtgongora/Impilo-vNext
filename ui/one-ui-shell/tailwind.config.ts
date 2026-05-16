import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        impilo: {
          green: "var(--impilo-green)",
          greenSoft: "var(--impilo-green-soft-logo)",
          yellow: "var(--impilo-yellow)",
          yellowSoft: "var(--accent-yellow-soft)",
          red: "var(--impilo-red)",
          redSoft: "var(--accent-red-soft)",
          charcoal: "var(--impilo-charcoal)",
          surface: "var(--surface)",
          background: "var(--background)",
          border: "var(--border-soft)",
          nompilo: "var(--nompilo)",
          nompiloSoft: "var(--nompilo-soft)",
          50: "#E6F6EC",
          100: "#CFEEDD",
          200: "#B8E4CE",
          300: "#85D3AA",
          400: "#4CBC7D",
          500: "#009739",
          600: "#0F9848",
          700: "#087F35",
          800: "#0A5D2A",
          900: "#0C3F20",
        },
        background: "var(--background)",
        foreground: "var(--text-primary)",
        card: "var(--surface)",
        border: "var(--border-soft)",
        primary: "var(--primary)",
        success: "var(--success)",
        warning: "var(--warning)",
        danger: "var(--danger)",
        muted: "var(--text-muted)",
        assistant: "var(--nompilo)",
      },
      fontFamily: {
        sans: ['"Inter"', "system-ui", "sans-serif"],
        serif: ['"Source Serif 4"', "Georgia", "serif"],
      },
      borderRadius: {
        sm: "0.5rem",
        md: "0.875rem",
        lg: "1.25rem",
        xl: "1.5rem",
        "2xl": "1.75rem",
        "3xl": "2rem",
      },
      boxShadow: {
        "impilo-soft": "var(--shadow-soft)",
        "impilo-card": "var(--shadow-card)",
        "impilo-floating": "var(--shadow-floating)",
        "impilo-nompilo": "var(--shadow-nompilo)",
      },
    },
  },
  plugins: [],
};

export default config;
