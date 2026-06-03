import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AccountService } from '../../services/account.service';
import { TransactionService } from '../../services/transaction.service';
import { Account } from '../../models/account.model';
import { TransferResponse } from '../../models/transaction.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-transfer',
  imports: [FormsModule, TopbarComponent, NavTabsComponent],
  templateUrl: './transfer.html',
  styleUrl: './transfer.css'
})
export class TransferComponent implements OnInit {
  private auth = inject(AuthService);
  private accountService = inject(AccountService);
  private transactionService = inject(TransactionService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  myAccounts = signal<Account[]>([]);
  sourceIban = '';
  targetIban = '';
  amount: number | null = null;
  reference = '';

  submitting = signal(false);
  result = signal<TransferResponse | null>(null);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadMyAccounts();
  }

  private loadMyAccounts(): void {
    const customerId = this.auth.getCustomerId();
    if (!customerId) {
      this.error.set('Customer profile not found. Please log in again.');
      return;
    }
    this.accountService.getByCustomer(customerId).subscribe({
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
    this.transactionService.transfer({
      sourceIban: this.sourceIban,
      targetIban: this.targetIban,
      amount: this.amount,
      reference: this.reference,
      initiatedBy: customerId
    }).subscribe({
      next: res => {
        this.result.set(res);
        this.submitting.set(false);
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
