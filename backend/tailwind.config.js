/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js"
  ],
  theme: {
    extend: {
      colors: {
        background: {
          DEFAULT: "#111112",
          secondary: "#171718",
          tertiary: "#1d1d1f",
        },
        surface: {
          DEFAULT: "#1b1b1d",
          hover: "#232325",
          active: "#2a2a2d",
          border: "#303033",
        },
        accent: {
          DEFAULT: "#e8c84a",
          hover: "#f0d45a",
          muted: "#e8c84a26",
          foreground: "#0f0f11",
        },
        foreground: {
          DEFAULT: "#eeeeef",
          muted: "#a2a2a6",
          subtle: "#707075",
        },
        success: {
          DEFAULT: "#34d399",
          bg: "#34d39920",
        },
        warning: {
          DEFAULT: "#fbbf24",
          bg: "#fbbf2420",
        },
        danger: {
          DEFAULT: "#f87171",
          bg: "#f8717120",
        },
        info: {
          DEFAULT: "#60a5fa",
          bg: "#60a5fa20",
        },
      },
      fontFamily: {
        sans: ["Aptos", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "monospace"],
      },
      borderRadius: {
        sm: "6px",
        DEFAULT: "8px",
        md: "10px",
        lg: "10px",
        xl: "10px",
      },
      boxShadow: {
        subtle: "0 1px 2px rgba(0,0,0,0.18)",
        card: "0 2px 8px rgba(0,0,0,0.22)",
        glow: "0 2px 8px rgba(0,0,0,0.22)",
      },
      animation: {
        "fade-in": "fadeIn 0.12s ease-out",
        "slide-in": "fadeIn 0.12s ease-out",
        "slide-up": "fadeIn 0.12s ease-out",
        pulse: "pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite",
      },
      keyframes: {
        fadeIn: {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        slideIn: {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        slideUp: {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
      },
    },
  },
  plugins: [],
}
