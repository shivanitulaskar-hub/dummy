import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  Stack,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { fetchFoodItems } from '../../api/food';
import { addSessionItem, changeSessionItem, removeSessionItem } from '../../api/sessions';
import type { GamingSession } from '../../api/types';
import { formatINR, formatMinutes } from '../../utils/format';
import { MenuPicker } from '../food/MenuPicker';

/** ADD FOOD for a running session: tap items to add; adjust or remove existing lines. */
export function AddFoodDialog({ session, onClose }: { session: GamingSession | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [current, setCurrent] = useState<GamingSession | null>(session);
  useEffect(() => setCurrent(session), [session]);
  const menu = useQuery({ queryKey: ['food-items'], queryFn: () => fetchFoodItems(), enabled: session !== null });

  const mutation = useMutation({
    mutationFn: (action: () => Promise<GamingSession>) => action(),
    onSuccess: (updated) => {
      setCurrent(updated);
      queryClient.invalidateQueries({ queryKey: ['sessions', 'active'] });
      queryClient.invalidateQueries({ queryKey: ['food-items'] });
    },
  });

  const lines = current?.charges?.lines.filter((l) => l.type === 'FOOD') ?? [];
  const id = current?.id ?? 0;

  return (
    <Dialog open={session !== null} onClose={onClose} fullWidth maxWidth="md" TransitionProps={{ onExited: () => mutation.reset() }}>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        Add food · {current?.stationCode}
        {current && current.charges && current.charges.creditMinutes > 0 && (
          <Chip size="small" color="success" label={`${formatMinutes(current.charges.creditMinutes)} free gaming`} />
        )}
      </DialogTitle>
      <DialogContent>
        {mutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {toUserMessage(mutation.error)}
          </Alert>
        )}
        {menu.isError && <Alert severity="error">{toUserMessage(menu.error)}</Alert>}
        <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '3fr 2fr' } }}>
          <MenuPicker
            items={menu.data ?? []}
            disabled={mutation.isPending}
            onPick={(item) => mutation.mutate(() => addSessionItem(id, item.id, 1))}
          />
          <Box>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              On this bill
            </Typography>
            {lines.length === 0 && <Typography color="text.secondary">No food yet. Tap an item to add it.</Typography>}
            <Stack divider={<Divider flexItem />}>
              {lines.map((line) => (
                <Stack key={line.id} direction="row" alignItems="center" spacing={1} sx={{ py: 0.75 }}>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography noWrap fontWeight={600}>
                      {line.description}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {formatINR(line.unitPrice)} × {line.quantity} = {formatINR(line.amount)}
                    </Typography>
                  </Box>
                  <IconButton
                    aria-label="One less"
                    disabled={mutation.isPending}
                    onClick={() =>
                      mutation.mutate(() =>
                        line.quantity > 1 ? changeSessionItem(id, line.id!, line.quantity - 1) : removeSessionItem(id, line.id!),
                      )
                    }
                  >
                    <RemoveIcon />
                  </IconButton>
                  <Typography fontWeight={800} sx={{ minWidth: 20, textAlign: 'center' }}>
                    {line.quantity}
                  </Typography>
                  <IconButton
                    aria-label="One more"
                    disabled={mutation.isPending}
                    onClick={() => mutation.mutate(() => changeSessionItem(id, line.id!, line.quantity + 1))}
                  >
                    <AddIcon />
                  </IconButton>
                  <IconButton aria-label="Remove" disabled={mutation.isPending} onClick={() => mutation.mutate(() => removeSessionItem(id, line.id!))}>
                    <DeleteIcon />
                  </IconButton>
                </Stack>
              ))}
            </Stack>
            {current?.charges && (
              <Stack direction="row" justifyContent="space-between" sx={{ mt: 2 }}>
                <Typography color="text.secondary">Food total</Typography>
                <Typography fontWeight={800}>{formatINR(current.charges.foodAmount)}</Typography>
              </Stack>
            )}
          </Box>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button variant="contained" size="large" onClick={onClose}>
          Done
        </Button>
      </DialogActions>
    </Dialog>
  );
}
