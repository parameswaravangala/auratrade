import { fileURLToPath, URL } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// The Spring Boot backend (dev profile). Override with AURATRADE_API, e.g. http://localhost:8081.
const backend = process.env.AURATRADE_API ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  server: {
    port: 5173,
    strictPort: true,
    // Same origin in the browser: Vite forwards API calls, so the backend needs no CORS.
    proxy: {
      "/dev": backend,
      "/api": backend,
      "/actuator": backend,
    },
  },
  build: { sourcemap: true, chunkSizeWarningLimit: 900 },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
    restoreMocks: true,
  },
});
