import { useState, type ReactNode } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  InputAdornment,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { closeDay, fetchClosingDay, fetchClosingHistory, fetchClosingToday, reopenDay } from '../api/closing';
import { toUserMessage } from '../api/errors';
import type { DailyClosing, DayStatus } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { formatDateTime, formatINR, formatMinutes } from '../utils/format';

function Line({ label, value, strong, muted }: { label: string; value: ReactNode; strong?: boolean; muted?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2} sx={{ py: 0.4 }}>
      <Typography color={strong ? 'text.primary' : 'text.secondary'} fontWeight={strong ? 800 : 400} variant={muted ? 'body2' : 'body1'}>
        {label}
      </Typography>
      <Typography fontWeight={strong ? 800 : 600} sx={{ whiteSpace: 'nowrap' }} variant={muted ? 'body2' : 'body1'}>
        {value}
      </Typography>
    </Stack>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="overline" color="text.secondary">{title}</Typography>
        {children}
      </CardContent>
    </Card>
  );
}

const STATUS_CHIP: Record<DayStatus, { label: string; color: 'default' | 'success' | 'warning' }> = {
  OPEN: { label: 'Open', color: 'default' },
  CLOSED: { label: 'Closed', color: 'success' },
  REOPENED: { label: 'Reopened', color: 'warning' },
};

/** Preview only: paise integers so the displayed difference is exact. The server stores the real figure. */
function previewDifference(actual: string, expected: number): number | null {
  if (actual.trim() === '' || Number.isNaN(Number(actual))) return null;
  return (Math.round(Number(actual) * 100) - Math.round(expected * 100)) / 100;
}

function DayView({ day, onChanged, onOpeningCash }: { day: DailyClosing; onChanged: () => void; onOpeningCash: (v: number | undefined) => void }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [opening, setOpening] = useState(String(day.cash.openingCash));
  const [actual, setActual] = useState('');
  const [notes, setNotes] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [reopening, setReopening] = useState(false);
  const [reason, setReason] = useState('');
  const f = day.figures;
  const closed = day.status === 'CLOSED';
  const difference = closed ? day.cashDifference : previewDifference(actual, day.cash.expectedCash);

  const close = useMutation({
    mutationFn: () => closeDay(day.businessDate, { openingCash: Number(opening || 0), actualCash: Number(actual), notes: notes.trim() || undefined }),
    onSuccess: () => {
      setConfirm(false);
      queryClient.invalidateQueries({ queryKey: ['closing'] });
      onChanged();
    },
  });
  const reopen = useMutation({
    mutationFn: () => reopenDay(day.businessDate, reason.trim()),
    onSuccess: () => {
      setReopening(false);
      setReason('');
      queryClient.invalidateQueries({ queryKey: ['closing'] });
      onChanged();
    },
  });

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 2 }} flexWrap="wrap">
        <Chip color={STATUS_CHIP[day.status].color} icon={closed ? <LockIcon /> : undefined} label={STATUS_CHIP[day.status].label} />
        {day.closedAt && (
          <Typography variant="body2" color="text.secondary">
            Closed by {day.closedBy} at {formatDateTime(day.closedAt)}
          </Typography>
        )}
        {day.status === 'REOPENED' && (
          <Typography variant="body2" color="warning.main">
            Reopened by {day.reopenedBy} at {formatDateTime(day.reopenedAt)}: {day.reopenReason}
          </Typography>
        )}
      </Stack>
      {day.live && (
        <Alert severity="info" variant="outlined" sx={{ mb: 2 }}>
          Live figures. They will be frozen when the day is closed.
        </Alert>
      )}
      {day.activeSessions.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {day.activeSessions.length} session(s) still running ({day.activeSessions.join(', ')}). End them before closing the day.
        </Alert>
      )}
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Section title="Sales">
            <Line label="Gaming" value={formatINR(f.gamingRevenue)} />
            <Line label="Food & drinks" value={formatINR(f.foodRevenue)} />
            <Line label="Game rentals" value={formatINR(f.rentalRevenue)} />
            <Line label="Discounts" value={`− ${formatINR(f.discountTotal)}`} />
            <Line label="GST" value={formatINR(f.taxTotal)} />
            {f.roundOffTotal !== 0 && <Line label="Round off" value={formatINR(f.roundOffTotal)} muted />}
            <Line label="Total revenue" value={formatINR(f.totalRevenue)} strong />
          </Section>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Section title="Payments received">
            <Line label="Cash" value={formatINR(f.cash)} />
            <Line label="UPI" value={formatINR(f.upi)} />
            <Line label="Card" value={formatINR(f.card)} />
            <Line label="Other" value={formatINR(f.other)} />
            <Line label="Refunds given" value={`− ${formatINR(f.refundTotal)}`} />
            <Line label="Deposits taken" value={formatINR(f.depositTotal)} muted />
            <Line label="Deposits returned" value={`− ${formatINR(f.depositRefundTotal)}`} muted />
          </Section>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Section title="Activity">
            <Line label="Bills" value={f.billCount} />
            <Line label="Gaming sessions" value={f.sessionCount} />
            <Line label="Customers" value={f.customerCount} />
            <Line label="Gaming hours (billed)" value={formatMinutes(f.gamingMinutes)} />
            <Line label="Food units sold" value={f.foodUnitsSold} />
          </Section>
        </Grid>
        <Grid size={12}>
          <Section title="Cash drawer">
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Line label="Opening cash (float)" value={formatINR(day.cash.openingCash)} />
                <Line label="+ Cash payments" value={formatINR(day.cash.cashPayments)} />
                <Line label="+ Cash deposits taken" value={formatINR(day.cash.cashDeposits)} />
                <Line label="− Cash refunds" value={formatINR(day.cash.cashRefunds)} />
                <Line label="− Cash deposits returned" value={formatINR(day.cash.cashDepositRefunds)} />
                <Line label="Expected cash" value={formatINR(day.cash.expectedCash)} strong />
                {closed && <Line label="Counted cash" value={formatINR(day.actualCash)} strong />}
                {difference != null && (
                  <Line
                    label={closed ? 'Difference' : 'Difference (preview)'}
                    strong
                    value={
                      <Box component="span" sx={{ color: difference === 0 ? 'success.main' : difference < 0 ? 'error.main' : 'warning.main' }}>
                        {difference > 0 ? '+' : ''}
                        {formatINR(difference)} {difference === 0 ? '✓' : difference < 0 ? '(short)' : '(excess)'}
                      </Box>
                    }
                  />
                )}
                {closed && day.notes && <Typography color="text.secondary" sx={{ mt: 1 }}>Notes: {day.notes}</Typography>}
              </Grid>
              {!closed && (
                <Grid size={{ xs: 12, md: 6 }}>
                  <Stack spacing={2}>
                    <TextField
                      label="Opening cash ₹"
                      type="number"
                      value={opening}
                      onChange={(e) => setOpening(e.target.value)}
                      onBlur={() => onOpeningCash(opening.trim() === '' ? undefined : Number(opening))}
                      helperText="Float in the drawer at the start of the day"
                      slotProps={{ input: { startAdornment: <InputAdornment position="start">₹</InputAdornment> } }}
                    />
                    <TextField
                      label="Cash counted now ₹"
                      type="number"
                      value={actual}
                      onChange={(e) => setActual(e.target.value)}
                      slotProps={{ input: { startAdornment: <InputAdornment position="start">₹</InputAdornment> } }}
                    />
                    <TextField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} />
                    <Button
                      variant="contained"
                      size="large"
                      startIcon={<LockIcon />}
                      disabled={actual.trim() === '' || day.activeSessions.length > 0}
                      onClick={() => setConfirm(true)}
                    >
                      Close the day
                    </Button>
                  </Stack>
                </Grid>
              )}
            </Grid>
          </Section>
        </Grid>
      </Grid>
      {closed && user?.role === 'ADMIN' && (
        <Button color="warning" startIcon={<LockOpenIcon />} sx={{ mt: 2 }} onClick={() => setReopening(true)}>
          Reopen day
        </Button>
      )}

      <Dialog open={confirm} onClose={() => setConfirm(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Close {day.businessDate}?</DialogTitle>
        <DialogContent>
          {close.isError && <Alert severity="error" sx={{ mb: 2 }}>{toUserMessage(close.error)}</Alert>}
          <Typography>
            The day's figures will be frozen. No more bills, refunds, rentals or stock entries can be recorded on this day unless an
            admin reopens it.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button color="inherit" onClick={() => setConfirm(false)}>Back</Button>
          <Button variant="contained" disabled={close.isPending} onClick={() => close.mutate()}>Close day</Button>
        </DialogActions>
      </Dialog>
      <Dialog open={reopening} onClose={() => setReopening(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Reopen {day.businessDate}</DialogTitle>
        <DialogContent>
          {reopen.isError && <Alert severity="error" sx={{ mb: 2 }}>{toUserMessage(reopen.error)}</Alert>}
          <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} autoFocus sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button color="inherit" onClick={() => setReopening(false)}>Back</Button>
          <Button variant="contained" color="warning" disabled={!reason.trim() || reopen.isPending} onClick={() => reopen.mutate()}>
            Reopen
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

export function DailyClosingPage() {
  const today = useQuery({ queryKey: ['closing', 'today'], queryFn: fetchClosingToday });
  const [date, setDate] = useState('');
  const [openingCash, setOpeningCash] = useState<number | undefined>(undefined);
  const selected = date || today.data?.businessDate || '';
  const day = useQuery({
    queryKey: ['closing', 'day', selected, openingCash],
    queryFn: () => fetchClosingDay(selected, openingCash),
    enabled: selected !== '',
  });
  const history = useQuery({ queryKey: ['closing', 'history'], queryFn: () => fetchClosingHistory() });

  return (
    <Box>
      <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ sm: 'center' }} spacing={2} sx={{ mb: 2 }}>
        <Typography variant="h4" sx={{ flex: 1 }}>Daily Closing</Typography>
        <TextField
          label="Business date"
          type="date"
          value={selected}
          onChange={(e) => {
            setDate(e.target.value);
            setOpeningCash(undefined);
          }}
          slotProps={{ inputLabel: { shrink: true } }}
          sx={{ maxWidth: 220 }}
        />
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        A business day runs until the configured day-start time (06:00 by default), so late-night bills belong to the evening before.
      </Typography>
      {(today.isError || day.isError) && <Alert severity="error">{toUserMessage(today.error ?? day.error)}</Alert>}
      {day.isLoading && <CircularProgress />}
      {day.data && (
        <DayView
          key={`${day.data.businessDate}-${day.data.status}`}
          day={day.data}
          onOpeningCash={setOpeningCash}
          onChanged={() => history.refetch()}
        />
      )}

      <Typography variant="h6" sx={{ mt: 4, mb: 1 }}>Recent closings</Typography>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Date</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Revenue</TableCell>
              <TableCell align="right">Expected cash</TableCell>
              <TableCell align="right">Counted</TableCell>
              <TableCell align="right">Difference</TableCell>
              <TableCell>Closed by</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {history.data?.map((c) => (
              <TableRow key={c.businessDate} hover sx={{ cursor: 'pointer' }} onClick={() => setDate(c.businessDate)}>
                <TableCell sx={{ fontWeight: 700 }}>{c.businessDate}</TableCell>
                <TableCell><Chip size="small" color={STATUS_CHIP[c.status].color} label={STATUS_CHIP[c.status].label} /></TableCell>
                <TableCell align="right">{formatINR(c.totalRevenue)}</TableCell>
                <TableCell align="right">{formatINR(c.expectedCash)}</TableCell>
                <TableCell align="right">{formatINR(c.actualCash)}</TableCell>
                <TableCell align="right" sx={{ color: c.cashDifference === 0 ? 'success.main' : c.cashDifference < 0 ? 'error.main' : 'warning.main' }}>
                  {formatINR(c.cashDifference)}
                </TableCell>
                <TableCell>{c.closedBy}</TableCell>
              </TableRow>
            ))}
            {history.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={7}>
                  <Typography color="text.secondary">No days closed in the last month.</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>
    </Box>
  );
}
