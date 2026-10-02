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
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { createDiscount, fetchDiscounts, updateDiscount } from '../../api/settings';
import type { Discount, DiscountRequest } from '../../api/types';
import { formatINR } from '../../utils/format';

const EMPTY: DiscountRequest = { code: '', name: '', discountType: 'PERCENTAGE', value: 10, maxAmount: null, validFrom: null, validTo: null, active: true };

function DiscountDialog({ open, discount, onClose }: { open: boolean; discount: Discount | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<DiscountRequest>(EMPTY);
  useEffect(() => {
    if (open) setForm(discount ? { ...discount } : EMPTY);
  }, [open, discount]);
  const save = useMutation({
    mutationFn: () => (discount ? updateDiscount(discount.id, form) : createDiscount(form)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['discounts'] });
      onClose();
    },
  });
  const set = <K extends keyof DiscountRequest>(k: K, v: DiscountRequest[K]) => setForm((f) => ({ ...f, [k]: v }));
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" TransitionProps={{ onExited: () => save.reset() }}>
      <DialogTitle>{discount ? `Edit ${discount.code}` : 'New discount'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 2fr' } }}>
            <TextField label="Code" value={form.code} onChange={(e) => set('code', e.target.value)} placeholder="STUDENT10" />
            <TextField label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Student 10%" />
            <TextField select label="Type" value={form.discountType} onChange={(e) => set('discountType', e.target.value as DiscountRequest['discountType'])}>
              <MenuItem value="PERCENTAGE">Percentage</MenuItem>
              <MenuItem value="FLAT">Flat amount</MenuItem>
            </TextField>
            <TextField label={form.discountType === 'PERCENTAGE' ? 'Percent %' : 'Amount ₹'} type="number" value={form.value} onChange={(e) => set('value', Number(e.target.value))} />
            <TextField label="Max discount ₹ (optional)" type="number" value={form.maxAmount ?? ''} onChange={(e) => set('maxAmount', e.target.value ? Number(e.target.value) : null)} />
            <FormControlLabel control={<Switch checked={form.active} onChange={(e) => set('active', e.target.checked)} />} label="Active" />
            <TextField label="Valid from" type="date" value={form.validFrom ?? ''} onChange={(e) => set('validFrom', e.target.value || null)} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField label="Valid to" type="date" value={form.validTo ?? ''} onChange={(e) => set('validTo', e.target.value || null)} slotProps={{ inputLabel: { shrink: true } }} />
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!form.code || !form.name || save.isPending} onClick={() => save.mutate()}>Save</Button>
      </DialogActions>
    </Dialog>
  );
}

export function DiscountsAdmin() {
  const discounts = useQuery({ queryKey: ['discounts', 'all'], queryFn: fetchDiscounts });
  const [dialog, setDialog] = useState<{ open: boolean; discount: Discount | null }>({ open: false, discount: null });
  return (
    <Box>
      <Stack direction="row" sx={{ mb: 2 }}>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, discount: null })}>
          New discount
        </Button>
      </Stack>
      {discounts.isError && <Alert severity="error">{toUserMessage(discounts.error)}</Alert>}
      {discounts.data?.length === 0 && (
        <Alert severity="info" variant="outlined">No discounts yet. Operators can only apply discounts created here.</Alert>
      )}
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Code</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>Value</TableCell>
              <TableCell>Valid</TableCell>
              <TableCell>Status</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {discounts.data?.map((d) => (
              <TableRow key={d.id}>
                <TableCell sx={{ fontWeight: 700 }}>{d.code}</TableCell>
                <TableCell>{d.name}</TableCell>
                <TableCell>
                  {d.discountType === 'PERCENTAGE' ? `${d.value}%` : formatINR(d.value)}
                  {d.maxAmount ? ` (max ${formatINR(d.maxAmount)})` : ''}
                </TableCell>
                <TableCell>{d.validFrom || d.validTo ? `${d.validFrom ?? '…'} → ${d.validTo ?? '…'}` : 'Always'}</TableCell>
                <TableCell>
                  <Chip size="small" label={d.validToday ? 'Usable today' : 'Not usable'} color={d.validToday ? 'success' : 'default'} variant="outlined" />
                </TableCell>
                <TableCell padding="checkbox">
                  <IconButton aria-label={`Edit ${d.code}`} onClick={() => setDialog({ open: true, discount: d })}><EditIcon /></IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      <DiscountDialog open={dialog.open} discount={dialog.discount} onClose={() => setDialog({ open: false, discount: null })} />
    </Box>
  );
}
