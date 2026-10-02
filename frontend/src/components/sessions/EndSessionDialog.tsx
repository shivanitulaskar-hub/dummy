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
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { completeSession, previewEnd } from '../../api/sessions';
import type { GamingSession, PaymentMode } from '../../api/types';
import { formatINR, formatMinutes, formatTime } from '../../utils/format';
import { randomId } from '../../utils/id';

const PAYMENT_MODES: PaymentMode[] = ['CASH', 'UPI', 'CARD', 'OTHER'];

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between" sx={{ py: 0.5 }}>
      <Typography color={strong ? 'text.primary' : 'text.secondary'} fontWeight={strong ? 800 : 400}>
        {label}
      </Typography>
      <Typography fontWeight={strong ? 800 : 500} sx={{ fontVariantNumeric: 'tabular-nums' }}>
        {value}
      </Typography>
    </Stack>
  );
}

/** END → review bill → choose payment → COMPLETE. The end time is frozen when END is pressed. */
export function EndSessionDialog({ sessionId, onClose }: { sessionId: number | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [paymentMode, setPaymentMode] = useState<PaymentMode>('CASH');
  const [referenceNo, setReferenceNo] = useState('');
  const idempotencyKey = useRef(randomId());

  const preview = useMutation({ mutationFn: previewEnd });
  const complete = useMutation({
    mutationFn: (session: GamingSession) =>
      completeSession(
        session.id,
        {
          endedAt: session.charges!.calculatedAt,
          paymentMode,
          referenceNo: referenceNo.trim() || undefined,
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
      setPaymentMode('CASH');
      setReferenceNo('');
      resetComplete();
      loadPreview(sessionId);
    } else {
      resetPreview();
    }
  }, [sessionId, loadPreview, resetPreview, resetComplete]);

  const session = complete.data ?? preview.data;
  const charges = session?.charges;
  const done = complete.isSuccess;

  return (
    <Dialog open={sessionId !== null} onClose={complete.isPending ? undefined : onClose} fullWidth maxWidth="xs">
      <DialogTitle>{done ? 'Bill completed' : `End session${session ? ` · ${session.stationCode}` : ''}`}</DialogTitle>
      <DialogContent>
        {preview.isPending && (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 4 }}>
            <CircularProgress />
          </Box>
        )}
        {preview.isError && <Alert severity="error">{toUserMessage(preview.error)}</Alert>}
        {done && session && (
          <Stack alignItems="center" spacing={1} sx={{ py: 1, mb: 1 }}>
            <CheckCircleIcon color="success" sx={{ fontSize: 56 }} />
            <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '0.04em' }}>
              {session.billNumber}
            </Typography>
            <Typography color="text.secondary">
              {formatINR(charges?.totalAmount)} received by {session.paymentMode}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Invoice printing arrives in Phase 3.
            </Typography>
          </Stack>
        )}
        {session && charges && !done && (
          <Stack spacing={0.5}>
            <Row label="Customer" value={session.customerName ?? 'Walk-in'} />
            {session.customerMobile && <Row label="Mobile" value={session.customerMobile} />}
            <Row label="Players" value={String(session.playerCount)} />
            <Row label="Start → End" value={`${formatTime(session.startedAt)} → ${formatTime(charges.calculatedAt)}`} />
            <Row label="Played" value={formatMinutes(charges.actualMinutes)} />
            {charges.creditMinutes > 0 && <Row label="Free credit" value={`− ${formatMinutes(charges.creditMinutes)}`} />}
            <Row label="Billable" value={formatMinutes(charges.billableMinutes)} />
            <Divider sx={{ my: 1 }} />
            <Row label="Gaming" value={formatINR(charges.gamingAmount)} />
            {charges.gamingExplanation && (
              <Typography variant="caption" color="text.secondary">
                {charges.gamingExplanation}
              </Typography>
            )}
            <Row label="Food" value={formatINR(charges.foodAmount)} />
            {charges.discountAmount > 0 && <Row label="Discount" value={`− ${formatINR(charges.discountAmount)}`} />}
            {charges.taxAmount > 0 && <Row label="GST" value={formatINR(charges.taxAmount)} />}
            {charges.roundOff !== 0 && <Row label="Round off" value={formatINR(charges.roundOff)} />}
            <Divider sx={{ my: 1 }} />
            <Row label="TOTAL" value={formatINR(charges.totalAmount)} strong />
            <Box sx={{ pt: 2 }}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Payment
              </Typography>
              <ToggleButtonGroup
                exclusive
                fullWidth
                color="primary"
                value={paymentMode}
                onChange={(_, v: PaymentMode | null) => v && setPaymentMode(v)}
              >
                {PAYMENT_MODES.map((m) => (
                  <ToggleButton key={m} value={m} sx={{ py: 1.25, fontWeight: 800 }}>
                    {m}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Box>
            {(paymentMode === 'UPI' || paymentMode === 'CARD') && (
              <TextField
                label="Reference no. (optional)"
                value={referenceNo}
                onChange={(e) => setReferenceNo(e.target.value)}
                size="small"
                sx={{ mt: 1.5 }}
              />
            )}
            {complete.isError && (
              <Alert severity="error" sx={{ mt: 1.5 }}>
                {toUserMessage(complete.error)}
              </Alert>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        {done ? (
          <Button variant="contained" size="large" fullWidth onClick={onClose} autoFocus>
            Done
          </Button>
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
              disabled={!session || complete.isPending}
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
