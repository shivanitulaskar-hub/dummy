import { useState } from 'react';
import { Alert, Box, Tab, Tabs, Typography } from '@mui/material';
import { DiscountsAdmin } from './DiscountsAdmin';
import { GstSettings } from './GstSettings';
import { StationsAdmin } from './StationsAdmin';

const TABS = ['Gaming stations', 'GST', 'Discounts', 'Users', 'Business details'];

export function SettingsPage() {
  const [tab, setTab] = useState(0);
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Settings
      </Typography>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}>
        {TABS.map((t) => (
          <Tab key={t} label={t} />
        ))}
      </Tabs>
      {tab === 0 && <StationsAdmin />}
      {tab === 1 && <GstSettings />}
      {tab === 2 && <DiscountsAdmin />}
      {tab >= 3 && (
        <Alert severity="info" variant="outlined" sx={{ maxWidth: 720 }}>
          <strong>Not built yet.</strong> {TABS[tab]} settings are scheduled for Phase 5.
        </Alert>
      )}
    </Box>
  );
}
