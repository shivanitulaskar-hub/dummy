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
