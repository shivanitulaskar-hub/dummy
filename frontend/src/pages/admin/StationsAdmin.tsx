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
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/DeleteOutline';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { createStation, deleteStation, fetchStations, updateStation } from '../../api/stations';
import type { Station, StationRequest } from '../../api/types';

const EMPTY: StationRequest = { code: '', name: '', consoleLabel: '', tvLabel: '', active: true, displayOrder: 0, notes: '' };

function StationDialog({ open, station, onClose }: { open: boolean; station: Station | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<StationRequest>(EMPTY);
  useEffect(() => {
    if (open) setForm(station ? { ...station } : EMPTY);
  }, [open, station]);
  const mutation = useMutation({
    mutationFn: () => (station ? updateStation(station.id, form) : createStation(form)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stations'] });
      onClose();
    },
  });
  const set = (key: keyof StationRequest, value: string | number | boolean) => setForm((f) => ({ ...f, [key]: value }));
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm" TransitionProps={{ onExited: () => mutation.reset() }}>
      <DialogTitle>{station ? `Edit ${station.code}` : 'Add gaming station'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {mutation.isError && <Alert severity="error">{toUserMessage(mutation.error)}</Alert>}
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 2fr' } }}>
            <TextField label="Code" value={form.code} onChange={(e) => set('code', e.target.value)} placeholder="PS5-06" required />
            <TextField label="Name" value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="PS5 Station 6" required />
            <TextField label="PS5 number" value={form.consoleLabel ?? ''} onChange={(e) => set('consoleLabel', e.target.value)} placeholder="PS5 #6" />
            <TextField label="TV" value={form.tvLabel ?? ''} onChange={(e) => set('tvLabel', e.target.value)} placeholder='TV #6 (44")' />
            <TextField label="Display order" type="number" value={form.displayOrder ?? 0} onChange={(e) => set('displayOrder', Number(e.target.value))} />
            <FormControlLabel control={<Switch checked={form.active ?? true} onChange={(e) => set('active', e.target.checked)} />} label="Active" />
          </Box>
          <TextField label="Notes" value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value)} multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={onClose} color="inherit">
          Cancel
        </Button>
        <Button variant="contained" onClick={() => mutation.mutate()} disabled={mutation.isPending || !form.code || !form.name}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function StationsAdmin() {
  const queryClient = useQueryClient();
  const stations = useQuery({ queryKey: ['stations', 'all'], queryFn: () => fetchStations(true) });
  const [dialog, setDialog] = useState<{ open: boolean; station: Station | null }>({ open: false, station: null });
  const remove = useMutation({
    mutationFn: deleteStation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['stations'] }),
  });

  return (
    <Box>
      <Stack direction="row" sx={{ mb: 2 }}>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ open: true, station: null })}>
          Add station
        </Button>
      </Stack>
      {(stations.error || remove.error) && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => remove.reset()}>
          {toUserMessage(stations.error ?? remove.error)}
        </Alert>
      )}
      <Box sx={{ overflowX: 'auto' }}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Code</TableCell>
              <TableCell>Name</TableCell>
              <TableCell>PS5</TableCell>
              <TableCell>TV</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {stations.data?.map((s) => (
              <TableRow key={s.id}>
                <TableCell sx={{ fontWeight: 700 }}>{s.code}</TableCell>
                <TableCell>{s.name}</TableCell>
                <TableCell>{s.consoleLabel}</TableCell>
                <TableCell>{s.tvLabel}</TableCell>
                <TableCell>
                  <Chip size="small" label={s.active ? 'Active' : 'Inactive'} color={s.active ? 'success' : 'default'} variant="outlined" />
                </TableCell>
                <TableCell align="right">
                  <Tooltip title="Edit">
                    <IconButton onClick={() => setDialog({ open: true, station: s })}>
                      <EditIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Delete (only stations never used)">
                    <IconButton
                      onClick={() => {
                        if (window.confirm(`Delete ${s.code}? Stations with history can only be deactivated.`)) {
                          remove.mutate(s.id);
                        }
                      }}
                    >
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      <StationDialog open={dialog.open} station={dialog.station} onClose={() => setDialog({ open: false, station: null })} />
    </Box>
  );
}
