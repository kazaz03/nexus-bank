import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Loan,
  LoanApplicationRequest,
  LoanApplicationResponse,
  LoanReviewRequest
} from '../models/loan.model';
import { Page } from '../models/pagination.model';

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
}
