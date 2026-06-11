/**
 * Safe API result wrapper — typed success/failure without throwing on HTTP errors.
 */

export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; status?: number; message: string; code?: string };

export async function safeApi<T>(request: () => Promise<Response>): Promise<ApiResult<T>> {
  try {
    const response = await request();

    if (!response.ok) {
      return {
        ok: false,
        status: response.status,
        message: `Request failed with status ${response.status}`,
      };
    }

    return {
      ok: true,
      data: (await response.json()) as T,
    };
  } catch (error) {
    return {
      ok: false,
      message: error instanceof Error ? error.message : "Unknown network error",
    };
  }
}
