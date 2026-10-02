import { useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  IconButton,
  Menu,
  MenuItem,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PeopleIcon from '@mui/icons-material/People';
import TvIcon from '@mui/icons-material/Tv';
import { useQuery } from '@tanstack/react-query';
import { toUserMessage } from '../api/errors';
import { fetchCurrentPlan } from '../api/pricing';
import { fetchActiveSessions } from '../api/sessions';
import { fetchStations } from '../api/stations';
import type { GamingSession, Station } from '../api/types';
import { EndSessionDialog } from '../components/sessions/EndSessionDialog';
import { CancelSessionDialog, ChangePlayersDialog } from '../components/sessions/SessionActionsDialogs';
import { StartSessionDialog } from '../components/sessions/StartSessionDialog';
import { formatElapsed, formatINR, formatTime } from '../utils/format';
import { useNow } from '../utils/useNow';

const REFRESH_MS = 15_000;

function AvailableCard({ station, onStart }: { station: Station; onStart: () => void }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%' }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '0.04em' }}>
            {station.code}
          </Typography>
          <Chip size="small" label="AVAILABLE" color="success" variant="outlined" sx={{ fontWeight: 700 }} />
        </Stack>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ color: 'text.secondary' }}>
          <TvIcon fontSize="small" />
          <Typography variant="body2">
            {[station.consoleLabel, station.tvLabel].filter(Boolean).join(' · ') || station.name}
          </Typography>
        </Stack>
        <Box sx={{ flex: 1, minHeight: 24 }} />
        <Button variant="contained" size="large" fullWidth startIcon={<PlayArrowIcon />} onClick={onStart}>
          START SESSION
        </Button>
      </CardContent>
    </Card>
  );
}

function ActiveCard({
  session,
  now,
  onEnd,
  onChangePlayers,
  onCancel,
}: {
  session: GamingSession;
  now: number;
  onEnd: () => void;
  onChangePlayers: () => void;
  onCancel: () => void;
}) {
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const elapsed = (now - new Date(session.startedAt).getTime()) / 1000;
  return (
    <Card
      sx={{
        height: '100%',
        borderColor: 'error.main',
        boxShadow: '0 0 0 1px rgba(239,68,68,0.5), 0 0 24px rgba(239,68,68,0.15)',
      }}
    >
      <CardContent sx={{ display: 'flex', flexDirection: 'column', gap: 1, height: '100%' }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '0.04em' }}>
            {session.stationCode}
          </Typography>
          <Stack direction="row" alignItems="center" spacing={0.5}>
            <Chip size="small" label="● ACTIVE" color="error" sx={{ fontWeight: 800 }} />
            <IconButton size="small" aria-label="More actions" onClick={(e) => setMenuAnchor(e.currentTarget)}>
              <MoreVertIcon />
            </IconButton>
          </Stack>
        </Stack>
        <Typography variant="h6" noWrap>
          {session.customerName ?? 'Walk-in'}
        </Typography>
        <Stack direction="row" spacing={2} sx={{ color: 'text.secondary' }}>
          <Stack direction="row" spacing={0.5} alignItems="center">
            <PeopleIcon fontSize="small" />
            <Typography variant="body2">
              {session.playerCount} player{session.playerCount === 1 ? '' : 's'}
            </Typography>
          </Stack>
          <Typography variant="body2">Started {formatTime(session.startedAt)}</Typography>
        </Stack>
        <Typography
          sx={{ fontSize: '2.25rem', fontWeight: 800, fontVariantNumeric: 'tabular-nums', lineHeight: 1.1, mt: 0.5 }}
        >
          {formatElapsed(elapsed)}
        </Typography>
        <Typography color="text.secondary">
          Current estimate:{' '}
          <Box component="span" sx={{ color: 'text.primary', fontWeight: 800 }}>
            {formatINR(session.charges?.totalAmount)}
          </Box>
        </Typography>
        <Box sx={{ flex: 1 }} />
        <Button variant="contained" color="error" size="large" fullWidth startIcon={<StopIcon />} onClick={onEnd}>
          END SESSION
        </Button>
        <Menu anchorEl={menuAnchor} open={menuAnchor !== null} onClose={() => setMenuAnchor(null)}>
          <MenuItem
            onClick={() => {
              setMenuAnchor(null);
              onChangePlayers();
            }}
          >
            Change players
          </MenuItem>
          <MenuItem
            sx={{ color: 'error.main' }}
            onClick={() => {
              setMenuAnchor(null);
              onCancel();
            }}
          >
            Cancel session
          </MenuItem>
        </Menu>
      </CardContent>
    </Card>
  );
}

export function LiveSessionsPage() {
  const now = useNow();
  const stations = useQuery({ queryKey: ['stations'], queryFn: () => fetchStations() });
  const sessions = useQuery({
    queryKey: ['sessions', 'active'],
    queryFn: fetchActiveSessions,
    refetchInterval: REFRESH_MS,
  });
  const plan = useQuery({ queryKey: ['pricing', 'current'], queryFn: fetchCurrentPlan });

  const [startFor, setStartFor] = useState<Station | null>(null);
  const [endId, setEndId] = useState<number | null>(null);
  const [changePlayersFor, setChangePlayersFor] = useState<GamingSession | null>(null);
  const [cancelFor, setCancelFor] = useState<GamingSession | null>(null);

  const byStation = useMemo(() => {
    const map = new Map<number, GamingSession>();
    sessions.data?.forEach((s) => map.set(s.stationId, s));
    return map;
  }, [sessions.data]);

  const activeCount = sessions.data?.length ?? 0;
  const total = stations.data?.length ?? 0;
  const error = stations.error ?? sessions.error;
  const loading = stations.isLoading || sessions.isLoading;

  return (
    <Box>
      <Stack direction="row" alignItems="baseline" spacing={2} sx={{ mb: 3, flexWrap: 'wrap' }}>
        <Typography variant="h4">Live Sessions</Typography>
        {stations.data && (
          <Typography color="text.secondary">
            {activeCount} active · {total - activeCount} available
          </Typography>
        )}
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {toUserMessage(error)}
        </Alert>
      )}

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)', xl: 'repeat(4, 1fr)' },
        }}
      >
        {loading && Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} variant="rounded" height={260} />)}
        {!loading &&
          stations.data?.map((station) => {
            const session = byStation.get(station.id);
            return session ? (
              <ActiveCard
                key={station.id}
                session={session}
                now={now}
                onEnd={() => setEndId(session.id)}
                onChangePlayers={() => setChangePlayersFor(session)}
                onCancel={() => setCancelFor(session)}
              />
            ) : (
              <AvailableCard key={station.id} station={station} onStart={() => setStartFor(station)} />
            );
          })}
      </Box>

      {stations.data && stations.data.length === 0 && (
        <Alert severity="info">No active gaming stations. An admin can add stations in Settings.</Alert>
      )}

      <StartSessionDialog station={startFor} onClose={() => setStartFor(null)} />
      <EndSessionDialog sessionId={endId} onClose={() => setEndId(null)} />
      <ChangePlayersDialog
        session={changePlayersFor}
        maxPlayers={plan.data?.maxPlayers ?? 4}
        onClose={() => setChangePlayersFor(null)}
      />
      <CancelSessionDialog session={cancelFor} onClose={() => setCancelFor(null)} />
    </Box>
  );
}
