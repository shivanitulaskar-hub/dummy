import { useState, type FormEvent } from 'react';
import { Alert, Box, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { toUserMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to={user.mustChangePassword ? '/change-password' : '/live'} replace />;
  }

  const from = (location.state as { from?: string } | null)?.from;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const loggedIn = await login(username.trim(), password);
      navigate(loggedIn.mustChangePassword ? '/change-password' : from && from !== '/login' ? from : '/live', {
        replace: true,
      });
    } catch (err) {
      setError(toUserMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        p: 2,
        background: 'radial-gradient(ellipse at top, rgba(59,130,246,0.25), transparent 60%), #0b0f17',
      }}
    >
      <Card sx={{ width: '100%', maxWidth: 420 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Stack spacing={1} alignItems="center" sx={{ mb: 3 }}>
            <Box component="img" src="/favicon.svg" alt="" sx={{ width: 56, height: 56 }} />
            <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '0.08em' }}>
              CONSOLE ADDA
            </Typography>
            <Typography variant="body2" color="text.secondary">
              PS5 Gaming Cafe · Counter Login
            </Typography>
          </Stack>
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Stack spacing={2}>
              {error && <Alert severity="error">{error}</Alert>}
              <TextField
                label="Username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                autoFocus
                required
              />
              <TextField
                label="Password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
              <Button type="submit" variant="contained" size="large" disabled={submitting || !username || !password}>
                {submitting ? 'Signing in…' : 'Sign in'}
              </Button>
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
