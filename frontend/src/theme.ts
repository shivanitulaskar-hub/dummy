import { createTheme } from '@mui/material/styles';

// Dark gaming-cafe look: deep navy background, electric-blue accent, high contrast, large touch targets.
export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#3b82f6', light: '#60a5fa', dark: '#1d4ed8', contrastText: '#ffffff' },
    secondary: { main: '#22d3ee' },
    success: { main: '#22c55e' },
    error: { main: '#ef4444' },
    warning: { main: '#f59e0b' },
    background: { default: '#0b0f17', paper: '#121826' },
    divider: 'rgba(148, 163, 184, 0.16)',
    text: { primary: '#e5e7eb', secondary: '#94a3b8' },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: [
      'Inter',
      'system-ui',
      '-apple-system',
      'Segoe UI',
      'Roboto',
      'Helvetica Neue',
      'Arial',
      'sans-serif',
    ].join(','),
    h4: { fontWeight: 700, letterSpacing: '-0.01em' },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 700 },
    button: { fontWeight: 700, textTransform: 'none', letterSpacing: '0.02em' },
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { minHeight: 44 },
        sizeLarge: { minHeight: 56, fontSize: '1.05rem', paddingInline: 24 },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: '1px solid rgba(148, 163, 184, 0.16)',
        },
      },
    },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    MuiTextField: { defaultProps: { fullWidth: true } },
  },
});
