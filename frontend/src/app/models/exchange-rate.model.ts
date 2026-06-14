export interface ExchangeRate {
  id: number;
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  validFrom: string;
  validTo: string | null;
}
