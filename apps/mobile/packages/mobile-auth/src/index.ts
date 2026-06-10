/**
 * @impilo/mobile-auth — Authentication & Session Management
 *
 * Keycloak PKCE authentication, token lifecycle, biometric unlock, session persistence.
 * All 4 mobile apps MUST use this package for auth.
 */

// Configuration
export { configureAuth, authStore, resolveActorId } from "./authStore";
export type { AuthState } from "./authStore";

// Keycloak PKCE client
export { KeycloakClient, AuthError, generateCodeVerifier, generateCodeChallenge } from "./keycloakClient";
export type { KeycloakConfig, TokenResponse, UserInfo } from "./keycloakClient";

// Token management
export { TokenManager } from "./tokenManager";
export type { TokenState } from "./tokenManager";

// Secure storage
export { configureSecureStorage, getSecureStorage, STORAGE_KEYS } from "./secureStorage";
export type { SecureStorageAdapter } from "./secureStorage";

// React hooks
export { useAuth, useSession, useAccessToken } from "./hooks";

// Identity & administration navigation (Session Experience Contract parity)
export { resolveMobileIdentityContext } from "./identityContext";
export type { LoginMethod, MobileWorkAssignment } from "./identityContext";
export { MOBILE_ADMIN_NAV_ITEMS, resolveMobileAdministrationNav } from "./administrationGovernanceNav";
export type { MobileAdminNavItem } from "./administrationGovernanceNav";
