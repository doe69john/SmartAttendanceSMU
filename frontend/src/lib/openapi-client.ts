import createClient, { type FetchResponse } from 'openapi-fetch';

import { ApiError } from './apiClient';
import { getAccessToken, handleUnauthorized } from './authTokenStore';

import { API_BASE_ORIGIN, resolveApiUrl } from './apiConfig';

import type { paths } from './generated/openapi-types';

export function buildApiUrl(path: string, { absolute }: { absolute?: boolean } = {}): string {
  return resolveApiUrl(path, { absolute });
}

const OPEN_API_BASE_URL = API_BASE_ORIGIN ?? '';

export const openApiClient = createClient<paths>({ baseUrl: OPEN_API_BASE_URL });


openApiClient.use({
  async onRequest({ request }) {
    const headers = new Headers(request.headers);
    const token = getAccessToken();

    if (token && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    if (!headers.has('Accept')) {
      headers.set('Accept', 'application/json');
    }

    return new Request(request, {
      headers,
      credentials: 'include',
    });
  },
  async onResponse({ response }) {
    if (response.status === 401) {
      await handleUnauthorized();
    }
  },
});

export function unwrapOpenApiResponse<TSpec, TOptions, TMedia>(
  result: FetchResponse<TSpec, TOptions, TMedia>,
  fallbackMessage = 'Request failed',
): FetchResponse<TSpec, TOptions, TMedia>["data"] {
  if ('error' in result && result.error) {
    const { error, response } = result;
    let message = fallbackMessage;

    if (typeof error === 'string' && error.trim().length) {
      message = error;
    } else if (error && typeof error === 'object') {
      const detail = (error as Record<string, unknown>).detail;
      const errorMessage = (error as Record<string, unknown>).message;
      if (typeof detail === 'string' && detail.trim().length) {
        message = detail;
      } else if (typeof errorMessage === 'string' && errorMessage.trim().length) {
        message = errorMessage;
      }
    }

    throw new ApiError(message, response.status ?? 500, error);
  }

  return result.data as FetchResponse<TSpec, TOptions, TMedia>["data"];
}
