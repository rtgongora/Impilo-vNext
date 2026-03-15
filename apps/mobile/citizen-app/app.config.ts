import { ExpoConfig, ConfigContext } from "expo/config";

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: "Impilo Health",
  slug: "impilo-citizen",
  version: "0.1.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "light",
  scheme: "impilo.citizen",
  splash: {
    image: "./assets/splash.png",
    resizeMode: "contain",
    backgroundColor: "#059669",
  },
  ios: {
    supportsTablet: true,
    bundleIdentifier: "zw.gov.impilo.citizen",
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
      backgroundColor: "#059669",
    },
    package: "zw.gov.impilo.citizen",
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
    "expo-router",
    "expo-secure-store",
    [
      "expo-build-properties",
      {
        android: {
          compileSdkVersion: 34,
          targetSdkVersion: 34,
          minSdkVersion: 24,
          buildToolsVersion: "34.0.0",
        },
        ios: {
          deploymentTarget: "15.1",
        },
      },
    ],
  ],
  extra: {
    keycloakUrl:
      process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "https://auth.impilo.gov.zw",
    keycloakRealm: process.env.EXPO_PUBLIC_KEYCLOAK_REALM ?? "impilo",
    keycloakClientId:
      process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? "citizen-app",
    apiBaseUrl:
      process.env.EXPO_PUBLIC_API_BASE_URL ?? "https://api.impilo.gov.zw",
    redirectUri:
      process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo.citizen://callback",
    eas: {
      projectId: "impilo-citizen",
    },
  },
});
