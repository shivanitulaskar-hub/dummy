import { useState, type FormEvent } from 'react';
import { Alert, Box, Button, Card, CardContent, Stack, TextField, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { changePassword } from '../api/auth';
import { toUserMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';

const POLICY = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

export function ChangePasswordPage() {
  const { user, applyLogin, logout } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const policyOk = POLICY.test(next);
  const matches = next === confirm;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      applyLogin(await changePassword(current, next));
      navigate('/live', { replace: true });
    } catch (err) {
      setError(toUserMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2 }}>
      <Card sx={{ width: '100%', maxWidth: 460 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Typography variant="h5" gutterBottom>
            Change password
          </Typography>
          {user?.mustChangePassword && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              You must set a new password before using the system.
            </Alert>
          )}
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Stack spacing={2}>
              {error && <Alert severity="error">{error}</Alert>}
              <TextField
                label="Current password"
                type="password"
                value={current}
                onChange={(e) => setCurrent(e.target.value)}
                autoComplete="current-password"
                autoFocus
              />
              <TextField
                label="New password"
                type="password"
                value={next}
                onChange={(e) => setNext(e.target.value)}
                autoComplete="new-password"
                error={next.length > 0 && !policyOk}
                helperText="At least 8 characters, with a letter and a digit"
              />
              <TextField
                label="Confirm new password"
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                autoComplete="new-password"
                error={confirm.length > 0 && !matches}
                helperText={confirm.length > 0 && !matches ? 'Passwords do not match' : ' '}
              />
              <Button
                type="submit"
                variant="contained"
                size="large"
                disabled={submitting || !current || !policyOk || !matches}
              >
                {submitting ? 'Saving…' : 'Save new password'}
              </Button>
              <Button onClick={logout} color="inherit">
                Log out
              </Button>
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
