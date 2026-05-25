import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
    "../shared-ui/src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        impilo: {
          primary: "#1e40af",
          secondary: "#059669",
          warning: "#d97706",
          danger: "#dc2626",
          surface: "#f8fafc",
        },
      },
    },
  },
  plugins: [],
};
export default config;
