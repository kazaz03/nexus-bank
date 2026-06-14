export interface Loan {
  id: number;
  customerId: number;
  accountId: number;
  amountRequested: number;
  amountApproved: number | null;
  currency: string;
  interestRate: number | null;
  termMonths: number;
  purpose: string;
  status: string;
  rejectionReason: string | null;
  reviewedAt: string | null;
  createdAt: string | null;
}

export interface RepaymentSchedule {
  id: number;
  loanApplicationId: number;
  installmentNumber: number;
  dueDate: string;
  amountDue: number;
  amountPaid: number;
  status: string;
  paidAt: string | null;
}

export interface LoanApplicationResponse {
  id: number;
  customerId: number;
  accountId: number;
  amountRequested: number;
  currency: string;
  termMonths: number;
  purpose: string;
  status: string;
}

export interface LoanReviewRequest {
  approved: boolean;
  amountApproved?: number;
  interestRate?: number;
  rejectionReason?: string;
  reviewedBy: number;
}

export interface LoanApplicationRequest {
  customerId: number;
  accountId: number;
  amountRequested: number;
  currency: string;
  termMonths: number;
  purpose: string;
}
