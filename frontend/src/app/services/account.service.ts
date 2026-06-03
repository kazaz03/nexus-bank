import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Account } from '../models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getByCustomer(customerId: number): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.apiBase}/api/accounts/customer/${customerId}`);
  }
}
