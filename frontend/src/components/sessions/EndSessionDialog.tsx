import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PrintIcon from '@mui/icons-material/Print';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { toUserMessage } from '../../api/errors';
import { completeSession, previewEnd } from '../../api/sessions';
import type { GamingSession, PaymentMode } from '../../api/types';
import { formatINR, formatMinutes, formatTime } from '../../utils/format';
import { randomId } from '../../utils/id';
import { DiscountSelect } from '../bills/DiscountSelect';
import { PaymentSelector } from '../bills/PaymentSelector';

function Row({ label, value, strong, muted }: { label: string; value: string; strong?: boolean; muted?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2} sx={{ py: 0.4 }}>
      <Typography
        color={strong ? 'text.primary' : 'text.secondary'}
        fontWeight={strong ? 800 : 400}
        variant={muted ? 'body2' : 'body1'}
        sx={{ minWidth: 0 }}
        noWrap
      >
        {label}
      </Typography>
      <Typography fontWeight={strong ? 800 : 500} sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
        {value}
      </Typography>
    </Stack>
  );
}

/** END → review bill (optionally discount) → payment → COMPLETE → print. The end time is frozen at END. */
export function EndSessionDialog({ sessionId, onClose }: { sessionId: number | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [referenceNo, setReferenceNo] = useState('');
  const [discountId, setDiscountId] = useState<number | null>(null);
  const idempotencyKey = useRef(randomId());
  // The end time from the first preview; later previews (discount changes) keep billing to this moment.
  const frozenEnd = useRef<string | null>(null);

  const preview = useMutation({
    mutationFn: ({ id, discount }: { id: number; discount: number | null }) => previewEnd(id, discount, frozenEnd.current),
    onSuccess: (s) => {
      if (!frozenEnd.current) frozenEnd.current = s.charges?.calculatedAt ?? null;
    },
  });
  const complete = useMutation({
    mutationFn: (session: GamingSession) =>
      completeSession(
        session.id,
        {
          endedAt: frozenEnd.current ?? session.charges!.calculatedAt,
          paymentMode,
          referenceNo: referenceNo.trim() || undefined,
          discountId,
        },
        idempotencyKey.current,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sessions', 'active'] }),
  });

  const { mutate: loadPreview, reset: resetPreview } = preview;
  const { reset: resetComplete } = complete;
  useEffect(() => {
    if (sessionId !== null) {
      idempotencyKey.current = randomId();
      frozenEnd.current = null;
      setPaymentMode('CASH');
      setReferenceNo('');
      setDiscountId(null);
      resetComplete();
      loadPreview({ id: sessionId, discount: null });
    } else {
      resetPreview();
    }
  }, [sessionId, loadPreview, resetPreview, resetComplete]);

  const changeDiscount = (id: number | null) => {
    setDiscountId(id);
    if (sessionId !== null) loadPreview({ id: sessionId, discount: id });
  };

  const session = complete.data ?? preview.data;
  const charges = session?.charges;
  const done = complete.isSuccess;
  const food = charges?.lines.filter((l) => l.type === 'FOOD') ?? [];
  const print = (format: 'thermal' | 'a4') => navigate(`/bills/${session!.billId}/print?format=${format}&autoprint=1`);

  return (
    <Dialog open={sessionId !== null} onClose={complete.isPending ? undefined : onClose} fullWidth maxWidth="xs">
      <DialogTitle>{done ? 'Bill completed' : `End session${session ? ` · ${session.stationCode}` : ''}`}</DialogTitle>
      <DialogContent>
        {preview.isPending && !preview.data && (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {preview.isError && <Alert severity="error">{toUserMessage(preview.error)}</Alert>}
        {done && session && (
          <Stack alignItems="center" spacing={1} sx={{ py: 1 }}>
            <CheckCircleIcon color="success" sx={{ fontSize: 56 }} />
            <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '0.04em' }}>
              {session.billNumber}
            </Typography>
            <Typography color="text.secondary">
              {formatINR(charges?.totalAmount)} received by {session.paymentMode ?? paymentMode}
            </Typography>
          </Stack>
        )}
        {session && charges && !done && (
          <Stack spacing={0.25}>
            <Row label="Customer" value={session.customerName ?? 'Walk-in'} />
            {session.customerMobile && <Row label="Mobile" value={session.customerMobile} />}
            <Row label="Players" value={String(session.playerCount)} />
            <Row label="Start → End" value={`${formatTime(session.startedAt)} → ${formatTime(charges.calculatedAt)}`} />
            <Row label="Played" value={formatMinutes(charges.actualMinutes)} />
            {charges.creditMinutes > 0 && <Row label="Free gaming credit" value={`− ${formatMinutes(charges.creditMinutes)}`} />}
            <Row label="Billable" value={formatMinutes(charges.billableMinutes)} />
            <Divider sx={{ my: 1 }} />
            <Row label="Gaming" value={formatINR(charges.gamingAmount)} />
            {charges.gamingExplanation && (
              <Typography variant="caption" color="text.secondary">
                {charges.gamingExplanation}
              </Typography>
            )}
            {food.map((l) => (
              <Row key={l.id} muted label={`${l.description} × ${l.quantity}`} value={formatINR(l.amount)} />
            ))}
            {food.length > 0 && <Row label="Food" value={formatINR(charges.foodAmount)} />}
            {charges.discountAmount > 0 && (
              <Row label={`Discount${charges.discountName ? ` (${charges.discountName})` : ''}`} value={`− ${formatINR(charges.discountAmount)}`} />
            )}
            {charges.taxAmount > 0 && <Row label="GST" value={formatINR(charges.taxAmount)} />}
            {charges.roundOff !== 0 && <Row label="Round off" value={formatINR(charges.roundOff)} />}
            <Divider sx={{ my: 1 }} />
            <Row label="TOTAL" value={formatINR(charges.totalAmount)} strong />
            <Stack spacing={2} sx={{ pt: 2 }}>
              <DiscountSelect value={discountId} onChange={changeDiscount} />
              <PaymentSelector mode={paymentMode} onModeChange={setPaymentMode} referenceNo={referenceNo} onReferenceChange={setReferenceNo} />
            </Stack>
            {complete.isError && (
              <Alert severity="error" sx={{ mt: 1.5 }}>
                {toUserMessage(complete.error)}
              </Alert>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3, flexWrap: 'wrap', gap: 1 }}>
        {done ? (
          <>
            <Button variant="outlined" startIcon={<PrintIcon />} onClick={() => print('thermal')}>
              Receipt
            </Button>
            <Button variant="outlined" startIcon={<PrintIcon />} onClick={() => print('a4')}>
              A4
            </Button>
            <Button variant="contained" size="large" sx={{ flex: 1 }} onClick={onClose} autoFocus>
              Done
            </Button>
          </>
        ) : (
          <>
            <Button onClick={onClose} color="inherit" size="large" disabled={complete.isPending}>
              Back
            </Button>
            <Button
              variant="contained"
              color="success"
              size="large"
              sx={{ flex: 1 }}
              disabled={!session || complete.isPending || preview.isPending}
              onClick={() => session && complete.mutate(session)}
            >
              {complete.isPending ? 'Saving…' : `COMPLETE · ${formatINR(charges?.totalAmount)}`}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
