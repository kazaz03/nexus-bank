import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Transaction,
  TransactionFilters,
  TransferRequest,
  TransferResponse
} from '../models/transaction.model';
import { Page } from '../models/pagination.model';
import { StatementResponse } from '../models/statement.model';
import { ExchangeRate } from '../models/exchange-rate.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getByAccount(accountId: number, filters: TransactionFilters): Observable<Page<Transaction>> {
    let url = `${this.apiBase}/api/transactions/account/${accountId}`
      + `?page=${filters.page}&size=${filters.size}`;

    if (filters.type) {
      url += `&type=${encodeURIComponent(filters.type)}`;
    }
    if (filters.from) {
      url += `&from=${encodeURIComponent(filters.from + 'T00:00:00')}`;
    }
    if (filters.to) {
      url += `&to=${encodeURIComponent(filters.to + 'T23:59:59')}`;
    }

    return this.http.get<Page<Transaction>>(url);
  }

  transfer(body: TransferRequest): Observable<TransferResponse> {
    return this.http.post<TransferResponse>(
      `${this.apiBase}/api/transactions/transfer`,
      body
    );
  }

  getStatement(accountId: number, from: string, to: string): Observable<StatementResponse> {
    const url = `${this.apiBase}/api/accounts/${accountId}/statement`
      + `?from=${encodeURIComponent(from + 'T00:00:00')}`
      + `&to=${encodeURIComponent(to + 'T23:59:59')}`;
    return this.http.get<StatementResponse>(url);
  }

  getExchangeRates(): Observable<ExchangeRate[]> {
    return this.http.get<ExchangeRate[]>(`${this.apiBase}/api/exchange-rates`);
  }
}
