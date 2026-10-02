import { useState, type FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { fetchCurrentPlan } from '../../api/pricing';
import { startSession } from '../../api/sessions';
import type { Station } from '../../api/types';

interface Props {
  station: Station | null;
  onClose: () => void;
}

/** Fast path: choose players → START. Customer details are optional. */
export function StartSessionDialog({ station, onClose }: Props) {
  const queryClient = useQueryClient();
  const [players, setPlayers] = useState(1);
  const [mobile, setMobile] = useState('');
  const [name, setName] = useState('');
  const plan = useQuery({ queryKey: ['pricing', 'current'], queryFn: fetchCurrentPlan, enabled: station !== null });
  const maxPlayers = plan.data?.maxPlayers ?? 4;

  const mutation = useMutation({
    mutationFn: () =>
      startSession({
        stationId: station!.id,
        playerCount: players,
        customerMobile: mobile.trim() || undefined,
        customerName: name.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sessions', 'active'] });
      handleClose();
    },
  });

  function handleClose() {
    setPlayers(1);
    setMobile('');
    setName('');
    mutation.reset();
    onClose();
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!mutation.isPending) mutation.mutate();
  }

  return (
    <Dialog open={station !== null} onClose={handleClose} fullWidth maxWidth="xs">
      <Box component="form" onSubmit={handleSubmit}>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          Start session · {station?.code}
          {plan.data && <Chip size="small" label={plan.data.name} variant="outlined" color="primary" />}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2.5} sx={{ pt: 1 }}>
            {mutation.isError && <Alert severity="error">{toUserMessage(mutation.error)}</Alert>}
            {plan.isError && <Alert severity="error">{toUserMessage(plan.error)}</Alert>}
            <Box>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Players
              </Typography>
              <ToggleButtonGroup
                exclusive
                fullWidth
                color="primary"
                value={players}
                onChange={(_, v: number | null) => v && setPlayers(v)}
              >
                {Array.from({ length: maxPlayers }, (_, i) => i + 1).map((n) => (
                  <ToggleButton key={n} value={n} sx={{ py: 1.5, fontSize: '1.25rem', fontWeight: 800 }}>
                    {n}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Box>
            <TextField
              label="Customer mobile (optional)"
              value={mobile}
              onChange={(e) => setMobile(e.target.value)}
              inputMode="tel"
              autoComplete="off"
              placeholder="10-digit number"
            />
            <TextField
              label="Customer name (optional)"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="off"
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button onClick={handleClose} color="inherit" size="large">
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            size="large"
            startIcon={<PlayArrowIcon />}
            disabled={mutation.isPending || plan.isError}
            sx={{ flex: 1 }}
          >
            {mutation.isPending ? 'Starting…' : 'START'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
