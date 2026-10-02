export type Role = 'ADMIN' | 'OPERATOR';

export interface UserInfo {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  mustChangePassword: boolean;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: UserInfo;
}

export interface Station {
  id: number;
  code: string;
  name: string;
  consoleLabel: string | null;
  tvLabel: string | null;
  active: boolean;
  displayOrder: number;
  notes: string | null;
}

export interface FieldViolation {
  field: string;
  message: string;
}

/** Error body returned by every failing backend call. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code?: string;
  message: string;
  path: string;
  validationErrors?: FieldViolation[];
}

export type DayType = 'WEEKDAY' | 'WEEKEND' | 'SPECIAL';
export type AdditionalPlayerBasis = 'PER_SESSION' | 'PER_HOUR';
export type SessionStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type PaymentMode = 'CASH' | 'UPI' | 'CARD' | 'OTHER';

export interface StationRequest {
  code: string;
  name: string;
  consoleLabel?: string | null;
  tvLabel?: string | null;
  active?: boolean;
  displayOrder?: number;
  notes?: string | null;
}

export interface PricingRule {
  playerCount: number;
  durationMinutes: number;
  price: number;
}

export interface PricingPlanRequest {
  name: string;
  dayType: DayType;
  activeFrom: string;
  activeTo: string | null;
  active: boolean;
  priority: number;
  minBillableMinutes: number;
  billingBlockMinutes: number;
  graceMinutes: number;
  graceEveryBoundary: boolean;
  extensionBlockMinutes: number;
  extensionPrice: number;
  additionalPlayerPrice: number;
  additionalPlayerBasis: AdditionalPlayerBasis;
  maxPlayers: number;
  notes: string | null;
  rules: PricingRule[];
}

export interface PricingPlan extends PricingPlanRequest {
  id: number;
  inUseByActiveSession: boolean;
  updatedAt: string;
}

export interface PriceQuote {
  planId: number;
  planName: string;
  playedMinutes: number;
  creditMinutes: number;
  billableMinutes: number;
  playerCount: number;
  baseAmount: number;
  additionalPlayerAmount: number;
  gamingAmount: number;
  explanation: string;
}

export interface ChargeLine {
  type: string;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
}

export interface ChargeBreakdown {
  calculatedAt: string;
  actualMinutes: number;
  creditMinutes: number;
  billableMinutes: number;
  gamingAmount: number;
  gamingExplanation: string | null;
  foodAmount: number;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  roundOff: number;
  totalAmount: number;
  lines: ChargeLine[];
}

export interface GamingSession {
  id: number;
  stationId: number;
  stationCode: string;
  customerName: string | null;
  customerMobile: string | null;
  playerCount: number;
  pricingPlanId: number;
  pricingPlanName: string;
  startedAt: string;
  endedAt: string | null;
  status: SessionStatus;
  billId: number;
  billNumber: string | null;
  paymentMode: PaymentMode | null;
  cancelReason: string | null;
  charges: ChargeBreakdown | null;
}

export interface StartSessionRequest {
  stationId: number;
  playerCount: number;
  pricingPlanId?: number;
  customerName?: string;
  customerMobile?: string;
  notes?: string;
}
