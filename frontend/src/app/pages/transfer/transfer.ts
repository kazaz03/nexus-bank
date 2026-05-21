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

interface TransferResponse {
  reference: string;
  sourceIban: string;
  targetIban: string;
  sourceAmount: number;
  sourceCurrency: string;
  targetAmount: number;
  targetCurrency: string;
  exchangeRate: number | null;
  sourceBalanceAfter: number;
  targetBalanceAfter: number;
  status: string;
}

@Component({
  selector: 'app-transfer',
  imports: [FormsModule, RouterLink],
  templateUrl: './transfer.html',
  styleUrl: './transfer.css'
})
export class TransferComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role  = this.auth.getRole();

  myAccounts = signal<Account[]>([]);
  sourceIban = '';
  targetIban = '';
  amount: number | null = null;
  reference = '';

  // UI state.
  submitting = signal(false);
  result     = signal<TransferResponse | null>(null);
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
            this.sourceIban = data[0].iban;
          }
        },
        error: () => this.error.set('Failed to load your accounts')
      });
  }

  submit(): void {
    this.error.set(null);
    this.result.set(null);

    if (!this.sourceIban || !this.targetIban || !this.amount || this.amount <= 0) {
      this.error.set('Source, target IBAN and a positive amount are required.');
      return;
    }
    if (this.sourceIban === this.targetIban) {
      this.error.set('Source and target IBAN must differ.');
      return;
    }

    const customerId = this.auth.getCustomerId();
    if (!customerId) {
      this.error.set('Customer profile not found. Please log in again.');
      return;
    }

    this.submitting.set(true);
    this.http
      .post<TransferResponse>('http://localhost:8080/api/transactions/transfer', {
        sourceIban: this.sourceIban,
        targetIban: this.targetIban,
        amount: this.amount,
        reference: this.reference,
        initiatedBy: customerId
      })
      .subscribe({
        next: res => {
          this.result.set(res);
          this.submitting.set(false);
          // Refresh the source account dropdown so the user sees updated balance.
          this.loadMyAccounts();
        },
        error: err => {
          this.submitting.set(false);
          const body = err?.error;
          this.error.set(body?.error || `Transfer failed (HTTP ${err?.status ?? '?'})`);
        }
      });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
