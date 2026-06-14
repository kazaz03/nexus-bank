import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Loan,
  LoanApplicationRequest,
  LoanApplicationResponse,
  LoanReviewRequest,
  RepaymentSchedule
} from '../models/loan.model';
import { Page } from '../models/pagination.model';

export interface JsonPatchOp {
  op: 'replace' | 'add' | 'remove';
  path: string;
  value?: unknown;
}

@Injectable({ providedIn: 'root' })
export class LoanService {
  private http = inject(HttpClient);
  private readonly apiBase = '';

  getAll(size = 50): Observable<Page<Loan>> {
    return this.http.get<Page<Loan>>(`${this.apiBase}/api/loans?size=${size}`);
  }

  review(loanId: number, body: LoanReviewRequest): Observable<Loan> {
    return this.http.post<Loan>(`${this.apiBase}/api/loans/${loanId}/review`, body);
  }

  apply(body: LoanApplicationRequest): Observable<LoanApplicationResponse> {
    return this.http.post<LoanApplicationResponse>(`${this.apiBase}/api/loans`, body);
  }

  getByCustomer(customerId: number): Observable<Loan[]> {
    return this.http.get<Loan[]>(`${this.apiBase}/api/loans/customer/${customerId}`);
  }

  getSchedule(loanId: number): Observable<RepaymentSchedule[]> {
    return this.http.get<RepaymentSchedule[]>(`${this.apiBase}/api/loans/${loanId}/schedule`);
  }

  patch(loanId: number, ops: JsonPatchOp[]): Observable<Loan> {
    const headers = new HttpHeaders({ 'Content-Type': 'application/json-patch+json' });
    return this.http.patch<Loan>(
      `${this.apiBase}/api/loans/${loanId}`,
      ops,
      { headers }
    );
  }
}
