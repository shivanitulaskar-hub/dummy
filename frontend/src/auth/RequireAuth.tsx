import { Box, CircularProgress } from '@mui/material';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import type { Role } from '../api/types';
import { useAuth } from './AuthContext';

/** Guards routes: requires login, forces the password-change screen, optionally requires roles. */
export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { user, initializing } = useAuth();
  const location = useLocation();

  if (initializing) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <CircularProgress />
      </Box>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/live" replace />;
  }
  return <Outlet />;
}
