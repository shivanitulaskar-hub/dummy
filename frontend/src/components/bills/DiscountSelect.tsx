import { MenuItem, TextField } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { fetchUsableDiscounts } from '../../api/settings';
import { formatINR } from '../../utils/format';

/** Only discounts configured by an admin and valid today can be chosen. */
export function DiscountSelect({ value, onChange }: { value: number | null; onChange: (id: number | null) => void }) {
  const discounts = useQuery({ queryKey: ['discounts', 'usable'], queryFn: fetchUsableDiscounts });
  if (!discounts.data || discounts.data.length === 0) return null;
  return (
    <TextField
      select
      size="small"
      label="Discount"
      value={value ?? ''}
      onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
    >
      <MenuItem value="">No discount</MenuItem>
      {discounts.data.map((d) => (
        <MenuItem key={d.id} value={d.id}>
          {d.name} ({d.discountType === 'PERCENTAGE' ? `${d.value}%` : formatINR(d.value)}
          {d.maxAmount ? `, max ${formatINR(d.maxAmount)}` : ''})
        </MenuItem>
      ))}
    </TextField>
  );
}
