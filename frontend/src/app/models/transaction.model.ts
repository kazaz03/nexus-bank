export interface Transaction {
  id: number;
  accountId: number;
  type: string;
  amount: number;
  currency: string;
  balanceAfter: number;
  counterpartyIban: string | null;
  reference: string | null;
  createdAt: string;
  status: string;
}

export interface TransferResponse {
  reference: string;
  sourceIban: string;
  targetIban: string;
  sourceAmount: number;
  sourceCurrency: string;
  targetAmount: number;
  targetCurrency: string;
  exchangeRate: number | null;
  sourceBalanceAfter: number;
  targetBalanceAfter: number;
  status: string;
}

export interface TransferRequest {
  sourceIban: string;
  targetIban: string;
  amount: number;
  reference: string;
  initiatedBy: number;
}

export interface TransactionFilters {
  page: number;
  size: number;
  type?: string;
  from?: string;
  to?: string;
}
