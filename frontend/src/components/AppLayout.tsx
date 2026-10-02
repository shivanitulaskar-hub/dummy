import { useEffect, useState } from 'react';
import {
  AppBar,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import MenuIcon from '@mui/icons-material/Menu';
import LogoutIcon from '@mui/icons-material/Logout';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { NAV_ITEMS } from './navigation';

const DRAWER_WIDTH = 232;

function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);
  return (
    <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums', color: 'text.secondary' }}>
      {now.toLocaleString('en-IN', { weekday: 'short', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })}
    </Typography>
  );
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  useEffect(() => setMobileOpen(false), [location.pathname]);

  const items = NAV_ITEMS.filter((item) => !item.roles || (user && item.roles.includes(user.role)));

  const drawer = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar sx={{ gap: 1.5 }}>
        <Box component="img" src="/favicon.svg" alt="" sx={{ width: 32, height: 32 }} />
        <Box>
          <Typography variant="subtitle1" noWrap sx={{ fontWeight: 800, lineHeight: 1.1, letterSpacing: '0.04em' }}>
            CONSOLE ADDA
          </Typography>
          <Typography variant="caption" color="text.secondary">
            PS5 Gaming Cafe
          </Typography>
        </Box>
      </Toolbar>
      <Divider />
      <List sx={{ px: 1, py: 1, flex: 1 }}>
        {items.map((item) => (
          <ListItemButton
            key={item.path}
            component={NavLink}
            to={item.path}
            sx={{
              borderRadius: 2,
              mb: 0.5,
              minHeight: 48,
              '&.active': { bgcolor: 'rgba(59, 130, 246, 0.16)', color: 'primary.light' },
              '&.active .MuiListItemIcon-root': { color: 'primary.light' },
            }}
          >
            <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
          bgcolor: 'rgba(11, 15, 23, 0.85)',
          backdropFilter: 'blur(8px)',
          borderBottom: 1,
          borderColor: 'divider',
        }}
      >
        <Toolbar sx={{ gap: 2 }}>
          {!isDesktop && (
            <IconButton edge="start" color="inherit" aria-label="Open menu" onClick={() => setMobileOpen(true)}>
              <MenuIcon />
            </IconButton>
          )}
          <Box sx={{ flex: 1 }} />
          <Clock />
          {user && (
            <Chip
              label={`${user.fullName} · ${user.role === 'ADMIN' ? 'Admin' : 'Operator'}`}
              color={user.role === 'ADMIN' ? 'primary' : 'default'}
              variant="outlined"
              sx={{ display: { xs: 'none', sm: 'flex' } }}
            />
          )}
          <Tooltip title="Log out">
            <IconButton color="inherit" onClick={logout} aria-label="Log out">
              <LogoutIcon />
            </IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant={isDesktop ? 'permanent' : 'temporary'}
          open={isDesktop || mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', bgcolor: 'background.paper' } }}
        >
          {drawer}
        </Drawer>
      </Box>

      <Box component="main" sx={{ flex: 1, minWidth: 0, p: { xs: 2, sm: 3 } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
