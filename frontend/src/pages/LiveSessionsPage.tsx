import { Alert, Box, Button, Card, CardContent, Chip, Skeleton, Stack, Tooltip, Typography } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import TvIcon from '@mui/icons-material/Tv';
import { useQuery } from '@tanstack/react-query';
import { toUserMessage } from '../api/errors';
import { fetchStations } from '../api/stations';
import type { Station } from '../api/types';

function StationCard({ station }: { station: Station }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%' }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '0.04em' }}>
            {station.code}
          </Typography>
          <Chip size="small" label="Status in Phase 2" variant="outlined" />
        </Stack>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ color: 'text.secondary' }}>
          <TvIcon fontSize="small" />
          <Typography variant="body2">
            {[station.consoleLabel, station.tvLabel].filter(Boolean).join(' · ') || station.name}
          </Typography>
        </Stack>
        <Box sx={{ flex: 1 }} />
        <Tooltip title="Session management is built in Phase 2">
          <span>
            <Button variant="contained" size="large" fullWidth startIcon={<PlayArrowIcon />} disabled>
              START SESSION
            </Button>
          </span>
        </Tooltip>
      </CardContent>
    </Card>
  );
}

export function LiveSessionsPage() {
  const { data: stations, isLoading, error } = useQuery({
    queryKey: ['stations'],
    queryFn: () => fetchStations(),
  });

  return (
    <Box>
      <Stack direction="row" alignItems="baseline" spacing={2} sx={{ mb: 3 }}>
        <Typography variant="h4">Live Sessions</Typography>
        {stations && (
          <Typography color="text.secondary">
            {stations.length} station{stations.length === 1 ? '' : 's'}
          </Typography>
        )}
      </Stack>

      {error && <Alert severity="error">{toUserMessage(error)}</Alert>}

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)', xl: 'repeat(4, 1fr)' },
        }}
      >
        {isLoading &&
          Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} variant="rounded" height={180} />)}
        {stations?.map((station) => <StationCard key={station.id} station={station} />)}
      </Box>

      {stations && stations.length === 0 && (
        <Alert severity="info">No active gaming stations. An admin can add stations in Settings.</Alert>
      )}
    </Box>
  );
}
