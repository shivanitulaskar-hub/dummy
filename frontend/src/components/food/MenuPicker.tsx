import { useMemo, useState } from 'react';
import { Box, ButtonBase, Chip, Stack, Typography } from '@mui/material';
import type { FoodItem } from '../../api/types';
import { formatINR } from '../../utils/format';

/** Big tap targets grouped by category: one tap adds one unit. */
export function MenuPicker({ items, onPick, disabled }: { items: FoodItem[]; onPick: (item: FoodItem) => void; disabled?: boolean }) {
  const categories = useMemo(() => [...new Set(items.map((i) => i.categoryName))], [items]);
  const [category, setCategory] = useState<string | null>(null);
  const visible = category ? items.filter((i) => i.categoryName === category) : items;

  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ mb: 1.5, overflowX: 'auto', pb: 0.5 }}>
        <Chip label="All" color={category === null ? 'primary' : 'default'} onClick={() => setCategory(null)} />
        {categories.map((c) => (
          <Chip key={c} label={c} color={category === c ? 'primary' : 'default'} onClick={() => setCategory(c)} />
        ))}
      </Stack>
      <Box sx={{ display: 'grid', gap: 1, gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)' } }}>
        {visible.map((item) => (
          <ButtonBase
            key={item.id}
            disabled={disabled}
            onClick={() => onPick(item)}
            sx={{
              display: 'block',
              textAlign: 'left',
              p: 1.5,
              borderRadius: 2,
              border: 1,
              borderColor: 'divider',
              bgcolor: 'background.default',
              minHeight: 76,
              '&:hover': { borderColor: 'primary.main', bgcolor: 'rgba(59,130,246,0.08)' },
            }}
          >
            <Typography fontWeight={700} sx={{ lineHeight: 1.2 }}>
              {item.name}
            </Typography>
            <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mt: 0.75, flexWrap: 'wrap', rowGap: 0.5 }}>
              <Typography color="primary.light" fontWeight={800}>
                {formatINR(item.sellingPrice)}
              </Typography>
              {item.gamingCreditMinutes > 0 && <Chip size="small" color="success" label={`+${item.gamingCreditMinutes}m`} />}
              {item.trackInventory && item.lowStock && (
                <Chip
                  size="small"
                  color={item.stockQuantity > 0 ? 'warning' : 'error'}
                  variant="outlined"
                  label={item.stockQuantity > 0 ? `${item.stockQuantity} left` : 'Out of stock'}
                />
              )}
            </Stack>
          </ButtonBase>
        ))}
      </Box>
    </Box>
  );
}
