export interface Account {
  id: number;
  iban: string;
  accountType: string;
  currency: string;
  balance: number;
  overdraftLimit: number | null;
  status: string;
}
