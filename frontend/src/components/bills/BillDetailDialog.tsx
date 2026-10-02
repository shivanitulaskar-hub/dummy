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
  Divider,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import PrintIcon from '@mui/icons-material/Print';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { cancelBill, fetchBill } from '../../api/bills';
import { toUserMessage } from '../../api/errors';
import { useAuth } from '../../auth/AuthContext';
import { formatDateTime, formatINR, formatMinutes, formatTime } from '../../utils/format';

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2} sx={{ py: 0.3 }}>
      <Typography color={strong ? 'text.primary' : 'text.secondary'} fontWeight={strong ? 800 : 400}>
        {label}
      </Typography>
      <Typography fontWeight={strong ? 800 : 500} sx={{ whiteSpace: 'nowrap' }}>
        {value}
      </Typography>
    </Stack>
  );
}

export function BillDetailDialog({ billId, onClose }: { billId: number | null; onClose: () => void }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [cancelling, setCancelling] = useState(false);
  const [reason, setReason] = useState('');
  const bill = useQuery({ queryKey: ['bill', billId], queryFn: () => fetchBill(billId!), enabled: billId !== null });
  const cancel = useMutation({
    mutationFn: () => cancelBill(billId!, reason.trim()),
    onSuccess: () => {
      setCancelling(false);
      queryClient.invalidateQueries({ queryKey: ['bill', billId] });
      queryClient.invalidateQueries({ queryKey: ['bills'] });
      queryClient.invalidateQueries({ queryKey: ['food-items'] });
    },
  });
  const b = bill.data;

  return (
    <Dialog
      open={billId !== null}
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      TransitionProps={{ onExited: () => { setCancelling(false); setReason(''); cancel.reset(); } }}
    >
      <DialogTitle sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        {b?.billNumber ?? 'Bill'}
        {b && <Chip size="small" label={b.status} color={b.status === 'PAID' ? 'success' : b.status === 'CANCELLED' ? 'error' : 'default'} />}
        {b && <Chip size="small" variant="outlined" label={b.billType === 'WALK_IN' ? 'Counter sale' : b.billType} />}
      </DialogTitle>
      <DialogContent>
        {bill.isLoading && <CircularProgress />}
        {bill.isError && <Alert severity="error">{toUserMessage(bill.error)}</Alert>}
        {b && (
          <Stack spacing={0.5}>
            <Row label="Date" value={formatDateTime(b.paidAt ?? b.createdAt)} />
            <Row label="Customer" value={`${b.customerName ?? 'Walk-in'}${b.customerMobile ? ` · ${b.customerMobile}` : ''}`} />
            {b.gaming && (
              <>
                <Row label="Station" value={`${b.gaming.stationCode} · ${b.gaming.playerCount} player(s)`} />
                <Row
                  label="Time"
                  value={`${formatTime(b.gaming.startedAt)} – ${formatTime(b.gaming.endedAt)} · played ${formatMinutes(b.gaming.actualMinutes ?? 0)}, billed ${formatMinutes(b.gaming.billableMinutes ?? 0)}`}
                />
              </>
            )}
            {b.credits.map((c, i) => (
              <Row key={i} label="Credit" value={`${c.minutes} min · ${c.reason}`} />
            ))}
            <Divider sx={{ my: 1 }} />
            {b.lines.map((l) => (
              <Row key={l.id} label={`${l.description}${l.quantity > 1 ? ` × ${l.quantity}` : ''}`} value={formatINR(l.amount)} />
            ))}
            <Divider sx={{ my: 1 }} />
            <Row label="Subtotal" value={formatINR(b.subtotal)} />
            {b.discountAmount > 0 && <Row label={`Discount (${b.discountName ?? ''})`} value={`− ${formatINR(b.discountAmount)}`} />}
            {b.taxAmount > 0 && <Row label="GST" value={formatINR(b.taxAmount)} />}
            {b.roundOff !== 0 && <Row label="Round off" value={formatINR(b.roundOff)} />}
            <Row label="TOTAL" value={formatINR(b.totalAmount)} strong />
            {b.payments.map((p) => (
              <Row
                key={p.id}
                label={`${p.type === 'REFUND' ? 'Refund' : 'Paid'} · ${p.mode}${p.referenceNo ? ` (${p.referenceNo})` : ''}`}
                value={`${p.type === 'REFUND' ? '− ' : ''}${formatINR(p.amount)}`}
              />
            ))}
            {b.status === 'CANCELLED' && (
              <Alert severity="error" sx={{ mt: 1 }}>
                Cancelled {formatDateTime(b.cancelledAt)}: {b.cancelReason}
              </Alert>
            )}
            {cancelling && (
              <Box sx={{ mt: 2 }}>
                <Alert severity="warning" sx={{ mb: 1.5 }}>
                  The full amount is recorded as refunded in the original payment mode and any food goes back into stock.
                </Alert>
                {cancel.isError && <Alert severity="error" sx={{ mb: 1.5 }}>{toUserMessage(cancel.error)}</Alert>}
                <TextField label="Reason for cancelling" value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
              </Box>
            )}
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3, flexWrap: 'wrap', gap: 1 }}>
        {b && b.status !== 'OPEN' && !cancelling && (
          <>
            <Button startIcon={<PrintIcon />} onClick={() => navigate(`/bills/${b.id}/print?format=thermal`)}>
              Receipt
            </Button>
            <Button startIcon={<PrintIcon />} onClick={() => navigate(`/bills/${b.id}/print?format=a4`)}>
              A4
            </Button>
          </>
        )}
        <Box sx={{ flex: 1 }} />
        {user?.role === 'ADMIN' && b?.status === 'PAID' && !cancelling && (
          <Button color="error" onClick={() => setCancelling(true)}>
            Cancel bill
          </Button>
        )}
        {cancelling ? (
          <>
            <Button color="inherit" onClick={() => setCancelling(false)}>
              Back
            </Button>
            <Button variant="contained" color="error" disabled={!reason.trim() || cancel.isPending} onClick={() => cancel.mutate()}>
              Cancel & refund
            </Button>
          </>
        ) : (
          <Button variant="contained" onClick={onClose}>
            Close
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
