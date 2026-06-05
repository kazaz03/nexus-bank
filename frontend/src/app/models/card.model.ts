export interface DebitCard {
  id: number;
  accountId: number;
  maskedCardNumber: string;
  expiryDate: string;
  status: string;
  issuedAt: string;
  issuedBy: number | null;
}
