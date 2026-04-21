import { ExpoConfig, ConfigContext } from "expo/config";

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: "Impilo Provider",
  slug: "impilo-provider",
  version: "0.1.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "light",
  scheme: "impilo.provider",
  splash: {
    image: "./assets/splash.png",
    resizeMode: "contain",
    backgroundColor: "#009739",
  },
  ios: {
    supportsTablet: true,
    bundleIdentifier: "zw.gov.impilo.provider",
    buildNumber: "1",
    infoPlist: {
      NSCameraUsageDescription:
        "Used for scanning patient IDs and capturing clinical images.",
      NSLocationWhenInUseUsageDescription:
        "Used to record outreach visit locations.",
      NSFaceIDUsageDescription: "Used for biometric authentication.",
    },
  },
  android: {
    adaptiveIcon: {
      foregroundImage: "./assets/adaptive-icon.png",
      backgroundColor: "#009739",
    },
    package: "zw.gov.impilo.provider",
    versionCode: 1,
    permissions: [
      "CAMERA",
      "ACCESS_FINE_LOCATION",
      "ACCESS_COARSE_LOCATION",
      "USE_BIOMETRIC",
      "USE_FINGERPRINT",
      "INTERNET",
      "ACCESS_NETWORK_STATE",
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
      process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? "provider-app",
    apiBaseUrl:
      process.env.EXPO_PUBLIC_API_BASE_URL ?? "https://api.impilo.gov.zw",
    redirectUri:
      process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo.provider://callback",
    eas: {
      projectId: "impilo-provider",
    },
  },
});
