export interface Account {
  id: number;
  customerId: number;
  iban: string;
  accountType: string;
  currency: string;
  balance: number;
  overdraftLimit: number | null;
  interestRate: number | null;
  status: string;
  createdAt: string | null;
}

export interface CreateAccountRequest {
  customerId: number;
  accountType: string;
  currency: string;
  overdraftLimit?: number;
  interestRate?: number;
  createdBy?: number;
}
