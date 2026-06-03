export interface Loan {
  id: number;
  customerId: number;
  accountId: number;
  amountRequested: number;
  amountApproved: number | null;
  interestRate: number | null;
  termMonths: number;
  purpose: string;
  status: string;
  rejectionReason: string | null;
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
