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
  InputAdornment,
  Stack,
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
import AddIcon from '@mui/icons-material/PersonAdd';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createCustomer, fetchCustomerHistory, searchCustomers, updateCustomer } from '../api/customers';
import { toUserMessage } from '../api/errors';
import type { Customer } from '../api/types';
import { BillDetailDialog } from '../components/bills/BillDetailDialog';
import { RentalStatusChip } from '../components/rentals/RentalStatusChip';
import { formatDateTime, formatINR, formatMinutes } from '../utils/format';

function CustomerFormDialog({ open, customer, onClose }: { open: boolean; customer: Customer | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [notes, setNotes] = useState('');
  const save = useMutation({
    mutationFn: () => {
      const request = { name: name.trim(), mobile: mobile.trim() || null, email: email.trim() || null, notes: notes.trim() || null };
      return customer ? updateCustomer(customer.id, request) : createCustomer(request);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      queryClient.invalidateQueries({ queryKey: ['customer-history'] });
      onClose();
    },
  });
  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      maxWidth="xs"
      TransitionProps={{
        onEnter: () => {
          setName(customer?.name ?? '');
          setMobile(customer?.mobile ?? '');
          setEmail(customer?.email ?? '');
          setNotes(customer?.notes ?? '');
          save.reset();
        },
      }}
    >
      <DialogTitle>{customer ? 'Edit customer' : 'New customer'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} autoFocus required />
          <TextField label="Mobile" value={mobile} onChange={(e) => setMobile(e.target.value)} inputMode="tel" helperText="10-digit Indian mobile number" />
          <TextField label="Email (optional)" value={email} onChange={(e) => setEmail(e.target.value)} type="email" />
          <TextField label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={!name.trim() || save.isPending} onClick={() => save.mutate()}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Box sx={{ minWidth: 110 }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h6">{value}</Typography>
    </Box>
  );
}

function HistoryDialog({ customerId, onClose, onEdit }: { customerId: number | null; onClose: () => void; onEdit: (c: Customer) => void }) {
  const [tab, setTab] = useState(0);
  const [billId, setBillId] = useState<number | null>(null);
  const history = useQuery({
    queryKey: ['customer-history', customerId],
    queryFn: () => fetchCustomerHistory(customerId!),
    enabled: customerId !== null,
  });
  const h = history.data;
  const c = h?.customer;
  return (
    <Dialog open={customerId !== null} onClose={onClose} fullWidth maxWidth="md" TransitionProps={{ onExited: () => setTab(0) }}>
      <DialogTitle>
        {c ? c.name : 'Customer'}
        {c?.mobile && <Typography component="span" color="text.secondary" sx={{ ml: 1.5 }}>{c.mobile}</Typography>}
      </DialogTitle>
      <DialogContent>
        {history.isLoading && <CircularProgress />}
        {history.isError && <Alert severity="error">{toUserMessage(history.error)}</Alert>}
        {h && c && (
          <>
            <Stack direction="row" flexWrap="wrap" gap={3} sx={{ mb: 2 }}>
              <Stat label="Sessions" value={String(c.totalSessions)} />
              <Stat label="Gaming time" value={formatMinutes(c.totalGamingMinutes)} />
              <Stat label="Total spent" value={formatINR(c.totalSpent)} />
              <Stat label="Last visit" value={formatDateTime(c.lastVisitAt)} />
              <Stat label="Customer since" value={formatDateTime(c.createdAt)} />
            </Stack>
            {(c.email || c.notes) && (
              <Typography color="text.secondary" sx={{ mb: 2 }}>
                {[c.email, c.notes].filter(Boolean).join(' · ')}
              </Typography>
            )}
            <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ mb: 1, borderBottom: 1, borderColor: 'divider' }}>
              <Tab label={`Sessions (${h.sessions.length})`} />
              <Tab label={`Bills (${h.bills.length})`} />
              <Tab label={`Food (${h.food.length})`} />
              <Tab label={`Rentals (${h.rentals.length})`} />
            </Tabs>
            <Box sx={{ overflowX: 'auto' }}>
              {tab === 0 && (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Started</TableCell>
                      <TableCell>Station</TableCell>
                      <TableCell align="right">Players</TableCell>
                      <TableCell align="right">Billed</TableCell>
                      <TableCell align="right">Gaming</TableCell>
                      <TableCell>Bill</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {h.sessions.map((s) => (
                      <TableRow key={s.id} hover sx={{ cursor: s.billNumber ? 'pointer' : 'default' }} onClick={() => s.billNumber && setBillId(s.billId)}>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(s.startedAt)}</TableCell>
                        <TableCell>{s.stationCode}</TableCell>
                        <TableCell align="right">{s.playerCount}</TableCell>
                        <TableCell align="right">{s.billableMinutes != null ? formatMinutes(s.billableMinutes) : '–'}</TableCell>
                        <TableCell align="right">{formatINR(s.gamingAmount)}</TableCell>
                        <TableCell>{s.status === 'COMPLETED' ? s.billNumber : <Chip size="small" label={s.status.toLowerCase()} />}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              {tab === 1 && (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Bill no.</TableCell>
                      <TableCell>Date</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell align="right">Total</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {h.bills.map((b) => (
                      <TableRow key={b.id} hover sx={{ cursor: 'pointer' }} onClick={() => setBillId(b.id)}>
                        <TableCell sx={{ fontWeight: 700 }}>
                          {b.billNumber}
                          {b.status === 'CANCELLED' && <Chip size="small" color="error" label="Cancelled" sx={{ ml: 1 }} />}
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(b.paidAt)}</TableCell>
                        <TableCell>{b.billType === 'WALK_IN' ? 'Counter' : b.billType === 'GAMING' ? 'Gaming' : 'Rental'}</TableCell>
                        <TableCell align="right">{formatINR(b.totalAmount)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              {tab === 2 && (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Item</TableCell>
                      <TableCell align="right">Qty</TableCell>
                      <TableCell align="right">Amount</TableCell>
                      <TableCell>Last bought</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {h.food.map((f) => (
                      <TableRow key={f.item}>
                        <TableCell>{f.item}</TableCell>
                        <TableCell align="right">{f.quantity}</TableCell>
                        <TableCell align="right">{formatINR(f.amount)}</TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(f.lastBoughtAt)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              {tab === 3 && (
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Rental</TableCell>
                      <TableCell>Game</TableCell>
                      <TableCell>Out</TableCell>
                      <TableCell>Due / returned</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Paid</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {h.rentals.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell sx={{ fontWeight: 700 }}>{r.rentalNumber}</TableCell>
                        <TableCell>{r.gameName}</TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.outAt)}</TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(r.returnedAt ?? r.expectedReturnAt)}</TableCell>
                        <TableCell><RentalStatusChip status={r.status} /></TableCell>
                        <TableCell align="right">{formatINR(r.totalAmount)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        {c && (
          <Button startIcon={<EditIcon />} onClick={() => onEdit(c)}>
            Edit details
          </Button>
        )}
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" onClick={onClose}>Close</Button>
      </DialogActions>
      <BillDetailDialog billId={billId} onClose={() => setBillId(null)} />
    </Dialog>
  );
}

export function CustomersPage() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [selected, setSelected] = useState<number | null>(null);
  const [form, setForm] = useState<{ open: boolean; customer: Customer | null }>({ open: false, customer: null });
  const customers = useQuery({
    queryKey: ['customers', q, page, size],
    queryFn: () => searchCustomers({ q: q.trim() || undefined, page, size }),
    placeholderData: keepPreviousData,
  });

  return (
    <Box>
      <Stack direction="row" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h4">Customers</Typography>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setForm({ open: true, customer: null })}>
          New customer
        </Button>
      </Stack>
      <TextField
        placeholder="Search by name, mobile or bill number"
        value={q}
        onChange={(e) => {
          setQ(e.target.value);
          setPage(0);
        }}
        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> } }}
        sx={{ mb: 2, maxWidth: 560 }}
      />
      {customers.isError && <Alert severity="error">{toUserMessage(customers.error)}</Alert>}
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Mobile</TableCell>
              <TableCell align="right">Sessions</TableCell>
              <TableCell align="right">Gaming time</TableCell>
              <TableCell align="right">Spent</TableCell>
              <TableCell>Last visit</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {customers.data?.content.map((c) => (
              <TableRow key={c.id} hover sx={{ cursor: 'pointer' }} onClick={() => setSelected(c.id)}>
                <TableCell sx={{ fontWeight: 700 }}>{c.name}</TableCell>
                <TableCell>{c.mobile ?? '–'}</TableCell>
                <TableCell align="right">{c.totalSessions}</TableCell>
                <TableCell align="right">{formatMinutes(c.totalGamingMinutes)}</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>{formatINR(c.totalSpent)}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(c.lastVisitAt)}</TableCell>
              </TableRow>
            ))}
            {customers.data && customers.data.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography color="text.secondary">No customers found.</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>
      {customers.data && (
        <TablePagination
          component="div"
          count={customers.data.totalElements}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, p) => setPage(p)}
          onRowsPerPageChange={(e) => {
            setSize(Number(e.target.value));
            setPage(0);
          }}
        />
      )}
      <HistoryDialog customerId={selected} onClose={() => setSelected(null)} onEdit={(c) => setForm({ open: true, customer: c })} />
      <CustomerFormDialog open={form.open} customer={form.customer} onClose={() => setForm({ open: false, customer: null })} />
    </Box>
  );
}
