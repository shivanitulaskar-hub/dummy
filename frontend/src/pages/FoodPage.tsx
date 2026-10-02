import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  Switch,
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
import RemoveIcon from '@mui/icons-material/Remove';
import EditIcon from '@mui/icons-material/Edit';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PrintIcon from '@mui/icons-material/Print';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { createWalkInBill } from '../api/bills';
import { toUserMessage } from '../api/errors';
import { createFoodCategory, createFoodItem, fetchFoodCategories, fetchFoodItems, updateFoodItem } from '../api/food';
import type { BillDetail, FoodItem, FoodItemRequest, PaymentMode } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { DiscountSelect } from '../components/bills/DiscountSelect';
import { PaymentSelector } from '../components/bills/PaymentSelector';
import { MenuPicker } from '../components/food/MenuPicker';
import { formatINR } from '../utils/format';
import { randomId } from '../utils/id';

/** Food-only sale at the counter (no gaming session). The total shown is an estimate; the server bills it. */
function CounterSale() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const menu = useQuery({ queryKey: ['food-items'], queryFn: () => fetchFoodItems() });
  const [cart, setCart] = useState<Map<number, number>>(new Map());
  const [mobile, setMobile] = useState('');
  const [name, setName] = useState('');
  const [discountId, setDiscountId] = useState<number | null>(null);
  const [mode, setMode] = useState<PaymentMode>('CASH');
  const [ref, setRef] = useState('');
  const [done, setDone] = useState<BillDetail | null>(null);
  const key = useRef(randomId());

  const byId = useMemo(() => new Map((menu.data ?? []).map((i) => [i.id, i])), [menu.data]);
  const lines = [...cart.entries()].map(([id, qty]) => ({ item: byId.get(id), qty })).filter((l) => l.item);
  const estimate = lines.reduce((sum, l) => sum + l.item!.sellingPrice * l.qty, 0);

  const change = (id: number, delta: number) =>
    setCart((c) => {
      const next = new Map(c);
      const qty = (next.get(id) ?? 0) + delta;
      if (qty <= 0) next.delete(id);
      else next.set(id, qty);
      return next;
    });

  const sale = useMutation({
    mutationFn: () =>
      createWalkInBill(
        {
          customerMobile: mobile.trim() || undefined,
          customerName: name.trim() || undefined,
          items: lines.map((l) => ({ foodItemId: l.item!.id, quantity: l.qty })),
          discountId,
          paymentMode: mode,
          referenceNo: ref.trim() || undefined,
        },
        key.current,
      ),
    onSuccess: (bill) => {
      setDone(bill);
      queryClient.invalidateQueries({ queryKey: ['food-items'] });
    },
  });

  const reset = () => {
    setCart(new Map());
    setMobile('');
    setName('');
    setDiscountId(null);
    setMode('CASH');
    setRef('');
    setDone(null);
    sale.reset();
    key.current = randomId();
  };

  return (
    <Box sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '3fr 2fr' } }}>
      <Box>
        {menu.isError && <Alert severity="error">{toUserMessage(menu.error)}</Alert>}
        <MenuPicker items={menu.data ?? []} onPick={(item) => change(item.id, 1)} disabled={done !== null} />
      </Box>
      <Card>
        <CardContent>
          {done ? (
            <Stack alignItems="center" spacing={1.5} sx={{ py: 2 }}>
              <CheckCircleIcon color="success" sx={{ fontSize: 56 }} />
              <Typography variant="h5" fontWeight={800}>
                {done.billNumber}
              </Typography>
              <Typography color="text.secondary">{formatINR(done.totalAmount)} received</Typography>
              <Stack direction="row" spacing={1}>
                <Button startIcon={<PrintIcon />} variant="outlined" onClick={() => navigate(`/bills/${done.id}/print?format=thermal&autoprint=1`)}>
                  Receipt
                </Button>
                <Button startIcon={<PrintIcon />} variant="outlined" onClick={() => navigate(`/bills/${done.id}/print?format=a4&autoprint=1`)}>
                  A4
                </Button>
              </Stack>
              <Button variant="contained" size="large" fullWidth onClick={reset}>
                New sale
              </Button>
            </Stack>
          ) : (
            <Stack spacing={2}>
              <Typography variant="h6">Counter sale</Typography>
              {lines.length === 0 && <Typography color="text.secondary">Tap items to add them.</Typography>}
              <Stack divider={<Divider flexItem />}>
                {lines.map(({ item, qty }) => (
                  <Stack key={item!.id} direction="row" alignItems="center" spacing={1} sx={{ py: 0.5 }}>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography noWrap fontWeight={600}>{item!.name}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatINR(item!.sellingPrice)} × {qty}
                      </Typography>
                    </Box>
                    <IconButton aria-label="One less" onClick={() => change(item!.id, -1)}><RemoveIcon /></IconButton>
                    <Typography fontWeight={800}>{qty}</Typography>
                    <IconButton aria-label="One more" onClick={() => change(item!.id, 1)}><AddIcon /></IconButton>
                  </Stack>
                ))}
              </Stack>
              <Stack direction="row" justifyContent="space-between">
                <Typography color="text.secondary">Items total</Typography>
                <Typography fontWeight={800}>{formatINR(estimate)}</Typography>
              </Stack>
              <Typography variant="caption" color="text.secondary">
                Discount and GST (if enabled) are applied on the final bill.
              </Typography>
              <TextField size="small" label="Customer mobile (optional)" value={mobile} onChange={(e) => setMobile(e.target.value)} inputMode="tel" />
              <TextField size="small" label="Customer name (optional)" value={name} onChange={(e) => setName(e.target.value)} />
              <DiscountSelect value={discountId} onChange={setDiscountId} />
              <PaymentSelector mode={mode} onModeChange={setMode} referenceNo={ref} onReferenceChange={setRef} />
              {sale.isError && <Alert severity="error">{toUserMessage(sale.error)}</Alert>}
              <Button
                variant="contained"
                color="success"
                size="large"
                disabled={lines.length === 0 || sale.isPending}
                onClick={() => sale.mutate()}
              >
                {sale.isPending ? 'Saving…' : 'COMPLETE SALE'}
              </Button>
            </Stack>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}

const EMPTY_ITEM: FoodItemRequest = {
  name: '',
  categoryId: 0,
  sellingPrice: 0,
  costPrice: 0,
  reorderLevel: 0,
  trackInventory: true,
  gamingCreditMinutes: 0,
  active: true,
};

function ItemDialog({ open, item, onClose }: { open: boolean; item: FoodItem | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const categories = useQuery({ queryKey: ['food-categories'], queryFn: fetchFoodCategories });
  const [form, setForm] = useState<FoodItemRequest>(EMPTY_ITEM);
  useEffect(() => {
    if (open) {
      setForm(
        item
          ? {
              name: item.name,
              categoryId: item.categoryId,
              sellingPrice: item.sellingPrice,
              costPrice: item.costPrice ?? 0,
              reorderLevel: item.reorderLevel,
              trackInventory: item.trackInventory,
              gamingCreditMinutes: item.gamingCreditMinutes,
              active: item.active,
            }
          : { ...EMPTY_ITEM, categoryId: categories.data?.[0]?.id ?? 0 },
      );
    }
  }, [open, item, categories.data]);
  const save = useMutation({
    mutationFn: () => (item ? updateFoodItem(item.id, form) : createFoodItem(form)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['food-items'] });
      onClose();
    },
  });
  const set = <K extends keyof FoodItemRequest>(k: K, v: FoodItemRequest[K]) => setForm((f) => ({ ...f, [k]: v }));
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" TransitionProps={{ onExited: () => save.reset() }}>
      <DialogTitle>{item ? `Edit ${item.name}` : 'Add menu item'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          <TextField label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} required />
          <TextField select label="Category" value={form.categoryId || ''} onChange={(e) => set('categoryId', Number(e.target.value))}>
            {categories.data?.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
          </TextField>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: '1fr 1fr' }}>
            <TextField label="Selling price ₹" type="number" value={form.sellingPrice} onChange={(e) => set('sellingPrice', Number(e.target.value))} />
            <TextField label="Cost price ₹" type="number" value={form.costPrice} onChange={(e) => set('costPrice', Number(e.target.value))} helperText="For profit reports" />
            <TextField label="Reorder level" type="number" value={form.reorderLevel} onChange={(e) => set('reorderLevel', Number(e.target.value))} />
            <TextField label="Free gaming minutes" type="number" value={form.gamingCreditMinutes} onChange={(e) => set('gamingCreditMinutes', Number(e.target.value))} helperText="e.g. 15 for the mojito offer" />
          </Box>
          <Stack direction="row" spacing={2}>
            <FormControlLabel control={<Switch checked={form.trackInventory} onChange={(e) => set('trackInventory', e.target.checked)} />} label="Track stock" />
            <FormControlLabel control={<Switch checked={form.active} onChange={(e) => set('active', e.target.checked)} />} label="On the menu" />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!form.name.trim() || !form.categoryId || save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function MenuAdmin() {
  const queryClient = useQueryClient();
  const items = useQuery({ queryKey: ['food-items', 'all'], queryFn: () => fetchFoodItems(true) });
  const [dialog, setDialog] = useState<{ open: boolean; item: FoodItem | null }>({ open: false, item: null });
  const [newCategory, setNewCategory] = useState('');
  const addCategory = useMutation({
    mutationFn: () => createFoodCategory(newCategory.trim(), 99),
    onSuccess: () => {
      setNewCategory('');
      queryClient.invalidateQueries({ queryKey: ['food-categories'] });
    },
  });
  return (
    <Box>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2 }} alignItems={{ sm: 'center' }}>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, item: null })}>
          Add item
        </Button>
        <Box sx={{ flex: 1 }} />
        <TextField size="small" label="New category" value={newCategory} onChange={(e) => setNewCategory(e.target.value)} />
        <Button disabled={!newCategory.trim() || addCategory.isPending} onClick={() => addCategory.mutate()}>
          Add category
        </Button>
      </Stack>
      {(items.isError || addCategory.isError) && (
        <Alert severity="error" sx={{ mb: 2 }}>{toUserMessage(items.error ?? addCategory.error)}</Alert>
      )}
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Item</TableCell>
              <TableCell>Category</TableCell>
              <TableCell align="right">Price</TableCell>
              <TableCell align="right">Cost</TableCell>
              <TableCell align="right">Stock</TableCell>
              <TableCell>Status</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {items.data?.map((i) => (
              <TableRow key={i.id} sx={{ opacity: i.active ? 1 : 0.5 }}>
                <TableCell sx={{ fontWeight: 600 }}>
                  {i.name}
                  {i.gamingCreditMinutes > 0 && <Chip size="small" color="success" label={`+${i.gamingCreditMinutes}m`} sx={{ ml: 1 }} />}
                </TableCell>
                <TableCell>{i.categoryName}</TableCell>
                <TableCell align="right">{formatINR(i.sellingPrice)}</TableCell>
                <TableCell align="right">{formatINR(i.costPrice)}</TableCell>
                <TableCell align="right">
                  {i.trackInventory ? (
                    <Typography color={i.lowStock ? 'warning.main' : 'text.primary'} fontWeight={i.lowStock ? 800 : 400}>
                      {i.stockQuantity}
                    </Typography>
                  ) : (
                    '–'
                  )}
                </TableCell>
                <TableCell>{i.active ? 'On menu' : 'Hidden'}</TableCell>
                <TableCell padding="checkbox">
                  <IconButton aria-label={`Edit ${i.name}`} onClick={() => setDialog({ open: true, item: i })}>
                    <EditIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      <ItemDialog open={dialog.open} item={dialog.item} onClose={() => setDialog({ open: false, item: null })} />
    </Box>
  );
}

export function FoodPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState(0);
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Food & Beverages
      </Typography>
      {user?.role === 'ADMIN' && (
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}>
          <Tab label="Counter sale" />
          <Tab label="Menu" />
        </Tabs>
      )}
      {tab === 0 ? <CounterSale /> : <MenuAdmin />}
    </Box>
  );
}
