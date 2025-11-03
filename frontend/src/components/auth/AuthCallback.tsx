import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { getCombinedSearchParams } from '@/lib/utils/url';

export default function AuthCallback() {
  const location = useLocation();
  const params = getCombinedSearchParams(location);
  const type = params.get('type');

  if (type === 'recovery') {
    const accessToken = params.get('access_token') ?? '';
    const nextParams = new URLSearchParams();
    if (accessToken.trim()) {
      nextParams.set('access_token', accessToken.trim());
    }
    const nextSearch = nextParams.toString();
    return (
      <Navigate
        to={`/auth/reset/confirm${nextSearch ? `?${nextSearch}` : ''}`}
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  return <Outlet />;
}
