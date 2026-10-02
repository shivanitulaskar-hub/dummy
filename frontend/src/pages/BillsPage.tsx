import { useState } from 'react';
import {
  Alert,
  Box,
  Chip,
  InputAdornment,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { searchBills } from '../api/bills';
import { toUserMessage } from '../api/errors';
import type { BillStatus } from '../api/types';
import { BillDetailDialog } from '../components/bills/BillDetailDialog';
import { formatDateTime, formatINR } from '../utils/format';

export function BillsPage() {
  const [q, setQ] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [status, setStatus] = useState<BillStatus | ''>('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<number | null>(null);

  const bills = useQuery({
    queryKey: ['bills', q, from, to, status, page, size],
    queryFn: () =>
      searchBills({ q: q || undefined, from: from || undefined, to: to || undefined, status: status || undefined, page, size }),
    placeholderData: keepPreviousData,
  });

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Bills
      </Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          placeholder="Bill number, customer name or mobile"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }}
          sx={{ flex: 2 }}
        />
        <TextField label="From" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} slotProps={{ inputLabel: { shrink: true } }} sx={{ flex: 1 }} />
        <TextField label="To" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} slotProps={{ inputLabel: { shrink: true } }} sx={{ flex: 1 }} />
        <TextField select label="Status" value={status} onChange={(e) => { setStatus(e.target.value as BillStatus | ''); setPage(0); }} sx={{ flex: 1 }}>
          <MenuItem value="">Paid & cancelled</MenuItem>
          <MenuItem value="PAID">Paid</MenuItem>
          <MenuItem value="CANCELLED">Cancelled</MenuItem>
        </TextField>
      </Stack>
      {bills.isError && <Alert severity="error">{toUserMessage(bills.error)}</Alert>}
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Bill no.</TableCell>
              <TableCell>Date</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Customer</TableCell>
              <TableCell>Payment</TableCell>
              <TableCell align="right">Total</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {bills.data?.content.map((b) => (
              <TableRow key={b.id} hover onClick={() => setSelected(b.id)} sx={{ cursor: 'pointer' }}>
                <TableCell sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                  {b.billNumber}
                  {b.status === 'CANCELLED' && <Chip size="small" color="error" label="Cancelled" sx={{ ml: 1 }} />}
                </TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(b.paidAt)}</TableCell>
                <TableCell>{b.billType === 'WALK_IN' ? 'Counter' : b.billType === 'GAMING' ? 'Gaming' : 'Rental'}</TableCell>
                <TableCell>
                  {b.customerName ?? 'Walk-in'}
                  {b.customerMobile && (
                    <Typography variant="caption" color="text.secondary" display="block">
                      {b.customerMobile}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>{b.paymentMode ?? '–'}</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>
                  {formatINR(b.totalAmount)}
                </TableCell>
              </TableRow>
            ))}
            {bills.data && bills.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography color="text.secondary">No bills found.</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>
      {bills.data && (
        <TablePagination
          component="div"
          count={bills.data.totalElements}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => {
            setSize(Number(e.target.value));
            setPage(0);
          }}
        />
      )}
      <BillDetailDialog billId={selected} onClose={() => setSelected(null)} />
    </Box>
  );
}
