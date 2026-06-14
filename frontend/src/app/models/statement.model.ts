import { Transaction } from './transaction.model';

export interface StatementResponse {
  accountId: number;
  fromDate: string;
  toDate: string;
  openingBalance: number;
  closingBalance: number;
  totalDeposited: number;
  totalWithdrawn: number;
  transactions: Transaction[];
}
