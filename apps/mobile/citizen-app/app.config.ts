import { ExpoConfig, ConfigContext } from "expo/config";

const VARIANT = (process.env.EXPO_PUBLIC_APP_VARIANT ?? "dev").toLowerCase();
const VARIANT_NAME_SUFFIX: Record<string, string> = {
  dev: " Dev",
  development: " Dev",
  preview: " Preview",
  staging: " Staging",
  production: "",
};
const VARIANT_BUNDLE_SUFFIX: Record<string, string> = {
  dev: ".dev",
  development: ".dev",
  preview: ".preview",
  staging: ".staging",
  production: "",
};

const APP_NAME =
  process.env.EXPO_PUBLIC_APP_NAME ?? `Impilo Health${VARIANT_NAME_SUFFIX[VARIANT] ?? ""}`;
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
    "expo-web-browser",
    "expo-secure-store",
    "expo-sqlite",
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
      process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://192.168.100.211:8160",
    redirectUri:
      process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo-citizen://auth/callback",
    eas: {
      projectId: "impilo-citizen",
    },
  },
});
