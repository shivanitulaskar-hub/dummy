import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { cancelSession, updateSession } from '../../api/sessions';
import type { GamingSession } from '../../api/types';

export function ChangePlayersDialog({
  session,
  maxPlayers,
  onClose,
}: {
  session: GamingSession | null;
  maxPlayers: number;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [players, setPlayers] = useState(1);
  useEffect(() => {
    if (session) setPlayers(session.playerCount);
  }, [session]);
  const mutation = useMutation({
    mutationFn: () => updateSession(session!.id, { playerCount: players }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessions', 'active'] });
      onClose();
    },
  });
  return (
    <Dialog open={session !== null} onClose={onClose} fullWidth maxWidth="xs" TransitionProps={{ onExited: () => mutation.reset() }}>
      <DialogTitle>Change players · {session?.stationCode}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {mutation.isError && <Alert severity="error">{toUserMessage(mutation.error)}</Alert>}
          <ToggleButtonGroup exclusive fullWidth color="primary" value={players} onChange={(_, v) => v && setPlayers(v)}>
            {Array.from({ length: maxPlayers }, (_, i) => i + 1).map((n) => (
              <ToggleButton key={n} value={n} sx={{ py: 1.5, fontSize: '1.25rem', fontWeight: 800 }}>
                {n}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={onClose} color="inherit">
          Back
        </Button>
        <Button variant="contained" size="large" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function CancelSessionDialog({ session, onClose }: { session: GamingSession | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const mutation = useMutation({
    mutationFn: () => cancelSession(session!.id, reason.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessions', 'active'] });
      onClose();
    },
  });
  return (
    <Dialog
      open={session !== null}
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      TransitionProps={{ onExited: () => { setReason(''); mutation.reset(); } }}
    >
      <DialogTitle>Cancel session · {session?.stationCode}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="warning">
            No bill is created for a cancelled session. Use this only for mistakes (wrong PS5, started by accident).
          </Alert>
          {mutation.isError && <Alert severity="error">{toUserMessage(mutation.error)}</Alert>}
          <TextField
            label="Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
            autoFocus
            inputProps={{ maxLength: 255 }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={onClose} color="inherit">
          Back
        </Button>
        <Button
          variant="contained"
          color="error"
          size="large"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending || !reason.trim()}
        >
          Cancel session
        </Button>
      </DialogActions>
    </Dialog>
  );
}
