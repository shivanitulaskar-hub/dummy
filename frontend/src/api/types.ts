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
  id: number | null;
  type: string;
  foodItemId: number | null;
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
  discountName: string | null;
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

export interface FoodCategory {
  id: number;
  name: string;
  displayOrder: number;
  active: boolean;
}

export interface FoodItem {
  id: number;
  name: string;
  categoryId: number;
  categoryName: string;
  sellingPrice: number;
  costPrice: number | null;
  stockQuantity: number;
  reorderLevel: number;
  lowStock: boolean;
  trackInventory: boolean;
  gamingCreditMinutes: number;
  active: boolean;
}

export interface FoodItemRequest {
  name: string;
  categoryId: number;
  sellingPrice: number;
  costPrice: number;
  reorderLevel: number;
  trackInventory: boolean;
  gamingCreditMinutes: number;
  active: boolean;
}

export type InventoryTxnType = 'OPENING' | 'PURCHASE' | 'SALE' | 'SALE_REVERSAL' | 'WASTAGE' | 'ADJUSTMENT';

export interface InventoryTxn {
  id: number;
  foodItemId: number;
  foodItemName: string;
  type: InventoryTxnType;
  quantity: number;
  unitCost: number | null;
  referenceType: string | null;
  referenceId: number | null;
  notes: string | null;
  businessDate: string;
  createdAt: string;
}

export interface StockRow {
  foodItemId: number;
  name: string;
  category: string;
  trackInventory: boolean;
  opening: number;
  purchases: number;
  sales: number;
  wastage: number;
  adjustments: number;
  closing: number;
  currentStock: number;
  reorderLevel: number;
  lowStock: boolean;
}

export type DiscountType = 'PERCENTAGE' | 'FLAT';

export interface DiscountRequest {
  code: string;
  name: string;
  discountType: DiscountType;
  value: number;
  maxAmount: number | null;
  validFrom: string | null;
  validTo: string | null;
  active: boolean;
}

export interface Discount extends DiscountRequest {
  id: number;
  validToday: boolean;
}

export type TaxCategory = 'GAMING' | 'FOOD' | 'RENTAL';
export type PriceMode = 'INCLUSIVE' | 'EXCLUSIVE';

export interface TaxSetting {
  category: TaxCategory;
  enabled: boolean;
  ratePercent: number;
  priceMode: PriceMode;
  hsnSacCode: string | null;
}

export type BillStatus = 'OPEN' | 'PAID' | 'CANCELLED';
export type BillType = 'GAMING' | 'WALK_IN' | 'RENTAL';

export interface BillSummary {
  id: number;
  billNumber: string | null;
  billType: BillType;
  status: BillStatus;
  customerName: string | null;
  customerMobile: string | null;
  totalAmount: number;
  paymentMode: PaymentMode | null;
  businessDate: string | null;
  paidAt: string | null;
}

export interface BillDetail {
  id: number;
  billNumber: string | null;
  billType: BillType;
  status: BillStatus;
  customerName: string | null;
  customerMobile: string | null;
  businessDate: string | null;
  createdAt: string;
  paidAt: string | null;
  gaming: {
    sessionId: number;
    stationCode: string;
    playerCount: number;
    startedAt: string;
    endedAt: string | null;
    actualMinutes: number | null;
    creditMinutes: number;
    billableMinutes: number | null;
    pricingPlan: string;
  } | null;
  lines: { id: number; type: string; description: string; quantity: number; unitPrice: number; amount: number }[];
  gamingAmount: number;
  foodAmount: number;
  rentalAmount: number;
  subtotal: number;
  discountName: string | null;
  discountAmount: number;
  taxableAmount: number;
  taxAmount: number;
  taxLines: {
    category: TaxCategory;
    hsnSacCode: string | null;
    taxableValue: number;
    ratePercent: number;
    priceMode: PriceMode;
    taxAmount: number;
    cgst: number;
    sgst: number;
  }[];
  roundOff: number;
  totalAmount: number;
  payments: {
    id: number;
    type: 'PAYMENT' | 'REFUND' | 'DEPOSIT' | 'DEPOSIT_REFUND';
    mode: PaymentMode;
    amount: number;
    referenceNo: string | null;
    reason: string | null;
    receivedAt: string;
  }[];
  credits: { minutes: number; reason: string }[];
  cancelledAt: string | null;
  cancelReason: string | null;
}

export interface Invoice {
  business: { name: string; tagline: string; address: string; phone: string; gstin: string };
  bill: BillDetail;
  footer: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
