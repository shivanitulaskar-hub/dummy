import { Box, Button, Typography } from '@mui/material';
import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <Box sx={{ textAlign: 'center', py: 8 }}>
      <Typography variant="h4" gutterBottom>
        Page not found
      </Typography>
      <Button component={Link} to="/live" variant="contained" size="large">
        Go to Live Sessions
      </Button>
    </Box>
  );
}
