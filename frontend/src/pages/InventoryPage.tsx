import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../api/errors';
import { fetchFoodItems } from '../api/food';
import { fetchInventoryTxns, fetchStock, recordInventoryTxn } from '../api/inventory';
import type { InventoryTxnType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { formatDateTime } from '../utils/format';

const today = () => new Date().toISOString().slice(0, 10);

const TYPE_LABEL: Record<InventoryTxnType, string> = {
  OPENING: 'Opening stock',
  PURCHASE: 'Purchase',
  SALE: 'Sale',
  SALE_REVERSAL: 'Sale reversed',
  WASTAGE: 'Wastage',
  ADJUSTMENT: 'Adjustment',
};

function RecordDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const items = useQuery({ queryKey: ['food-items', 'all'], queryFn: () => fetchFoodItems(true), enabled: open });
  const [foodItemId, setFoodItemId] = useState<number | ''>('');
  const [type, setType] = useState<InventoryTxnType>('PURCHASE');
  const [quantity, setQuantity] = useState('');
  const [unitCost, setUnitCost] = useState('');
  const [notes, setNotes] = useState('');
  const types: InventoryTxnType[] = user?.role === 'ADMIN' ? ['PURCHASE', 'WASTAGE', 'OPENING', 'ADJUSTMENT'] : ['PURCHASE', 'WASTAGE'];
  const save = useMutation({
    mutationFn: () =>
      recordInventoryTxn({
        foodItemId: Number(foodItemId),
        type,
        quantity: Number(quantity),
        unitCost: unitCost ? Number(unitCost) : null,
        notes: notes.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
      queryClient.invalidateQueries({ queryKey: ['food-items'] });
      onClose();
    },
  });
  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      TransitionProps={{ onExited: () => { setFoodItemId(''); setQuantity(''); setUnitCost(''); setNotes(''); setType('PURCHASE'); save.reset(); } }}
    >
      <DialogTitle>Record stock</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          <TextField select label="Item" value={foodItemId} onChange={(e) => setFoodItemId(Number(e.target.value))}>
            {items.data?.filter((i) => i.trackInventory).map((i) => (
              <MenuItem key={i.id} value={i.id}>
                {i.name} (stock {i.stockQuantity})
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Type" value={type} onChange={(e) => setType(e.target.value as InventoryTxnType)}>
            {types.map((t) => <MenuItem key={t} value={t}>{TYPE_LABEL[t]}</MenuItem>)}
          </TextField>
          <TextField
            label="Quantity"
            type="number"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
            helperText={type === 'ADJUSTMENT' ? 'Use a minus sign to reduce stock, e.g. -2' : 'Number of units'}
          />
          {(type === 'PURCHASE' || type === 'OPENING') && (
            <TextField label="Unit cost ₹ (optional)" type="number" value={unitCost} onChange={(e) => setUnitCost(e.target.value)} />
          )}
          <TextField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={type === 'WASTAGE' ? 'e.g. expired' : ''} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!foodItemId || !quantity || save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function InventoryPage() {
  const [tab, setTab] = useState(0);
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [open, setOpen] = useState(false);
  const stock = useQuery({ queryKey: ['inventory', 'stock', from, to], queryFn: () => fetchStock(from, to), enabled: tab === 0 });
  const ledger = useQuery({ queryKey: ['inventory', 'txns', from, to], queryFn: () => fetchInventoryTxns({ from, to }), enabled: tab === 1 });
  const rows = stock.data?.filter((r) => r.trackInventory) ?? [];

  return (
    <Box>
      <Stack direction="row" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h4">Inventory</Typography>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
          Record stock
        </Button>
      </Stack>
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
      </Stack>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab label="Stock summary" />
        <Tab label="Ledger" />
      </Tabs>
      {(stock.isError || ledger.isError) && <Alert severity="error">{toUserMessage(stock.error ?? ledger.error)}</Alert>}
      {tab === 0 ? (
        <Box sx={{ overflowX: 'auto' }}>
          <Typography variant="caption" color="text.secondary">
            Closing = Opening + Purchases − Sales − Wastage ± Adjustments
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Item</TableCell>
                <TableCell align="right">Opening</TableCell>
                <TableCell align="right">Purchases</TableCell>
                <TableCell align="right">Sales</TableCell>
                <TableCell align="right">Wastage</TableCell>
                <TableCell align="right">Adjust.</TableCell>
                <TableCell align="right">Closing</TableCell>
                <TableCell align="right">Now</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.foodItemId}>
                  <TableCell sx={{ fontWeight: 600 }}>
                    {r.name}
                    {r.lowStock && <Chip size="small" color="warning" label="Low" sx={{ ml: 1 }} />}
                  </TableCell>
                  <TableCell align="right">{r.opening}</TableCell>
                  <TableCell align="right">{r.purchases}</TableCell>
                  <TableCell align="right">{r.sales}</TableCell>
                  <TableCell align="right">{r.wastage}</TableCell>
                  <TableCell align="right">{r.adjustments > 0 ? `+${r.adjustments}` : r.adjustments}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 800 }}>{r.closing}</TableCell>
                  <TableCell align="right">{r.currentStock}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      ) : (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>Item</TableCell>
                <TableCell>Type</TableCell>
                <TableCell align="right">Qty</TableCell>
                <TableCell>Notes</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {ledger.data?.map((t) => (
                <TableRow key={t.id}>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(t.createdAt)}</TableCell>
                  <TableCell>{t.foodItemName}</TableCell>
                  <TableCell>{TYPE_LABEL[t.type]}</TableCell>
                  <TableCell align="right" sx={{ color: t.quantity < 0 ? 'error.main' : 'success.main', fontWeight: 700 }}>
                    {t.quantity > 0 ? `+${t.quantity}` : t.quantity}
                  </TableCell>
                  <TableCell>{t.notes ?? (t.referenceType === 'BILL' ? `Bill #${t.referenceId}` : '')}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}
      <RecordDialog open={open} onClose={() => setOpen(false)} />
    </Box>
  );
}
