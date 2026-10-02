import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  ButtonGroup,
  CircularProgress,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import { useMutation, useQuery } from '@tanstack/react-query';
import { toUserMessage } from '../api/errors';
import { downloadReport, fetchReport, fetchReportList } from '../api/reports';
import type { ReportColumnType, ReportTable } from '../api/types';
import { formatDateTime, formatINR, localIsoDate } from '../utils/format';

function formatCell(type: ReportColumnType, value: string | number | null | undefined): string {
  if (value == null || value === '') return '';
  switch (type) {
    case 'MONEY':
      return formatINR(Number(value));
    case 'PERCENT':
      return `${Number(value).toFixed(2)}%`;
    case 'DECIMAL':
      return Number(value).toFixed(2);
    case 'DATETIME':
      return formatDateTime(String(value));
    default:
      return String(value);
  }
}

const NUMERIC: ReportColumnType[] = ['MONEY', 'PERCENT', 'DECIMAL', 'INTEGER'];

function presets(): { label: string; from: string; to: string }[] {
  const now = new Date();
  const today = localIsoDate(now);
  const yesterday = localIsoDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1));
  const monthStart = localIsoDate(new Date(now.getFullYear(), now.getMonth(), 1));
  const lastMonthStart = localIsoDate(new Date(now.getFullYear(), now.getMonth() - 1, 1));
  const lastMonthEnd = localIsoDate(new Date(now.getFullYear(), now.getMonth(), 0));
  const last7 = localIsoDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6));
  return [
    { label: 'Today', from: today, to: today },
    { label: 'Yesterday', from: yesterday, to: yesterday },
    { label: 'Last 7 days', from: last7, to: today },
    { label: 'This month', from: monthStart, to: today },
    { label: 'Last month', from: lastMonthStart, to: lastMonthEnd },
  ];
}

function ReportView({ report }: { report: ReportTable }) {
  const align = (t: ReportColumnType) => (NUMERIC.includes(t) ? 'right' : 'left');
  return (
    <Box>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              {report.columns.map((c) => (
                <TableCell key={c.key} align={align(c.type)} sx={{ whiteSpace: 'nowrap' }}>
                  {c.label}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {report.rows.map((row, i) => (
              <TableRow key={i} hover>
                {report.columns.map((c) => (
                  <TableCell
                    key={c.key}
                    align={align(c.type)}
                    sx={{ whiteSpace: 'nowrap', color: row.low === 'LOW' && c.key === 'low' ? 'warning.main' : undefined }}
                  >
                    {formatCell(c.type, row[c.key])}
                  </TableCell>
                ))}
              </TableRow>
            ))}
            {report.rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={report.columns.length}>
                  <Typography color="text.secondary">Nothing in this period.</Typography>
                </TableCell>
              </TableRow>
            )}
            {report.totals && report.rows.length > 0 && (
              <TableRow sx={{ '& td': { fontWeight: 800, borderTop: 2, borderColor: 'divider' } }}>
                {report.columns.map((c, i) => (
                  <TableCell key={c.key} align={align(c.type)} sx={{ whiteSpace: 'nowrap' }}>
                    {report.totals![c.key] != null ? formatCell(c.type, report.totals![c.key]) : i === 0 ? 'Total' : ''}
                  </TableCell>
                ))}
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Box>
      {report.note && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5, maxWidth: 900 }}>
          {report.note}
        </Typography>
      )}
    </Box>
  );
}

export function ReportsPage() {
  const list = useQuery({ queryKey: ['reports', 'list'], queryFn: fetchReportList });
  const initial = presets()[0];
  const [type, setType] = useState('daily');
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const report = useQuery({
    queryKey: ['reports', type, from, to],
    queryFn: () => fetchReport(type, from, to),
    enabled: Boolean(from && to),
  });
  const download = useMutation({ mutationFn: (format: 'csv' | 'xlsx') => downloadReport(type, format, from, to) });

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Reports
      </Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 1.5 }}>
        <TextField select label="Report" value={type} onChange={(e) => setType(e.target.value)} sx={{ minWidth: 240 }}>
          {(list.data ?? [{ type: 'daily', title: 'Daily sales', adminOnly: false }]).map((r) => (
            <MenuItem key={r.type} value={r.type}>
              {r.title}
            </MenuItem>
          ))}
        </TextField>
        <TextField label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ maxWidth: 200 }} />
        <TextField label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} sx={{ maxWidth: 200 }} />
        <Box sx={{ flex: 1 }} />
        <ButtonGroup variant="outlined" sx={{ alignSelf: { md: 'center' } }}>
          <Button startIcon={<DownloadIcon />} disabled={!report.data || download.isPending} onClick={() => download.mutate('csv')}>
            CSV
          </Button>
          <Button startIcon={<DownloadIcon />} disabled={!report.data || download.isPending} onClick={() => download.mutate('xlsx')}>
            Excel
          </Button>
        </ButtonGroup>
      </Stack>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
        {presets().map((p) => (
          <Button
            key={p.label}
            size="small"
            variant={p.from === from && p.to === to ? 'contained' : 'text'}
            onClick={() => {
              setFrom(p.from);
              setTo(p.to);
            }}
          >
            {p.label}
          </Button>
        ))}
      </Stack>
      {download.isError && <Alert severity="error" sx={{ mb: 2 }}>{toUserMessage(download.error)}</Alert>}
      {report.isError && <Alert severity="error">{toUserMessage(report.error)}</Alert>}
      {report.isLoading && <CircularProgress />}
      {report.data && (
        <>
          <Typography variant="h6" gutterBottom>
            {report.data.title}{' '}
            <Typography component="span" color="text.secondary">
              {report.data.from === report.data.to ? report.data.from : `${report.data.from} to ${report.data.to}`}
            </Typography>
          </Typography>
          <ReportView report={report.data} />
        </>
      )}
    </Box>
  );
}
