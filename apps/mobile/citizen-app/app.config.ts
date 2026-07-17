import { ExpoConfig, ConfigContext } from "expo/config";

const VARIANT_NAME_SUFFIX = {
  dev: " (Dev)",
  development: " (Dev)",
  preview: " (Preview)",
  staging: " (Staging)",
  production: "",
} as const;
const VARIANT_BUNDLE_SUFFIX = {
  dev: ".dev",
  development: ".dev",
  preview: ".preview",
  staging: ".staging",
  production: "",
} as const;

type AppVariant = keyof typeof VARIANT_NAME_SUFFIX;

function resolveVariant(raw: string | undefined): AppVariant {
  const normalized = (raw ?? "dev").toLowerCase();
  if (normalized in VARIANT_NAME_SUFFIX) {
    return normalized as AppVariant;
  }
  return "dev";
}

const VARIANT = resolveVariant(process.env.EXPO_PUBLIC_APP_VARIANT);

const APP_NAME =
  process.env.EXPO_PUBLIC_APP_NAME ?? `Impilo${VARIANT_NAME_SUFFIX[VARIANT] ?? ""}`;
const BUNDLE_BASE = "zw.gov.impilo.citizen";
const BUNDLE = `${BUNDLE_BASE}${VARIANT_BUNDLE_SUFFIX[VARIANT] ?? ""}`;

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: APP_NAME,
  slug: "impilo-citizen",
  version: "0.1.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "light",
  scheme: "impilo-citizen",
  splash: {
    image: "./assets/splash.png",
    resizeMode: "contain",
    backgroundColor: "#009739",
  },
  ios: {
    supportsTablet: true,
    bundleIdentifier: BUNDLE,
    buildNumber: "1",
    infoPlist: {
      NSCameraUsageDescription:
        "Used to scan QR codes for health records.",
      NSPhotoLibraryUsageDescription:
        "Used to upload profile photos and documents.",
      NSFaceIDUsageDescription: "Used for biometric authentication.",
      NSMicrophoneUsageDescription:
        "Used for telehealth video consultations.",
    },
  },
  android: {
    adaptiveIcon: {
      foregroundImage: "./assets/adaptive-icon.png",
      backgroundColor: "#009739",
    },
    package: BUNDLE,
    versionCode: 1,
    permissions: [
      "CAMERA",
      "READ_EXTERNAL_STORAGE",
      "USE_BIOMETRIC",
      "USE_FINGERPRINT",
      "INTERNET",
      "ACCESS_NETWORK_STATE",
      "RECORD_AUDIO",
    ],
  },
  plugins: [
    // NOTE: this array REPLACES app.json plugins wholesale — expo-video must be
    // wired here too (LEARNING_RECORDING W4 course player).
    "expo-video",
    "expo-web-browser",
    "expo-secure-store",
    "expo-sqlite",
    "@livekit/react-native-expo-plugin",
    [
      "@config-plugins/react-native-webrtc",
      {
        cameraPermission:
          "Used for telehealth video consultations and to scan QR codes for health records.",
        microphonePermission:
          "Used for telehealth audio and video consultations.",
      },
    ],
    [
      "expo-build-properties",
      {
        android: {
          compileSdkVersion: 35,
          targetSdkVersion: 35,
          minSdkVersion: 24,
          buildToolsVersion: "35.0.0",
        },
        ios: {
          deploymentTarget: "15.1",
        },
      },
    ],
  ],
  extra: {
    appVariant: VARIANT,
    appName: APP_NAME,
    keycloakUrl:
      process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "http://192.168.100.211:8480",
    keycloakRealm: process.env.EXPO_PUBLIC_KEYCLOAK_REALM ?? "impilo",
    keycloakClientId:
      process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? "citizen-app",
    apiBaseUrl:
      process.env.EXPO_PUBLIC_API_BASE_URL ??
      (VARIANT === "preview" ? "https://impilo.mohcc.gov.zw" : "http://192.168.100.211:8160"),
    authBaseUrl:
      process.env.EXPO_PUBLIC_AUTH_BASE_URL ??
      (VARIANT === "preview" ? "https://impilo.mohcc.gov.zw:8480" : process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "http://192.168.100.211:8480"),
    fhirBaseUrl: process.env.EXPO_PUBLIC_FHIR_BASE_URL,
    liveBaseUrl: process.env.EXPO_PUBLIC_LIVE_BASE_URL,
    nompiloBaseUrl: process.env.EXPO_PUBLIC_NOMPILO_BASE_URL,
    webBaseUrl: process.env.EXPO_PUBLIC_WEB_BASE_URL ?? (VARIANT === "preview" ? "https://impilo.mohcc.gov.zw" : undefined),
    redirectUri:
      process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo-citizen://auth/callback",
    eas: {
      projectId: "impilo-citizen",
    },
  },
});
