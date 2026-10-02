import { Chip } from '@mui/material';
import type { RentalStatus } from '../../api/types';

const LOOK: Record<RentalStatus, { label: string; color: 'info' | 'error' | 'success' | 'default' }> = {
  OUT: { label: 'Out', color: 'info' },
  OVERDUE: { label: 'Overdue', color: 'error' },
  RETURNED: { label: 'Returned', color: 'success' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

export function RentalStatusChip({ status }: { status: RentalStatus }) {
  const look = LOOK[status];
  return <Chip size="small" variant={status === 'OVERDUE' ? 'filled' : 'outlined'} color={look.color} label={look.label} />;
}
