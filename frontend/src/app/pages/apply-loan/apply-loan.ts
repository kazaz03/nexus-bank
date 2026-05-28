import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

interface Account {
  id: number;
  iban: string;
  accountType: string;
  currency: string;
  balance: number;
}

interface LoanApplicationResponse {
  id: number;
  customerId: number;
  accountId: number;
  amountRequested: number;
  currency: string;
  termMonths: number;
  purpose: string;
  status: string;
}

@Component({
  selector: 'app-apply-loan',
  imports: [FormsModule, RouterLink],
  templateUrl: './apply-loan.html',
  styleUrl: './apply-loan.css'
})
export class ApplyLoanComponent implements OnInit {
  private http   = inject(HttpClient);
  private auth   = inject(AuthService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role  = this.auth.getRole();

  myAccounts        = signal<Account[]>([]);
  selectedAccountId: number | null = null;
  amountRequested:  number | null = null;
  currency          = 'BAM';
  termMonths:       number | null = null;
  purpose           = '';

  submitting = signal(false);
  result     = signal<LoanApplicationResponse | null>(null);
  error      = signal<string | null>(null);

  ngOnInit(): void {
    this.loadMyAccounts();
  }

  private loadMyAccounts(): void {
    const customerId = this.auth.getCustomerId();
    if (!customerId) {
      this.error.set('Customer profile not found. Please log in again.');
      return;
    }
    this.http
      .get<Account[]>(`http://localhost:8080/api/accounts/customer/${customerId}`)
      .subscribe({
        next: data => {
          this.myAccounts.set(data);
          if (data.length > 0) {
            this.selectedAccountId = data[0].id;
          }
        },
        error: () => this.error.set('Failed to load your accounts.')
      });
  }

  submit(): void {
    this.error.set(null);
    this.result.set(null);

    const customerId = this.auth.getCustomerId();
    if (!customerId) {
      this.error.set('Customer profile not found. Please log in again.');
      return;
    }
    if (!this.selectedAccountId) {
      this.error.set('Please select a disbursement account.');
      return;
    }
    if (!this.amountRequested || this.amountRequested < 100) {
      this.error.set('Loan amount must be at least 100.');
      return;
    }
    if (!this.termMonths || this.termMonths < 1) {
      this.error.set('Term must be at least 1 month.');
      return;
    }
    if (!this.purpose.trim()) {
      this.error.set('Purpose is required.');
      return;
    }

    this.submitting.set(true);
    this.http
      .post<LoanApplicationResponse>('http://localhost:8080/api/loans', {
        customerId,
        accountId:       this.selectedAccountId,
        amountRequested: this.amountRequested,
        currency:        this.currency,
        termMonths:      this.termMonths,
        purpose:         this.purpose.trim()
      })
      .subscribe({
        next: res => {
          this.result.set(res);
          this.submitting.set(false);
        },
        error: err => {
          this.submitting.set(false);
          const body = err?.error;
          this.error.set(
            body?.message || body?.error
              || `Application failed (HTTP ${err?.status ?? '?'})`
          );
        }
      });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
