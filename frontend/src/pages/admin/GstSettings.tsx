import { useEffect, useState } from 'react';
import { Alert, Box, Button, Card, CardContent, FormControlLabel, MenuItem, Stack, Switch, TextField, Typography } from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toUserMessage } from '../../api/errors';
import { fetchTaxSettings, updateTaxSetting } from '../../api/settings';
import type { TaxSetting } from '../../api/types';

const LABEL = { GAMING: 'Gaming (services)', FOOD: 'Food & beverages', RENTAL: 'Game rentals' } as const;

function TaxCard({ setting }: { setting: TaxSetting }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(setting);
  useEffect(() => setForm(setting), [setting]);
  const save = useMutation({
    mutationFn: () =>
      updateTaxSetting(setting.category, {
        enabled: form.enabled,
        ratePercent: form.ratePercent,
        priceMode: form.priceMode,
        hsnSacCode: form.hsnSacCode,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['settings', 'tax'] }),
  });
  const dirty = JSON.stringify(form) !== JSON.stringify(setting);
  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Stack direction="row" alignItems="center">
            <Typography variant="h6" sx={{ flex: 1 }}>{LABEL[setting.category]}</Typography>
            <FormControlLabel
              control={<Switch checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />}
              label={form.enabled ? 'GST on' : 'GST off'}
            />
          </Stack>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 2fr 1fr' } }}>
            <TextField label="Rate %" type="number" value={form.ratePercent} onChange={(e) => setForm({ ...form, ratePercent: Number(e.target.value) })} />
            <TextField select label="Prices are" value={form.priceMode} onChange={(e) => setForm({ ...form, priceMode: e.target.value as TaxSetting['priceMode'] })}>
              <MenuItem value="EXCLUSIVE">Exclusive (GST added on top)</MenuItem>
              <MenuItem value="INCLUSIVE">Inclusive (GST inside the price)</MenuItem>
            </TextField>
            <TextField label="SAC/HSN" value={form.hsnSacCode ?? ''} onChange={(e) => setForm({ ...form, hsnSacCode: e.target.value || null })} />
          </Box>
          {save.isError && <Alert severity="error">{toUserMessage(save.error)}</Alert>}
          {save.isSuccess && !dirty && <Alert severity="success">Saved. New bills use these settings.</Alert>}
          <Box>
            <Button variant="contained" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
              Save
            </Button>
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

export function GstSettings() {
  const taxes = useQuery({ queryKey: ['settings', 'tax'], queryFn: fetchTaxSettings });
  return (
    <Stack spacing={2} sx={{ maxWidth: 820 }}>
      <Alert severity="info" variant="outlined">
        Confirm rates and SAC/HSN codes with your CA before enabling GST. Changes apply to bills completed after saving;
        existing bills keep the tax they were issued with. The GSTIN printed on invoices is set under Business details (Phase 5).
      </Alert>
      {taxes.isError && <Alert severity="error">{toUserMessage(taxes.error)}</Alert>}
      {taxes.data?.map((t) => <TaxCard key={t.category} setting={t} />)}
    </Stack>
  );
}
