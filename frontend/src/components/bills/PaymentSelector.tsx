import { TextField, ToggleButton, ToggleButtonGroup, Typography, Box } from '@mui/material';
import type { PaymentMode } from '../../api/types';

const MODES: PaymentMode[] = ['CASH', 'UPI', 'CARD', 'OTHER'];

export function PaymentSelector({
  mode,
  onModeChange,
  referenceNo,
  onReferenceChange,
}: {
  mode: PaymentMode;
  onModeChange: (m: PaymentMode) => void;
  referenceNo: string;
  onReferenceChange: (v: string) => void;
}) {
  return (
    <Box>
      <Typography variant="subtitle2" color="text.secondary" gutterBottom>
        Payment
      </Typography>
      <ToggleButtonGroup exclusive fullWidth color="primary" value={mode} onChange={(_, v: PaymentMode | null) => v && onModeChange(v)}>
        {MODES.map((m) => (
          <ToggleButton key={m} value={m} sx={{ py: 1.25, fontWeight: 800 }}>
            {m}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>
      {(mode === 'UPI' || mode === 'CARD') && (
        <TextField
          label="Reference no. (optional)"
          value={referenceNo}
          onChange={(e) => onReferenceChange(e.target.value)}
          size="small"
          sx={{ mt: 1.5 }}
        />
      )}
    </Box>
  );
}
