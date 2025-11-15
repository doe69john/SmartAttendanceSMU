// Utilities for talking to the locally running native companion app. The
// companion exposes an HTTP bridge on the same device, so this logic must live
// in the web client rather than the Java backend.
const DEFAULT_BRIDGE_URL = 'http://127.0.0.1:4455';

export const COMPANION_HANDSHAKE_STORAGE_KEY = 'companion:handshakeToken';
export const COMPANION_LAST_SESSION_STORAGE_KEY = 'companion:lastSessionId';

export interface CompanionHandshakeResult {
  status: 'healthy' | 'unreachable';
  version?: string;
  token?: string;
  message?: string;
}

export interface CompanionSessionPayload {
  sessionId: string;
  sectionId?: string;
  backendBaseUrl?: string;
  modelUrl: string;
  cascadeUrl: string;
  labelsUrl?: string;
  missingStudentIds?: string[];
  labels?: Record<string, string>;
  authToken?: string;
  scheduledStart?: string;
  scheduledEnd?: string;
  lateThresholdMinutes?: number;
}

export interface CompanionSessionStatus {
  status: string;
  active: boolean;
  sessionId?: string | null;
  sectionId?: string | null;
  modelPath?: string | null;
  cascadePath?: string | null;
  labelsPath?: string | null;
  startedAt?: string | null;
  lastHeartbeat?: string | null;
}

function normalizeBaseUrl(url: string | undefined): string {
  if (!url) return DEFAULT_BRIDGE_URL;
  return url.endsWith('/') ? url.slice(0, -1) : url;
}

export function detectMobileDevice(): boolean {
  if (typeof window === 'undefined') return false;
  const nav = window.navigator as Navigator & { maxTouchPoints?: number };
  const ua = nav?.userAgent ?? '';
  const touchPoints = typeof nav?.maxTouchPoints === 'number' ? nav.maxTouchPoints : 0;
  const isTouchCapable = 'ontouchstart' in window || touchPoints > 1;
  const mobileRegex = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i;
  const isMobileUa = mobileRegex.test(ua);
  const narrowViewport = window.innerWidth <= 900;
  return Boolean(isMobileUa || (isTouchCapable && narrowViewport));
}

export function detectSupportedDesktopDevice(): boolean {
  if (typeof window === 'undefined') return false;
  if (detectMobileDevice()) return false;

  const navigatorWithUaData = window.navigator as Navigator & {
    userAgentData?: { platform?: string };
  };

  const ua = (navigatorWithUaData.userAgent ?? '').toLowerCase();
  const platform = (
    navigatorWithUaData.userAgentData?.platform ?? navigatorWithUaData.platform ?? ''
  ).toLowerCase();

  const combined = `${ua} ${platform}`;

  const supportsDesktopJava = /macintosh|mac os x|macos|windows nt|win32|win64/.test(combined);
  const supportsLinuxDesktop = /linux/.test(combined) && !/android/.test(combined);

  return supportsDesktopJava || supportsLinuxDesktop;
}

export async function performCompanionHandshake(): Promise<CompanionHandshakeResult> {
  const baseUrl = normalizeBaseUrl(import.meta.env.VITE_COMPANION_BRIDGE_URL as string | undefined);
  const handshakeUrl = `${baseUrl}/handshake`;
  const healthUrl = `${baseUrl}/health`;

  try {
    const response = await fetch(handshakeUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
      },
      body: JSON.stringify({ source: 'web-dashboard' }),
    });

    if (!response.ok) {
      throw new Error(`Handshake failed with status ${response.status}`);
    }

    const payload = await response.json().catch(() => ({}));
    const token = typeof payload?.token === 'string' ? payload.token : undefined;
    const version = typeof payload?.version === 'string' ? payload.version : undefined;
    const message = typeof payload?.message === 'string' ? payload.message : undefined;

    if (!token) {
      const reason = message?.trim().length
        ? message
        : 'Companion handshake did not return an access token.';
      return {
        status: 'unreachable',
        version,
        message: reason,
      };
    }

    return {
      status: 'healthy',
      token,
      version,
      message,
    };
  } catch (handshakeError) {
    try {
      const response = await fetch(healthUrl, { method: 'GET', headers: { Accept: 'application/json' } });
      if (!response.ok) {
        throw new Error(`Health check failed with status ${response.status}`);
      }
      const payload = await response.json().catch(() => ({}));
      const version = typeof payload?.version === 'string' ? payload.version : undefined;
      const handshakeMessage =
        handshakeError instanceof Error
          ? handshakeError.message
          : 'Unable to reach companion handshake endpoint';
      const healthMessage = typeof payload?.message === 'string' ? payload.message : undefined;
      const combinedMessage = healthMessage?.trim().length
        ? `${handshakeMessage}; ${healthMessage}`
        : handshakeMessage;
      return {
        status: 'unreachable',
        version,
        message: combinedMessage,
      };
    } catch (healthError) {
      const originalMessage =
        handshakeError instanceof Error ? handshakeError.message : 'Unable to reach companion handshake endpoint';
      const fallbackMessage =
        healthError instanceof Error ? healthError.message : 'Unable to reach companion health endpoint';
      return {
        status: 'unreachable',
        message: `${originalMessage}; ${fallbackMessage}`,
      };
    }
  }
}

async function companionFetch<T>(
  path: string,
  options: RequestInit & { token?: string } = {}
): Promise<T> {
  const baseUrl = normalizeBaseUrl(import.meta.env.VITE_COMPANION_BRIDGE_URL as string | undefined);
  const method = options.method ?? 'GET';
  const headers: HeadersInit = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    ...(options.headers ?? {})
  };
  if (options.token) {
    headers['X-Companion-Token'] = options.token;
  }
  if (import.meta.env.DEV) {
    console.info('[companion] request', {
      path,
      method,
      token: maskToken(options.token),
      body: previewPayload(options.body),
    });
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: options.body,
  });

  const contentType = response.headers.get('Content-Type') ?? '';
  const text = await response.text();
  const isJson = contentType.includes('application/json');
  const responsePreview = isJson ? safeJsonPreview(text) : undefined;

  if (import.meta.env.DEV) {
    const log = response.ok ? console.info : console.warn;
    log('[companion] response', {
      path,
      method,
      status: response.status,
      statusText: response.statusText,
      body: responsePreview,
    });
  }

  if (!response.ok) {
    throw new Error(text || `Companion request failed with status ${response.status}`);
  }
  if (response.status === 204 || !text.trim()) {
    return undefined as T;
  }

  try {
    return JSON.parse(text) as T;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error';
    throw new Error(`Failed to parse companion response JSON: ${message}`);
  }
}

export async function startCompanionSession(token: string, payload: CompanionSessionPayload) {
  if (!token) {
    throw new Error('Companion token required to start a session');
  }
  return companionFetch('/session/start', {
    method: 'POST',
    token,
    body: JSON.stringify(payload)
  });
}

export async function stopCompanionSession(token: string) {
  if (!token) {
    throw new Error('Companion token required to stop a session');
  }
  return companionFetch('/session/stop', { method: 'POST', token });
}

function maskToken(token?: string): string {
  if (!token) {
    return '(absent)';
  }
  const trimmed = token.trim();
  if (trimmed.length <= 8) {
    return '***';
  }
  return `${trimmed.slice(0, 4)}…${trimmed.slice(-4)}`;
}

function previewPayload(body: RequestInit['body'] | undefined): unknown {
  if (typeof body === 'string') {
    const json = safeJsonPreview(body);
    if (json !== undefined) {
      return json;
    }
    return body.length > 500 ? `${body.slice(0, 500)}…` : body;
  }
  return body === undefined ? undefined : '[non-string payload]';
}

function safeJsonPreview(payload: string): unknown {
  if (!payload) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(payload);
    return truncateJson(parsed);
  } catch {
    return undefined;
  }
}

function truncateJson(value: unknown, depth = 0): unknown {
  if (depth > 2) {
    return '[truncated]';
  }
  if (Array.isArray(value)) {
    return value.slice(0, 5).map((item) => truncateJson(item, depth + 1));
  }
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {};
    let count = 0;
    for (const [key, val] of Object.entries(value as Record<string, unknown>)) {
      result[key] = truncateJson(val, depth + 1);
      count += 1;
      if (count >= 10) {
        result['__truncated__'] = true;
        break;
      }
    }
    return result;
  }
  return value;
}

export async function shutdownCompanion(token: string) {
  if (!token) {
    throw new Error('Companion token required to shutdown the companion app');
  }
  return companionFetch('/application/shutdown', { method: 'POST', token });
}

export async function getCompanionSessionStatus(token?: string): Promise<CompanionSessionStatus> {
  return companionFetch<CompanionSessionStatus>('/session/status', {
    method: 'GET',
    token
  });
}
