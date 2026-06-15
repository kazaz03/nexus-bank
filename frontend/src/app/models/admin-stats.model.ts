export interface DailyVolume {
  date: string;
  count: number;
  totalAmount: number;
}

export interface AdminStats {
  // Users
  totalUsers: number;
  totalCustomers: number;
  totalTellers: number;
  totalLoanOfficers: number;
  totalAdmins: number;
  activeUsers: number;

  // Accounts
  totalAccounts: number;
  activeAccounts: number;
  checkingAccounts: number;
  savingsAccounts: number;
  foreignAccounts: number;

  // Transactions
  transactionsToday: number;
  totalTransactions: number;
  volumeLast7Days: DailyVolume[];

  // Loans
  totalLoans: number;
  pendingLoans: number;
  approvedLoans: number;
  disbursedLoans: number;
  rejectedLoans: number;
}
