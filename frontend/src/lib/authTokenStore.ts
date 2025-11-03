export type UnauthorizedHandler = () => void | Promise<void>;

const STORAGE_KEY = 'smartattendance.accessToken';

let accessToken: string | null = readStoredToken();
let unauthorizedHandler: UnauthorizedHandler | null = null;
let handlingUnauthorized = false;

export function setAccessToken(token: string | null) {
  accessToken = token;
  persistToken(token);
}

export function getAccessToken() {
  if (accessToken === null) {
    accessToken = readStoredToken();
  }
  return accessToken;
}

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

export async function handleUnauthorized() {
  if (!unauthorizedHandler || handlingUnauthorized) {
    return;
  }

  handlingUnauthorized = true;
  try {
    await unauthorizedHandler();
  } finally {
    handlingUnauthorized = false;
  }
}

function readStoredToken(): string | null {
  if (typeof window === 'undefined' || !window.sessionStorage) {
    return null;
  }
  try {
    const value = window.sessionStorage.getItem(STORAGE_KEY);
    return value && value.length ? value : null;
  } catch (error) {
    console.warn('Unable to read stored session token', error);
    return null;
  }
}

function persistToken(token: string | null) {
  if (typeof window === 'undefined' || !window.sessionStorage) {
    return;
  }
  try {
    if (token) {
      window.sessionStorage.setItem(STORAGE_KEY, token);
    } else {
      window.sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch (error) {
    console.warn('Unable to persist session token', error);
  }
}
