import type { ReactNode } from 'react';
import { Alert, Box, Card, CardContent, CircularProgress, Grid, Stack, Typography } from '@mui/material';
import { BarChart } from '@mui/x-charts/BarChart';
import { useQuery } from '@tanstack/react-query';
import { toUserMessage } from '../api/errors';
import { fetchDashboard } from '../api/reports';
import type { Dashboard } from '../api/types';
import { CATEGORY_COLORS, SINGLE_SERIES } from '../components/charts/chartColors';
import { formatINR, formatMinutes } from '../utils/format';

const money = (v: number | null) => (v == null ? '' : formatINR(v));
const shortDate = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });

function Tile({ label, value, hint, highlight }: { label: string; value: ReactNode; hint?: ReactNode; highlight?: boolean }) {
  return (
    <Card sx={{ height: '100%', borderColor: highlight ? 'primary.main' : undefined }}>
      <CardContent>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        <Typography variant="h4" sx={{ mt: 0.5, fontWeight: 800 }}>{value}</Typography>
        {hint && <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{hint}</Typography>}
      </CardContent>
    </Card>
  );
}

function ChartCard({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="h6">{title}</Typography>
        {subtitle && <Typography variant="body2" color="text.secondary">{subtitle}</Typography>}
        <Box sx={{ mt: 1 }}>{children}</Box>
      </CardContent>
    </Card>
  );
}

const tile = { xs: 12, sm: 6, md: 3 } as const;

function AdminSection({ d }: { d: Dashboard }) {
  const m = d.month!;
  const days = d.revenueByDay ?? [];
  const split = [
    { name: 'Gaming', value: m.gamingRevenue, color: CATEGORY_COLORS.gaming },
    { name: 'Food & drinks', value: m.foodRevenue, color: CATEGORY_COLORS.food },
    { name: 'Rentals', value: m.rentalRevenue, color: CATEGORY_COLORS.rental },
  ];
  const stations = d.stationUtilization ?? [];
  return (
    <>
      <Typography variant="h6" sx={{ mt: 4, mb: 1.5 }}>This month ({m.month})</Typography>
      <Grid container spacing={2}>
        <Grid size={tile}><Tile label="Monthly revenue" value={formatINR(m.netSales)} hint={`${m.billCount} bills`} /></Grid>
        <Grid size={tile}><Tile label="Gaming revenue" value={formatINR(m.gamingRevenue)} hint={`${m.sessionCount} sessions · ${formatMinutes(m.gamingMinutes)} billed`} /></Grid>
        <Grid size={tile}><Tile label="Food revenue" value={formatINR(m.foodRevenue)} /></Grid>
        <Grid size={tile}><Tile label="Rental revenue" value={formatINR(m.rentalRevenue)} hint={`Profit estimate ${formatINR(m.profitEstimate)}`} /></Grid>
      </Grid>

      <Grid container spacing={2} sx={{ mt: 0.5 }}>
        <Grid size={12}>
          <ChartCard title="Revenue by day" subtitle="Last 30 days, by category (before discounts and GST)">
            <BarChart
              height={300}
              dataset={days}
              xAxis={[{ scaleType: 'band', dataKey: 'date', valueFormatter: (v: string) => shortDate(v), categoryGapRatio: 0.35 }]}
              yAxis={[{ valueFormatter: (v: number) => formatINR(v), width: 72, tickNumber: 5 }]}
              series={[
                { dataKey: 'gaming', label: 'Gaming', stack: 'revenue', color: CATEGORY_COLORS.gaming, valueFormatter: money },
                { dataKey: 'food', label: 'Food & drinks', stack: 'revenue', color: CATEGORY_COLORS.food, valueFormatter: money },
                { dataKey: 'rental', label: 'Rentals', stack: 'revenue', color: CATEGORY_COLORS.rental, valueFormatter: money },
              ]}
              grid={{ horizontal: true }}
              borderRadius={4}
            />
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <ChartCard title="Gaming vs food vs rentals" subtitle="This month">
            <BarChart
              height={200}
              layout="horizontal"
              margin={{ right: 32 }}
              dataset={split}
              yAxis={[{ scaleType: 'band', dataKey: 'name', width: 110, colorMap: { type: 'ordinal', values: split.map((s) => s.name), colors: split.map((s) => s.color) } }]}
              xAxis={[{ valueFormatter: (v: number) => formatINR(v), tickNumber: 4 }]}
              series={[{ dataKey: 'value', label: 'Revenue', valueFormatter: money }]}
              hideLegend
              grid={{ vertical: true }}
              borderRadius={4}
            />
          </ChartCard>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <ChartCard title="Revenue by payment method" subtitle="This month, received minus refunds">
            <BarChart
              height={200}
              layout="horizontal"
              margin={{ right: 32 }}
              dataset={d.paymentMethods ?? []}
              yAxis={[{ scaleType: 'band', dataKey: 'mode', width: 70 }]}
              xAxis={[{ valueFormatter: (v: number) => formatINR(v), tickNumber: 4 }]}
              series={[{ dataKey: 'amount', label: 'Amount', color: SINGLE_SERIES, valueFormatter: money }]}
              hideLegend
              grid={{ vertical: true }}
              borderRadius={4}
            />
          </ChartCard>
        </Grid>
        <Grid size={12}>
          <ChartCard title="Station utilization" subtitle="This month: hours played ÷ opening hours">
            <BarChart
              height={Math.max(160, stations.length * 44 + 40)}
              layout="horizontal"
              margin={{ right: 32 }}
              dataset={stations}
              yAxis={[{ scaleType: 'band', dataKey: 'stationCode', width: 80 }]}
              xAxis={[{ min: 0, max: 100, valueFormatter: (v: number) => `${v}%`, tickNumber: 5 }]}
              series={[
                {
                  dataKey: 'utilizationPercent',
                  label: 'Utilization',
                  color: SINGLE_SERIES,
                  valueFormatter: (v: number | null, ctx) => {
                    const s = stations[ctx.dataIndex];
                    return v == null || !s ? '' : `${v.toFixed(1)}% · ${formatMinutes(s.minutesPlayed)} · ${s.sessions} sessions`;
                  },
                },
              ]}
              hideLegend
              grid={{ vertical: true }}
              borderRadius={4}
            />
          </ChartCard>
        </Grid>
      </Grid>
    </>
  );
}

export function DashboardPage() {
  const dashboard = useQuery({ queryKey: ['dashboard'], queryFn: fetchDashboard, refetchInterval: 60_000 });
  const d = dashboard.data;
  if (dashboard.isLoading) return <CircularProgress />;
  if (dashboard.isError || !d) return <Alert severity="error">{toUserMessage(dashboard.error)}</Alert>;
  const t = d.today;
  return (
    <Box>
      <Stack direction="row" alignItems="baseline" spacing={2} sx={{ mb: 2 }}>
        <Typography variant="h4">Dashboard</Typography>
        <Typography color="text.secondary">Business day {d.businessDate}</Typography>
      </Stack>
      <Grid container spacing={2}>
        <Grid size={tile}><Tile highlight label="Today's revenue" value={formatINR(t.netSales)} hint={`${t.billCount} bills · avg ${formatINR(t.averageBill)}`} /></Grid>
        <Grid size={tile}><Tile label="Gaming revenue" value={formatINR(t.gamingRevenue)} hint={`${t.sessionCount} sessions · ${formatMinutes(t.gamingMinutes)} billed`} /></Grid>
        <Grid size={tile}><Tile label="Food revenue" value={formatINR(t.foodRevenue)} hint={`${t.foodUnits} items sold`} /></Grid>
        <Grid size={tile}><Tile label="Rental revenue" value={formatINR(t.rentalRevenue)} hint={d.rentalsOverdue > 0 ? <Box component="span" sx={{ color: 'error.main' }}>{d.rentalsOut} discs out · {d.rentalsOverdue} overdue</Box> : `${d.rentalsOut} discs out`} /></Grid>
        <Grid size={tile}><Tile label="Active stations" value={`${d.activeStations} / ${d.totalStations}`} hint="Playing right now" /></Grid>
        <Grid size={tile}><Tile label="Gaming hours" value={formatMinutes(t.gamingMinutes)} hint="Billed today" /></Grid>
        <Grid size={tile}><Tile label="Average bill" value={formatINR(t.averageBill)} /></Grid>
        {t.profitEstimate != null && (
          <Grid size={tile}><Tile label="Today's profit estimate" value={formatINR(t.profitEstimate)} hint="Sales excl. GST − food cost" /></Grid>
        )}
      </Grid>
      {d.month && <AdminSection d={d} />}
    </Box>
  );
}
