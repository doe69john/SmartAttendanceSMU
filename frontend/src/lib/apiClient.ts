import { getAccessToken, handleUnauthorized } from './authTokenStore';
import { resolveApiUrl } from './apiConfig';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiRequestOptions {
  path: string;
  method: HttpMethod;
  body?: unknown;
  headers?: Record<string, string>;
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly details: unknown;

  constructor(message: string, status: number, details?: unknown) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

async function parseResponseBody(response: Response) {
  const contentType = response.headers.get('content-type');
  if (contentType?.includes('application/json')) {
    try {
      return await response.json();
    } catch (error) {
      console.warn('Failed to parse JSON response', error);
      return null;
    }
  }

  try {
    const text = await response.text();
    return text.length ? text : null;
  } catch (error) {
    console.warn('Failed to read response body', error);
    return null;
  }
}

function isFormData(body: unknown): body is FormData {
  return typeof FormData !== 'undefined' && body instanceof FormData;
}

export async function apiRequest<T = unknown>({ path, method, body, headers = {} }: ApiRequestOptions): Promise<T> {
  const token = getAccessToken();
  const requestHeaders: Record<string, string> = {
    ...headers,
  };

  if (token) {
    requestHeaders.Authorization = 'Bearer ' + token;
  }

  let requestBody: BodyInit | undefined;
  if (body !== undefined) {
    if (isFormData(body)) {
      requestBody = body;
      delete requestHeaders['Content-Type'];
    } else if (body instanceof Blob || body instanceof ArrayBuffer || body instanceof URLSearchParams) {
      requestBody = body as BodyInit;
    } else if (typeof body === 'string') {
      requestHeaders['Content-Type'] = requestHeaders['Content-Type'] ?? 'text/plain';
      requestBody = body;
    } else {
      requestHeaders['Content-Type'] = requestHeaders['Content-Type'] ?? 'application/json';
      requestBody = JSON.stringify(body);
    }
  }

  const requestUrl = resolveApiUrl(path, { absolute: true });

  const response = await fetch(requestUrl, {
    method,
    headers: requestHeaders,
    body: requestBody,
    credentials: 'include',
  });

  if (response.status === 401) {
    await handleUnauthorized();
    throw new ApiError('Unauthorized', response.status);
  }

  if (!response.ok) {
    const responseBody = await parseResponseBody(response);
    let message: string | undefined;
    if (typeof responseBody === 'string' && responseBody.trim().length) {
      message = responseBody;
    } else if (responseBody && typeof responseBody === 'object' && 'message' in responseBody) {
      const value = (responseBody as Record<string, unknown>).message;
      if (typeof value === 'string' && value.trim().length) {
        message = value;
      }
    }
    if (!message || !message.trim().length) {
      message = response.statusText || 'Request failed';
    }
    throw new ApiError(message, response.status, responseBody);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const responseBody = await parseResponseBody(response);
  return responseBody as T;
}
