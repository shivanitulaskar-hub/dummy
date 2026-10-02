import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { createPricingPlan, updatePricingPlan } from '../../api/pricing';
import type { PricingPlan, PricingPlanRequest } from '../../api/types';

const today = () => new Date().toISOString().slice(0, 10);

const EMPTY: PricingPlanRequest = {
  name: '',
  dayType: 'WEEKEND',
  activeFrom: today(),
  activeTo: null,
  active: true,
  priority: 0,
  minBillableMinutes: 60,
  billingBlockMinutes: 30,
  graceMinutes: 10,
  graceEveryBoundary: true,
  extensionBlockMinutes: 30,
  extensionPrice: 45,
  additionalPlayerPrice: 50,
  additionalPlayerBasis: 'PER_HOUR',
  maxPlayers: 4,
  notes: null,
  rules: [{ playerCount: 1, durationMinutes: 60, price: 90 }],
};

function toRequest(plan: PricingPlan): PricingPlanRequest {
  return {
    name: plan.name,
    dayType: plan.dayType,
    activeFrom: plan.activeFrom,
    activeTo: plan.activeTo,
    active: plan.active,
    priority: plan.priority,
    minBillableMinutes: plan.minBillableMinutes,
    billingBlockMinutes: plan.billingBlockMinutes,
    graceMinutes: plan.graceMinutes,
    graceEveryBoundary: plan.graceEveryBoundary,
    extensionBlockMinutes: plan.extensionBlockMinutes,
    extensionPrice: plan.extensionPrice,
    additionalPlayerPrice: plan.additionalPlayerPrice,
    additionalPlayerBasis: plan.additionalPlayerBasis,
    maxPlayers: plan.maxPlayers,
    notes: plan.notes,
    rules: plan.rules.map((r) => ({ ...r })),
  };
}

interface Props {
  open: boolean;
  plan: PricingPlan | null;
  onClose: () => void;
}

/** Create or edit a rate card. All validation is repeated server-side; this form only helps. */
export function PricingPlanEditor({ open, plan, onClose }: Props) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<PricingPlanRequest>(EMPTY);

  useEffect(() => {
    if (open) setForm(plan ? toRequest(plan) : { ...EMPTY, activeFrom: today() });
  }, [open, plan]);

  const mutation = useMutation({
    mutationFn: () => (plan ? updatePricingPlan(plan.id, form) : createPricingPlan(form)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pricing'] });
      onClose();
    },
  });

  const set = <K extends keyof PricingPlanRequest>(key: K, value: PricingPlanRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }));
  const num = (v: string) => (v === '' ? 0 : Number(v));

  const setRule = (index: number, key: 'playerCount' | 'durationMinutes' | 'price', value: string) =>
    setForm((f) => ({ ...f, rules: f.rules.map((r, i) => (i === index ? { ...r, [key]: num(value) } : r)) }));

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md" TransitionProps={{ onExited: () => mutation.reset() }}>
      <DialogTitle>{plan ? `Edit ${plan.name}` : 'New pricing plan'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          {plan?.inUseByActiveSession && (
            <Alert severity="warning">
              A session is running on this plan. Changes are blocked until it ends (so its bill can't change), or create
              a new plan instead.
            </Alert>
          )}
          {mutation.isError && <Alert severity="error">{toUserMessage(mutation.error)}</Alert>}

          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr 1fr' } }}>
            <TextField label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} required />
            <TextField select label="Day type" value={form.dayType} onChange={(e) => set('dayType', e.target.value as PricingPlanRequest['dayType'])}>
              <MenuItem value="WEEKDAY">Weekday</MenuItem>
              <MenuItem value="WEEKEND">Weekend (Sat/Sun)</MenuItem>
              <MenuItem value="SPECIAL">Special / event</MenuItem>
            </TextField>
            <TextField label="Priority" type="number" value={form.priority} onChange={(e) => set('priority', num(e.target.value))} helperText="Higher wins" />
            <TextField label="Active from" type="date" value={form.activeFrom} onChange={(e) => set('activeFrom', e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField label="Active to (optional)" type="date" value={form.activeTo ?? ''} onChange={(e) => set('activeTo', e.target.value || null)} slotProps={{ inputLabel: { shrink: true } }} />
            <FormControlLabel control={<Switch checked={form.active} onChange={(e) => set('active', e.target.checked)} />} label="Active" />
          </Box>

          <Typography variant="subtitle1" fontWeight={700}>
            Time rounding
          </Typography>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(4, 1fr)' } }}>
            <TextField label="Minimum (min)" type="number" value={form.minBillableMinutes} onChange={(e) => set('minBillableMinutes', num(e.target.value))} />
            <TextField label="Block (min)" type="number" value={form.billingBlockMinutes} onChange={(e) => set('billingBlockMinutes', num(e.target.value))} />
            <TextField label="Grace (min)" type="number" value={form.graceMinutes} onChange={(e) => set('graceMinutes', num(e.target.value))} />
            <FormControlLabel
              control={<Switch checked={form.graceEveryBoundary} onChange={(e) => set('graceEveryBoundary', e.target.checked)} />}
              label="Grace at every boundary"
            />
          </Box>

          <Typography variant="subtitle1" fontWeight={700}>
            Extensions & players
          </Typography>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(5, 1fr)' } }}>
            <TextField label="Extension block (min)" type="number" value={form.extensionBlockMinutes} onChange={(e) => set('extensionBlockMinutes', num(e.target.value))} />
            <TextField label="Extension price ₹" type="number" value={form.extensionPrice} onChange={(e) => set('extensionPrice', num(e.target.value))} />
            <TextField label="Extra player ₹" type="number" value={form.additionalPlayerPrice} onChange={(e) => set('additionalPlayerPrice', num(e.target.value))} />
            <TextField select label="Extra player basis" value={form.additionalPlayerBasis} onChange={(e) => set('additionalPlayerBasis', e.target.value as PricingPlanRequest['additionalPlayerBasis'])}>
              <MenuItem value="PER_HOUR">Per hour</MenuItem>
              <MenuItem value="PER_SESSION">Per session</MenuItem>
            </TextField>
            <TextField label="Max players" type="number" value={form.maxPlayers} onChange={(e) => set('maxPlayers', num(e.target.value))} />
          </Box>

          <Stack direction="row" alignItems="center" justifyContent="space-between">
            <Typography variant="subtitle1" fontWeight={700}>
              Duration slabs
            </Typography>
            <Button
              startIcon={<AddIcon />}
              onClick={() => setForm((f) => ({ ...f, rules: [...f.rules, { playerCount: 1, durationMinutes: 60, price: 0 }] }))}
            >
              Add slab
            </Button>
          </Stack>
          <Typography variant="caption" color="text.secondary">
            1-player slabs are required. Slabs for other player counts are optional and replace the extra-player charge.
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Players</TableCell>
                <TableCell>Minutes</TableCell>
                <TableCell>Price ₹</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {form.rules.map((rule, i) => (
                <TableRow key={i}>
                  <TableCell>
                    <TextField size="small" type="number" value={rule.playerCount} onChange={(e) => setRule(i, 'playerCount', e.target.value)} />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" type="number" value={rule.durationMinutes} onChange={(e) => setRule(i, 'durationMinutes', e.target.value)} />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" type="number" value={rule.price} onChange={(e) => setRule(i, 'price', e.target.value)} />
                  </TableCell>
                  <TableCell padding="checkbox">
                    <IconButton aria-label="Remove slab" onClick={() => setForm((f) => ({ ...f, rules: f.rules.filter((_, j) => j !== i) }))}>
                      <DeleteIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <TextField label="Notes" value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value || null)} multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={onClose} color="inherit">
          Cancel
        </Button>
        <Button variant="contained" size="large" onClick={() => mutation.mutate()} disabled={mutation.isPending || !form.name.trim()}>
          {mutation.isPending ? 'Saving…' : 'Save plan'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
