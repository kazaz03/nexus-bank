import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Account, CreateAccountRequest } from '../models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getByCustomer(customerId: number): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.apiBase}/api/accounts/customer/${customerId}`);
  }

  getById(accountId: number): Observable<Account> {
    return this.http.get<Account>(`${this.apiBase}/api/accounts/${accountId}`);
  }

  openAccount(body: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${this.apiBase}/api/accounts`, body);
  }

  closeAccount(accountId: number, closedBy?: number): Observable<Account> {
    const params = closedBy != null ? `?closedBy=${closedBy}` : '';
    return this.http.patch<Account>(`${this.apiBase}/api/accounts/${accountId}/close${params}`, {});
  }
}
