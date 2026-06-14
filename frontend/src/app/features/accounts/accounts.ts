import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AccountService } from '../../services/account.service';
import { Account } from '../../models/account.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-accounts',
  imports: [TopbarComponent, NavTabsComponent],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css'
})
export class AccountsComponent implements OnInit {
  private auth = inject(AuthService);
  private accountService = inject(AccountService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  accounts = signal<Account[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const customerId = this.auth.getCustomerId();
    if (!customerId) {
      this.error.set('Customer profile not found. Please log in again.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    this.accountService.getByCustomer(customerId).subscribe({
      next: data => {
        this.accounts.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load accounts');
      }
    });
  }

  formatAmount(value: number, currency: string): string {
    return `${value.toFixed(2)} ${currency}`;
  }

  viewTransactions(accountId: number): void {
    this.router.navigate(['/transactions', accountId]);
  }

  viewStatement(accountId: number): void {
    this.router.navigate(['/statement', accountId]);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
