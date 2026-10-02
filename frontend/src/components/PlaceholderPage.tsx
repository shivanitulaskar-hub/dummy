import { Alert, Box, Typography } from '@mui/material';

/** Honest placeholder for modules scheduled in a later phase. Nothing here pretends to work. */
export function PlaceholderPage({ title, phase, description }: { title: string; phase: number; description: string }) {
  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        {title}
      </Typography>
      <Alert severity="info" variant="outlined" sx={{ maxWidth: 720 }}>
        <strong>Not built yet. Scheduled for Phase {phase}.</strong> {description}
      </Alert>
    </Box>
  );
}
