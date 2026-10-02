import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  InputAdornment,
  MenuItem,
  Stack,
  Switch,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import AlbumIcon from '@mui/icons-material/Album';
import SearchIcon from '@mui/icons-material/Search';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../api/errors';
import {
  cancelRental,
  createGame,
  createRental,
  fetchGames,
  fetchReturnPreview,
  returnRental,
  searchRentals,
  updateGame,
} from '../api/rentals';
import type { Game, GameRequest, PaymentMode, Rental, RentalStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { BillDetailDialog } from '../components/bills/BillDetailDialog';
import { PaymentSelector } from '../components/bills/PaymentSelector';
import { RentalStatusChip } from '../components/rentals/RentalStatusChip';
import { formatDateTime, formatINR } from '../utils/format';
import { randomId } from '../utils/id';

function gameLabel(name: string, copy: number) {
  return copy > 1 ? `${name} (copy ${copy})` : name;
}

function invalidateRentals(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['rentals'] });
  queryClient.invalidateQueries({ queryKey: ['games'] });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
}

// ---------------------------------------------------------------- rent out

function RentDialog({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: (r: Rental) => void }) {
  const queryClient = useQueryClient();
  const games = useQuery({ queryKey: ['games', 'active'], queryFn: () => fetchGames(false), enabled: open });
  const [gameId, setGameId] = useState<number | ''>('');
  const [mobile, setMobile] = useState('');
  const [name, setName] = useState('');
  const [days, setDays] = useState('');
  const [deposit, setDeposit] = useState('');
  const [mode, setMode] = useState<PaymentMode>('CASH');
  const [referenceNo, setReferenceNo] = useState('');
  const [key, setKey] = useState(randomId);
  const available = games.data?.filter((g) => g.availableForRental && !g.currentRental) ?? [];
  const game = games.data?.find((g) => g.id === gameId);

  const save = useMutation({
    mutationFn: () =>
      createRental(
        {
          gameId: Number(gameId),
          customerName: name.trim(),
          customerMobile: mobile.trim(),
          rentalDays: days ? Number(days) : undefined,
          depositAmount: deposit !== '' ? Number(deposit) : undefined,
          paymentMode: mode,
          referenceNo: referenceNo.trim() || undefined,
        },
        key,
      ),
    onSuccess: (r) => {
      invalidateRentals(queryClient);
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      onDone(r);
    },
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      TransitionProps={{
        onExited: () => {
          setGameId('');
          setMobile('');
          setName('');
          setDays('');
          setDeposit('');
          setMode('CASH');
          setReferenceNo('');
          setKey(randomId());
          save.reset();
        },
      }}
    >
      <DialogTitle>Rent a game</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          <TextField
            select
            label="Game"
            value={gameId}
            onChange={(e) => {
              const g = games.data?.find((x) => x.id === Number(e.target.value));
              setGameId(Number(e.target.value));
              setDays(g ? String(g.rentalPeriodDays) : '');
              setDeposit(g ? String(g.defaultDeposit) : '');
            }}
            helperText={games.data && available.length === 0 ? 'All discs are rented out or unavailable' : undefined}
          >
            {available.map((g) => (
              <MenuItem key={g.id} value={g.id}>
                {gameLabel(g.name, g.copyNumber)} · {formatINR(g.rentalPrice)} / {g.rentalPeriodDays} days
              </MenuItem>
            ))}
          </TextField>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField label="Mobile" value={mobile} onChange={(e) => setMobile(e.target.value)} inputMode="tel" required />
            <TextField label="Customer name" value={name} onChange={(e) => setName(e.target.value)} required />
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              label="Days"
              type="number"
              value={days}
              onChange={(e) => setDays(e.target.value)}
              helperText={game ? `${formatINR(game.rentalPrice)} per ${game.rentalPeriodDays} days or part of it` : ' '}
            />
            <TextField
              label="Deposit ₹"
              type="number"
              value={deposit}
              onChange={(e) => setDeposit(e.target.value)}
              helperText="Refundable, returned on return"
            />
          </Stack>
          {game && game.lateFeePerDay > 0 && (
            <Typography variant="body2" color="text.secondary">
              Late fee {formatINR(game.lateFeePerDay)} per day after the due time.
            </Typography>
          )}
          <PaymentSelector mode={mode} onModeChange={setMode} referenceNo={referenceNo} onReferenceChange={setReferenceNo} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          size="large"
          disabled={!gameId || !mobile.trim() || !name.trim() || save.isPending}
          onClick={() => save.mutate()}
        >
          Rent out & take payment
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ---------------------------------------------------------------- return / cancel

function ReturnDialog({ rental, onClose }: { rental: Rental | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<PaymentMode>('CASH');
  const [referenceNo, setReferenceNo] = useState('');
  const preview = useQuery({
    queryKey: ['rental-return-preview', rental?.id],
    queryFn: () => fetchReturnPreview(rental!.id),
    enabled: rental !== null,
    gcTime: 0,
  });
  const p = preview.data;
  const save = useMutation({
    mutationFn: () =>
      returnRental(rental!.id, p && p.lateFee > 0 ? { paymentMode: mode, referenceNo: referenceNo.trim() || undefined } : {}),
    onSuccess: () => {
      invalidateRentals(queryClient);
      onClose();
    },
  });
  return (
    <Dialog open={rental !== null} onClose={onClose} fullWidth maxWidth="xs" TransitionProps={{ onExited: () => { setMode('CASH'); setReferenceNo(''); save.reset(); } }}>
      <DialogTitle>Return {rental && gameLabel(rental.gameName, rental.copyNumber)}</DialogTitle>
      <DialogContent>
        {preview.isLoading && <CircularProgress />}
        {(preview.isError || save.isError) && <Alert severity="error">{toUserMessage(preview.error ?? save.error)}</Alert>}
        {rental && p && (
          <Stack spacing={1.5} sx={{ pt: 1 }}>
            <Typography>
              {rental.customerName} · {rental.customerMobile}
            </Typography>
            <Typography color="text.secondary">Due {formatDateTime(p.expectedReturnAt)}</Typography>
            {p.lateFee > 0 ? (
              <Alert severity="warning" variant="outlined">
                <strong>{p.lateDays} day{p.lateDays === 1 ? '' : 's'} late.</strong> Collect late fee{' '}
                <strong>{formatINR(p.lateFeeTotal)}</strong> ({formatINR(p.lateFeePerDay)} × {p.lateDays}
                {p.lateFeeTotal !== p.lateFee ? ' + GST' : ''}).
              </Alert>
            ) : (
              <Alert severity="success" variant="outlined">Returned on time. No late fee.</Alert>
            )}
            {p.depositToRefund > 0 && (
              <Alert severity="info" variant="outlined">
                Give back the deposit: <strong>{formatINR(p.depositToRefund)}</strong> {p.depositMode && `(${p.depositMode})`}
              </Alert>
            )}
            {p.lateFee > 0 && (
              <PaymentSelector mode={mode} onModeChange={setMode} referenceNo={referenceNo} onReferenceChange={setReferenceNo} />
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Back</Button>
        <Button variant="contained" size="large" disabled={!p || save.isPending} onClick={() => save.mutate()}>
          Confirm return
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function CancelDialog({ rental, onClose }: { rental: Rental | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const save = useMutation({
    mutationFn: () => cancelRental(rental!.id, reason.trim()),
    onSuccess: () => {
      invalidateRentals(queryClient);
      onClose();
    },
  });
  return (
    <Dialog open={rental !== null} onClose={onClose} fullWidth maxWidth="xs" TransitionProps={{ onExited: () => { setReason(''); save.reset(); } }}>
      <DialogTitle>Cancel rental {rental?.rentalNumber}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          {rental && (
            <Typography color="text.secondary">
              Refunds {formatINR(rental.totalAmount)}
              {rental.depositAmount > 0 && ` and the ${formatINR(rental.depositAmount)} deposit`} in {rental.paymentMode}. Use this only for
              a rental entered by mistake; a returned disc is handled with Return.
            </Typography>
          )}
          <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Back</Button>
        <Button variant="contained" color="error" disabled={!reason.trim() || save.isPending} onClick={() => save.mutate()}>
          Cancel & refund
        </Button>
      </DialogActions>
    </Dialog>
  );
}

// ---------------------------------------------------------------- rentals list

function RentalsTable({
  status,
  onReturn,
  onCancel,
  onBill,
}: {
  status?: RentalStatus;
  onReturn: (r: Rental) => void;
  onCancel: (r: Rental) => void;
  onBill: (billId: number) => void;
}) {
  const [q, setQ] = useState('');
  const [filter, setFilter] = useState<RentalStatus | ''>(status ?? '');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const rentals = useQuery({
    queryKey: ['rentals', filter, q, page, size],
    queryFn: () => searchRentals({ status: filter || undefined, q: q.trim() || undefined, page, size }),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
  const outOnly = status === 'OUT';
  return (
    <>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          placeholder="Rental no., customer, mobile or game"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }}
          sx={{ flex: 2 }}
        />
        <TextField select label="Status" value={filter} onChange={(e) => { setFilter(e.target.value as RentalStatus | ''); setPage(0); }} sx={{ flex: 1 }}>
          {!outOnly && <MenuItem value="">All</MenuItem>}
          <MenuItem value="OUT">Out (incl. overdue)</MenuItem>
          <MenuItem value="OVERDUE">Overdue only</MenuItem>
          {!outOnly && <MenuItem value="RETURNED">Returned</MenuItem>}
          {!outOnly && <MenuItem value="CANCELLED">Cancelled</MenuItem>}
        </TextField>
      </Stack>
      {rentals.isError && <Alert severity="error">{toUserMessage(rentals.error)}</Alert>}
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Rental</TableCell>
              <TableCell>Game</TableCell>
              <TableCell>Customer</TableCell>
              <TableCell>Out</TableCell>
              <TableCell>{outOnly ? 'Due' : 'Due / returned'}</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Deposit</TableCell>
              <TableCell align="right">{outOnly ? 'Late fee now' : 'Paid'}</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {rentals.data?.content.map((r) => {
              const out = r.status === 'OUT' || r.status === 'OVERDUE';
              return (
                <TableRow key={r.id} hover>
                  <TableCell sx={{ fontWeight: 700, whiteSpace: 'nowrap', cursor: r.billId ? 'pointer' : 'default' }} onClick={() => r.billId && onBill(r.billId)}>
                    {r.rentalNumber}
                  </TableCell>
                  <TableCell>{gameLabel(r.gameName, r.copyNumber)}</TableCell>
                  <TableCell>
                    {r.customerName}
                    <Typography variant="caption" color="text.secondary" display="block">{r.customerMobile}</Typography>
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.outAt)}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(out ? r.expectedReturnAt : r.returnedAt ?? r.expectedReturnAt)}</TableCell>
                  <TableCell><RentalStatusChip status={r.status} /></TableCell>
                  <TableCell align="right">{formatINR(r.depositAmount)}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {out ? (r.accruedLateFee > 0 ? `${formatINR(r.accruedLateFee)} (${r.accruedLateDays}d)` : '–') : formatINR(r.totalAmount)}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {out && (
                      <>
                        <Button variant="contained" size="small" onClick={() => onReturn(r)}>Return</Button>
                        <Button color="error" size="small" onClick={() => onCancel(r)} sx={{ ml: 1 }}>Cancel</Button>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
            {rentals.data && rentals.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={9}>
                  <Typography color="text.secondary">{outOnly ? 'No discs are out right now.' : 'No rentals found.'}</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>
      {rentals.data && (
        <TablePagination
          component="div"
          count={rentals.data.totalElements}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => {
            setSize(Number(e.target.value));
            setPage(0);
          }}
        />
      )}
    </>
  );
}

// ---------------------------------------------------------------- game master

const EMPTY_GAME: GameRequest = {
  name: '',
  copyNumber: 1,
  platform: 'PS5',
  purchasePrice: 0,
  purchaseDate: null,
  rentalPrice: 0,
  rentalPeriodDays: 7,
  lateFeePerDay: 0,
  defaultDeposit: 0,
  availableForRental: true,
  active: true,
  notes: null,
};

function GameFormDialog({ open, game, onClose }: { open: boolean; game: Game | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<Partial<Record<keyof GameRequest, string | boolean | number | null>>>({ ...EMPTY_GAME });
  const set = (k: keyof GameRequest, v: string | boolean | null) => setForm((f) => ({ ...f, [k]: v }));
  const save = useMutation({
    mutationFn: () => {
      const request: GameRequest = {
        name: String(form.name).trim(),
        copyNumber: Number(form.copyNumber),
        platform: String(form.platform ?? 'PS5'),
        purchasePrice: Number(form.purchasePrice),
        purchaseDate: form.purchaseDate ? String(form.purchaseDate) : null,
        rentalPrice: Number(form.rentalPrice),
        rentalPeriodDays: Number(form.rentalPeriodDays),
        lateFeePerDay: Number(form.lateFeePerDay),
        defaultDeposit: Number(form.defaultDeposit),
        availableForRental: Boolean(form.availableForRental),
        active: Boolean(form.active),
        notes: form.notes ? String(form.notes).trim() : null,
      };
      return game ? updateGame(game.id, request) : createGame(request);
    },
    onSuccess: () => {
      invalidateRentals(queryClient);
      onClose();
    },
  });
  const field = (k: keyof GameRequest, label: string, type = 'number') => (
    <TextField label={label} type={type} value={form[k] ?? ''} onChange={(e) => set(k, e.target.value)} slotProps={type === 'date' ? { inputLabel: { shrink: true } } : undefined} />
  );
  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      TransitionProps={{
        onEnter: () => {
          setForm(
            game
              ? {
                  name: game.name,
                  copyNumber: game.copyNumber,
                  platform: game.platform,
                  purchasePrice: game.purchasePrice ?? 0,
                  purchaseDate: game.purchaseDate,
                  rentalPrice: game.rentalPrice,
                  rentalPeriodDays: game.rentalPeriodDays,
                  lateFeePerDay: game.lateFeePerDay,
                  defaultDeposit: game.defaultDeposit,
                  availableForRental: game.availableForRental,
                  active: game.active,
                  notes: game.notes,
                }
              : { ...EMPTY_GAME },
          );
          save.reset();
        },
      }}
    >
      <DialogTitle>{game ? 'Edit game disc' : 'New game disc'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          <Stack direction="row" spacing={2}>
            <TextField label="Game name" value={form.name ?? ''} onChange={(e) => set('name', e.target.value)} sx={{ flex: 3 }} />
            <TextField label="Copy no." type="number" value={form.copyNumber ?? ''} onChange={(e) => set('copyNumber', e.target.value)} sx={{ flex: 1 }} helperText="2nd disc = 2" />
          </Stack>
          <Stack direction="row" spacing={2}>
            {field('purchasePrice', 'Purchase price ₹')}
            {field('purchaseDate', 'Purchase date', 'date')}
          </Stack>
          <Stack direction="row" spacing={2}>
            {field('rentalPrice', 'Rental price ₹')}
            {field('rentalPeriodDays', 'For days')}
          </Stack>
          <Stack direction="row" spacing={2}>
            {field('lateFeePerDay', 'Late fee ₹ / day')}
            {field('defaultDeposit', 'Default deposit ₹')}
          </Stack>
          <TextField label="Notes" value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value)} />
          <Stack direction="row" spacing={2}>
            <FormControlLabel control={<Switch checked={Boolean(form.availableForRental)} onChange={(e) => set('availableForRental', e.target.checked)} />} label="Available for rent" />
            <FormControlLabel control={<Switch checked={Boolean(form.active)} onChange={(e) => set('active', e.target.checked)} />} label="Active" />
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!String(form.name ?? '').trim() || save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function GamesTab() {
  const { user } = useAuth();
  const admin = user?.role === 'ADMIN';
  const games = useQuery({ queryKey: ['games', 'all'], queryFn: () => fetchGames(true) });
  const [form, setForm] = useState<{ open: boolean; game: Game | null }>({ open: false, game: null });
  return (
    <>
      {admin && (
        <Stack direction="row" sx={{ mb: 2 }}>
          <Typography color="text.secondary" sx={{ flex: 1 }}>
            One row per physical disc. ROI = (rental revenue − purchase cost) ÷ purchase cost.
          </Typography>
          <Button startIcon={<AddIcon />} onClick={() => setForm({ open: true, game: null })}>Add disc</Button>
        </Stack>
      )}
      {games.isError && <Alert severity="error">{toUserMessage(games.error)}</Alert>}
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Game</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Rent</TableCell>
              <TableCell align="right">Late fee/day</TableCell>
              <TableCell align="right">Deposit</TableCell>
              {admin && (
                <>
                  <TableCell align="right">Cost</TableCell>
                  <TableCell align="right">Rentals</TableCell>
                  <TableCell align="right">Revenue</TableCell>
                  <TableCell align="right">Left to recover</TableCell>
                  <TableCell align="right">ROI</TableCell>
                  <TableCell align="right">Rentals to recover</TableCell>
                </>
              )}
            </TableRow>
          </TableHead>
          <TableBody>
            {games.data?.map((g) => (
              <TableRow key={g.id} hover={admin} sx={{ cursor: admin ? 'pointer' : 'default', opacity: g.active ? 1 : 0.5 }} onClick={() => admin && setForm({ open: true, game: g })}>
                <TableCell sx={{ fontWeight: 700 }}>{gameLabel(g.name, g.copyNumber)}</TableCell>
                <TableCell>
                  {!g.active ? (
                    <Chip size="small" label="Inactive" />
                  ) : g.currentRental ? (
                    <Chip size="small" color={g.currentRental.overdue ? 'error' : 'info'} label={`${g.currentRental.overdue ? 'Overdue' : 'Out'} · ${g.currentRental.customerName}`} />
                  ) : g.availableForRental ? (
                    <Chip size="small" color="success" variant="outlined" label="Available" />
                  ) : (
                    <Chip size="small" label="Not for rent" />
                  )}
                </TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>{formatINR(g.rentalPrice)} / {g.rentalPeriodDays}d</TableCell>
                <TableCell align="right">{formatINR(g.lateFeePerDay)}</TableCell>
                <TableCell align="right">{formatINR(g.defaultDeposit)}</TableCell>
                {admin && g.roi && (
                  <>
                    <TableCell align="right">{formatINR(g.roi.purchaseCost)}</TableCell>
                    <TableCell align="right">{g.roi.rentals}</TableCell>
                    <TableCell align="right">{formatINR(g.roi.revenue)}</TableCell>
                    <TableCell align="right">{g.roi.recovered ? <Chip size="small" color="success" label="Recovered" /> : formatINR(g.roi.remaining)}</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 700 }}>{g.roi.roiPercent == null ? '–' : `${g.roi.roiPercent.toFixed(1)}%`}</TableCell>
                    <TableCell align="right">{g.roi.rentalsToRecover ?? '–'}</TableCell>
                  </>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      <GameFormDialog open={form.open} game={form.game} onClose={() => setForm({ open: false, game: null })} />
    </>
  );
}

// ---------------------------------------------------------------- page

export function RentalsPage() {
  const [tab, setTab] = useState(0);
  const [renting, setRenting] = useState(false);
  const [done, setDone] = useState<Rental | null>(null);
  const [returning, setReturning] = useState<Rental | null>(null);
  const [cancelling, setCancelling] = useState<Rental | null>(null);
  const [billId, setBillId] = useState<number | null>(null);

  return (
    <Box>
      <Stack direction="row" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h4">Game Rentals</Typography>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" size="large" startIcon={<AlbumIcon />} onClick={() => setRenting(true)}>
          Rent a game
        </Button>
      </Stack>
      {done && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setDone(null)} action={done.billId ? <Button color="inherit" onClick={() => setBillId(done.billId)}>Bill</Button> : undefined}>
          <strong>{done.rentalNumber}</strong>: {gameLabel(done.gameName, done.copyNumber)} to {done.customerName}. Paid {formatINR(done.totalAmount)}
          {done.depositAmount > 0 && ` + deposit ${formatINR(done.depositAmount)}`}. Due back {formatDateTime(done.expectedReturnAt)}.
        </Alert>
      )}
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab label="Out now" />
        <Tab label="All rentals" />
        <Tab label="Games" />
      </Tabs>
      {tab === 0 && <RentalsTable key="out" status="OUT" onReturn={setReturning} onCancel={setCancelling} onBill={setBillId} />}
      {tab === 1 && <RentalsTable key="all" onReturn={setReturning} onCancel={setCancelling} onBill={setBillId} />}
      {tab === 2 && <GamesTab />}
      <RentDialog
        open={renting}
        onClose={() => setRenting(false)}
        onDone={(r) => {
          setRenting(false);
          setDone(r);
        }}
      />
      <ReturnDialog rental={returning} onClose={() => setReturning(null)} />
      <CancelDialog rental={cancelling} onClose={() => setCancelling(null)} />
      <BillDetailDialog billId={billId} onClose={() => setBillId(null)} />
    </Box>
  );
}
