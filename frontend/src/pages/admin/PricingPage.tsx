import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import { useQuery } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { fetchPricingPlans, fetchQuote } from '../../api/pricing';
import type { PricingPlan } from '../../api/types';
import { formatINR, formatMinutes } from '../../utils/format';
import { PricingPlanEditor } from './PricingPlanEditor';

function QuoteTester() {
  const [minutes, setMinutes] = useState(90);
  const [players, setPlayers] = useState(1);
  const [credit, setCredit] = useState(0);
  const quote = useQuery({
    queryKey: ['pricing', 'quote', minutes, players, credit],
    queryFn: () => fetchQuote({ minutes, players, creditMinutes: credit }),
    enabled: minutes >= 0 && players >= 1,
  });
  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Price calculator
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          Check what a customer would pay right now with the plan in effect today.
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField label="Minutes played" type="number" value={minutes} onChange={(e) => setMinutes(Number(e.target.value))} />
          <TextField label="Players" type="number" value={players} onChange={(e) => setPlayers(Number(e.target.value))} />
          <TextField label="Credit minutes" type="number" value={credit} onChange={(e) => setCredit(Number(e.target.value))} />
        </Stack>
        {quote.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {toUserMessage(quote.error)}
          </Alert>
        )}
        {quote.data && (
          <Box sx={{ mt: 2 }}>
            <Typography variant="h4" fontWeight={800}>
              {formatINR(quote.data.gamingAmount)}
            </Typography>
            <Typography color="text.secondary">
              Billed as {formatMinutes(quote.data.billableMinutes)} on “{quote.data.planName}” · {quote.data.explanation}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

function PlanCard({ plan, onEdit }: { plan: PricingPlan; onEdit: () => void }) {
  const players = [...new Set(plan.rules.map((r) => r.playerCount))].sort((a, b) => a - b);
  return (
    <Card sx={{ opacity: plan.active ? 1 : 0.6 }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
          <Typography variant="h6">{plan.name}</Typography>
          <Chip size="small" label={plan.dayType} color="primary" variant="outlined" />
          {!plan.active && <Chip size="small" label="Inactive" />}
          {plan.inUseByActiveSession && <Chip size="small" label="In use" color="error" />}
          <Box sx={{ flex: 1 }} />
          <Button startIcon={<EditIcon />} onClick={onEdit}>
            Edit
          </Button>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          From {plan.activeFrom}
          {plan.activeTo ? ` to ${plan.activeTo}` : ''} · minimum {formatMinutes(plan.minBillableMinutes)}, then{' '}
          {plan.billingBlockMinutes}-min blocks · {plan.graceMinutes} min grace
          {plan.graceEveryBoundary ? ' at every boundary' : ' (first boundary only)'} · +{formatINR(plan.extensionPrice)} per{' '}
          {plan.extensionBlockMinutes} min · extra player {formatINR(plan.additionalPlayerPrice)}{' '}
          {plan.additionalPlayerBasis === 'PER_HOUR' ? 'per hour' : 'per session'} · max {plan.maxPlayers} players
        </Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Players</TableCell>
              {[...new Set(plan.rules.map((r) => r.durationMinutes))]
                .sort((a, b) => a - b)
                .map((d) => (
                  <TableCell key={d} align="right">
                    {formatMinutes(d)}
                  </TableCell>
                ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {players.map((p) => (
              <TableRow key={p}>
                <TableCell>{p}</TableCell>
                {[...new Set(plan.rules.map((r) => r.durationMinutes))]
                  .sort((a, b) => a - b)
                  .map((d) => {
                    const rule = plan.rules.find((r) => r.playerCount === p && r.durationMinutes === d);
                    return (
                      <TableCell key={d} align="right">
                        {rule ? formatINR(rule.price) : '–'}
                      </TableCell>
                    );
                  })}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

export function PricingPage() {
  const plans = useQuery({ queryKey: ['pricing', 'plans'], queryFn: fetchPricingPlans });
  const [editing, setEditing] = useState<PricingPlan | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);

  return (
    <Box>
      <Stack direction="row" alignItems="center" sx={{ mb: 3 }}>
        <Typography variant="h4">Pricing</Typography>
        <Box sx={{ flex: 1 }} />
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => {
            setEditing(null);
            setEditorOpen(true);
          }}
        >
          New plan
        </Button>
      </Stack>
      <Stack spacing={2}>
        <QuoteTester />
        {plans.isError && <Alert severity="error">{toUserMessage(plans.error)}</Alert>}
        {plans.data?.map((plan) => (
          <PlanCard
            key={plan.id}
            plan={plan}
            onEdit={() => {
              setEditing(plan);
              setEditorOpen(true);
            }}
          />
        ))}
      </Stack>
      <PricingPlanEditor open={editorOpen} plan={editing} onClose={() => setEditorOpen(false)} />
    </Box>
  );
}
