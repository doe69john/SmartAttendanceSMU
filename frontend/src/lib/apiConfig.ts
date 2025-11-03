const FALLBACK_DEV_API_BASE_URL = 'http://localhost:18080/api';
const FALLBACK_PROD_API_BASE_URL = 'https://your-domain.example/api';

function deriveDefaultBaseUrl(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    const origin = window.location.origin.replace(/\/+$/, '');
    return `${origin}/api`;
  }
  return import.meta.env.PROD ? FALLBACK_PROD_API_BASE_URL : FALLBACK_DEV_API_BASE_URL;
}

const DEFAULT_API_BASE_URL = deriveDefaultBaseUrl();

export function isAbsoluteUrl(url: string): boolean {
  return /^https?:\/\//i.test(url);
}

function normalizeBaseUrl(value: string | undefined | null): string {
  const trimmed = value?.trim();
  if (!trimmed) {
    return DEFAULT_API_BASE_URL;
  }

  const withoutTrailingSlash = trimmed.replace(/\/+$/, '');
  if (isAbsoluteUrl(withoutTrailingSlash)) {
    return withoutTrailingSlash;
  }

  if (!withoutTrailingSlash) {
    return '/';
  }

  return withoutTrailingSlash.startsWith('/') ? withoutTrailingSlash : `/${withoutTrailingSlash}`;
}

const rawBaseUrl = import.meta.env.VITE_API_BASE_URL;

export const API_BASE_URL = normalizeBaseUrl(rawBaseUrl ?? DEFAULT_API_BASE_URL);
export const API_BASE_IS_ABSOLUTE = isAbsoluteUrl(API_BASE_URL);

const absoluteBase = API_BASE_IS_ABSOLUTE
  ? new URL(API_BASE_URL.endsWith('/') ? API_BASE_URL : `${API_BASE_URL}/`)
  : undefined;

export const API_BASE_ORIGIN = absoluteBase?.origin;

const RELATIVE_BASE_PATH = (() => {
  if (absoluteBase) {
    const pathname = absoluteBase.pathname.replace(/\/+$/, '');
    return pathname || '/';
  }
  if (API_BASE_URL === '/') {
    return '/';
  }
  return API_BASE_URL.replace(/\/+$/, '') || '/';
})();

function splitPathAndSuffix(path: string): [string, string] {
  const match = path.match(/^[^?#]*/);
  const pathname = match ? match[0] : '';
  const suffix = path.slice(pathname.length);
  return [pathname, suffix];
}

function joinRelativePath(basePath: string, pathname: string): string {
  const trimmedBase = basePath === '/' ? '/' : basePath.replace(/\/+$/, '');
  const normalizedPath = pathname.replace(/^\/+/, '');

  if (!normalizedPath) {
    return trimmedBase || '/';
  }

  const baseWithoutLeading = trimmedBase.replace(/^\/+/, '');
  if (
    baseWithoutLeading.length > 0 &&
    (normalizedPath === baseWithoutLeading || normalizedPath.startsWith(`${baseWithoutLeading}/`))
  ) {
    return `/${normalizedPath}`;
  }

  if (!trimmedBase || trimmedBase === '/') {
    return `/${normalizedPath}`;
  }

  return `${trimmedBase}/${normalizedPath}`;
}

function maybeMakeAbsolute(url: string, absolute?: boolean): string {
  const shouldBeAbsolute = absolute ?? API_BASE_IS_ABSOLUTE;
  if (!shouldBeAbsolute) {
    return url;
  }

  if (isAbsoluteUrl(url)) {
    return url;
  }

  if (typeof window !== 'undefined' && window.location?.origin) {
    const prefix = url.startsWith('/') ? '' : '/';
    return `${window.location.origin}${prefix}${url}`;
  }

  return url;
}

export function resolveApiUrl(path: string, options: { absolute?: boolean } = {}): string {
  const target = path?.trim();

  if (!target) {
    const basePath = absoluteBase ? `${absoluteBase.origin}${RELATIVE_BASE_PATH}` : RELATIVE_BASE_PATH;
    return maybeMakeAbsolute(basePath, options.absolute);
  }

  if (isAbsoluteUrl(target)) {
    return target;
  }

  const [pathname, suffix] = splitPathAndSuffix(target);
  const relativePath = joinRelativePath(RELATIVE_BASE_PATH, pathname);
  const result = absoluteBase ? `${absoluteBase.origin}${relativePath}` : relativePath;

  return maybeMakeAbsolute(`${result}${suffix}`, options.absolute);
}

export { DEFAULT_API_BASE_URL };
