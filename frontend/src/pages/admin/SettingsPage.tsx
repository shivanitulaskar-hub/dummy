import { useState } from 'react';
import { Alert, Box, Tab, Tabs, Typography } from '@mui/material';
import { StationsAdmin } from './StationsAdmin';

const LATER_TABS = ['GST', 'Discounts', 'Users', 'Business details'];

export function SettingsPage() {
  const [tab, setTab] = useState(0);
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Settings
      </Typography>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}>
        <Tab label="Gaming stations" />
        {LATER_TABS.map((t) => (
          <Tab key={t} label={t} />
        ))}
      </Tabs>
      {tab === 0 ? (
        <StationsAdmin />
      ) : (
        <Alert severity="info" variant="outlined" sx={{ maxWidth: 720 }}>
          <strong>Not built yet.</strong> {LATER_TABS[tab - 1]} settings are scheduled for Phase {tab === 2 ? 3 : 5}.
        </Alert>
      )}
    </Box>
  );
}
