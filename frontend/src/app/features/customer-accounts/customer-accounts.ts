import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AccountService } from '../../services/account.service';
import { CustomerService } from '../../services/customer.service';
import { Account } from '../../models/account.model';
import { Customer } from '../../models/customer.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-customer-accounts',
  imports: [TopbarComponent, NavTabsComponent],
  templateUrl: './customer-accounts.html',
  styleUrl: './customer-accounts.css'
})
export class CustomerAccountsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);
  private accountService = inject(AccountService);
  private customerService = inject(CustomerService);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  customerId = 0;
  customer = signal<Customer | null>(null);
  accounts = signal<Account[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  closingId = signal<number | null>(null);
  closeError = signal<string | null>(null);
  closeSuccess = signal<string | null>(null);

  ngOnInit(): void {
    this.customerId = Number(this.route.snapshot.paramMap.get('customerId'));
    this.loadCustomer();
    this.loadAccounts();
  }

  loadCustomer(): void {
    this.customerService.getAll().subscribe({
      next: list => {
        const found = list.find(c => c.id === this.customerId) ?? null;
        this.customer.set(found);
      },
      error: () => {}
    });
  }

  loadAccounts(): void {
    this.loading.set(true);
    this.error.set(null);
    this.accountService.getByCustomer(this.customerId).subscribe({
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

  closeAccount(account: Account): void {
    if (account.balance !== 0) return;
    if (!confirm(`Close account ${account.iban}? This cannot be undone.`)) return;

    this.closingId.set(account.id);
    this.closeError.set(null);
    this.closeSuccess.set(null);

    const userId = this.auth.getUserId() ?? undefined;
    this.accountService.closeAccount(account.id, userId).subscribe({
      next: updated => {
        this.accounts.update(list =>
          list.map(a => a.id === updated.id ? updated : a)
        );
        this.closingId.set(null);
        this.closeSuccess.set(`Account ${updated.iban} closed.`);
      },
      error: err => {
        this.closingId.set(null);
        const body = err?.error;
        this.closeError.set(
          body?.message || body?.error || `Failed to close account (HTTP ${err?.status ?? '?'})`
        );
      }
    });
  }

  viewTransactions(accountId: number): void {
    this.router.navigate(['/transactions', accountId]);
  }

  viewStatement(accountId: number): void {
    this.router.navigate(['/statement', accountId]);
  }

  formatAmount(value: number, currency: string): string {
    return `${value.toFixed(2)} ${currency}`;
  }

  goBack(): void {
    this.router.navigate(['/dashboard']);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
