import type { Location } from 'react-router-dom';

/**
 * Merge query parameters that may be supplied via either the search string
 * (`?foo=bar`) or hash fragment (`#foo=bar`) portions of the location.
 */
export function getCombinedSearchParams(location: Location) {
  const params = new URLSearchParams(location.search);
  const hash = location.hash?.startsWith('#') ? location.hash.slice(1) : location.hash;

  if (hash) {
    const hashParams = new URLSearchParams(hash);
    hashParams.forEach((value, key) => {
      params.set(key, value);
    });
  }

  return params;
}
