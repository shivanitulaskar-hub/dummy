import type { ReactElement } from 'react';
import DashboardIcon from '@mui/icons-material/SpaceDashboard';
import SportsEsportsIcon from '@mui/icons-material/SportsEsports';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PeopleIcon from '@mui/icons-material/People';
import FastfoodIcon from '@mui/icons-material/Fastfood';
import AlbumIcon from '@mui/icons-material/Album';
import AssessmentIcon from '@mui/icons-material/Assessment';
import InventoryIcon from '@mui/icons-material/Inventory2';
import SellIcon from '@mui/icons-material/Sell';
import SettingsIcon from '@mui/icons-material/Settings';
import EventAvailableIcon from '@mui/icons-material/EventAvailable';
import type { Role } from '../api/types';

export interface NavItem {
  path: string;
  label: string;
  icon: ReactElement;
  roles?: Role[];
}

/** Main navigation, in the order requested by the specification. */
export const NAV_ITEMS: NavItem[] = [
  { path: '/dashboard', label: 'Dashboard', icon: <DashboardIcon /> },
  { path: '/live', label: 'Live Sessions', icon: <SportsEsportsIcon /> },
  { path: '/bills', label: 'Bills', icon: <ReceiptLongIcon /> },
  { path: '/customers', label: 'Customers', icon: <PeopleIcon /> },
  { path: '/food', label: 'Food', icon: <FastfoodIcon /> },
  { path: '/rentals', label: 'Rentals', icon: <AlbumIcon /> },
  { path: '/reports', label: 'Reports', icon: <AssessmentIcon /> },
  { path: '/closing', label: 'Daily Closing', icon: <EventAvailableIcon /> },
  { path: '/inventory', label: 'Inventory', icon: <InventoryIcon /> },
  { path: '/pricing', label: 'Pricing', icon: <SellIcon />, roles: ['ADMIN'] },
  { path: '/settings', label: 'Settings', icon: <SettingsIcon />, roles: ['ADMIN'] },
];
