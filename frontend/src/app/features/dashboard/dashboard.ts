import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CustomerService } from '../../services/customer.service';
import { AccountService } from '../../services/account.service';
import { CardService } from '../../services/card.service';
import { AdminService } from '../../services/admin.service';
import { Customer } from '../../models/customer.model';
import { Account, CreateAccountRequest } from '../../models/account.model';
import { DebitCard } from '../../models/card.model';
import { AdminStats } from '../../models/admin-stats.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, TopbarComponent, NavTabsComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit {
  private auth = inject(AuthService);
  private customerService = inject(CustomerService);
  private accountService = inject(AccountService);
  private cardService = inject(CardService);
  private adminService = inject(AdminService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  customers = signal<Customer[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  /* ── Admin stats ───────────────────────────── */
  stats = signal<AdminStats | null>(null);
  statsLoading = signal(false);

  /* ── Issue Card form (TELLER / ADMIN) ────────── */
  showIssueForm = signal(false);
  selectedCustomerId: number | null = null;
  customerAccounts = signal<Account[]>([]);
  accountsLoading = signal(false);
  selectedAccountId: number | null = null;
  issuing = signal(false);
  issueError = signal<string | null>(null);
  issuedCard = signal<DebitCard | null>(null);
  activating = signal(false);
  activateError = signal<string | null>(null);

  /* ── Open Account form (TELLER / ADMIN) ─────── */
  showOpenAccountForm = signal(false);
  openAccountCustomerId: number | null = null;
  openAccountType = 'CHECKING';
  openAccountCurrency = 'BAM';
  openAccountOverdraft: number | null = null;
  openAccountInterest: number | null = null;
  openingAccount = signal(false);
  openAccountError = signal<string | null>(null);
  openAccountSuccess = signal<Account | null>(null);

  ngOnInit(): void {
    if (this.role === 'TELLER' || this.role === 'ADMIN') {
      this.loadCustomers();
    }
    if (this.role === 'ADMIN') {
      this.loadStats();
    }
  }

  loadCustomers(): void {
    this.loading.set(true);
    this.customerService.getAll().subscribe({
      next: data => {
        this.customers.set(data);
        this.loading.set(false);
        if (data.length > 0) {
          this.selectedCustomerId = data[0].id;
          this.openAccountCustomerId = data[0].id;
          this.loadAccountsForCustomer(data[0].id);
        }
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load customers');
      }
    });
  }

  loadStats(): void {
    this.statsLoading.set(true);
    this.adminService.getStats().subscribe({
      next: data => {
        this.stats.set(data);
        this.statsLoading.set(false);
      },
      error: () => this.statsLoading.set(false)
    });
  }

  toggleIssueForm(): void {
    this.showIssueForm.update(v => !v);
    this.issueError.set(null);
    this.issuedCard.set(null);
  }

  onCustomerChange(): void {
    if (this.selectedCustomerId) {
      this.selectedAccountId = null;
      this.issuedCard.set(null);
      this.issueError.set(null);
      this.loadAccountsForCustomer(this.selectedCustomerId);
    }
  }

  private loadAccountsForCustomer(customerId: number): void {
    this.accountsLoading.set(true);
    this.customerAccounts.set([]);
    this.accountService.getByCustomer(customerId).subscribe({
      next: accounts => {
        this.customerAccounts.set(accounts);
        this.selectedAccountId = accounts.length > 0 ? accounts[0].id : null;
        this.accountsLoading.set(false);
      },
      error: () => {
        this.accountsLoading.set(false);
      }
    });
  }

  issueCard(): void {
    this.issueError.set(null);
    this.issuedCard.set(null);

    if (!this.selectedAccountId) {
      this.issueError.set('Please select an account.');
      return;
    }

    const userId = this.auth.getUserId();
    this.issuing.set(true);

    this.cardService.issueCard(this.selectedAccountId, userId ?? 0).subscribe({
      next: card => {
        this.issuedCard.set(card);
        this.issuing.set(false);
      },
      error: err => {
        this.issuing.set(false);
        const body = err?.error;
        this.issueError.set(
          body?.message || body?.error || `Failed to issue card (HTTP ${err?.status ?? '?'})`
        );
      }
    });
  }

  activateCard(): void {
    const card = this.issuedCard();
    if (!card) return;
    this.activating.set(true);
    this.activateError.set(null);
    this.cardService.activateCard(card.id).subscribe({
      next: updated => {
        this.issuedCard.set(updated);
        this.activating.set(false);
      },
      error: err => {
        this.activating.set(false);
        const body = err?.error;
        this.activateError.set(body?.message || `Activation failed (HTTP ${err?.status ?? '?'})`);
      }
    });
  }

  /* ── Open Account ────────────────────────────── */
  toggleOpenAccountForm(): void {
    this.showOpenAccountForm.update(v => !v);
    this.openAccountError.set(null);
    this.openAccountSuccess.set(null);
  }

  openAccount(): void {
    if (!this.openAccountCustomerId) {
      this.openAccountError.set('Please select a customer.');
      return;
    }
    this.openingAccount.set(true);
    this.openAccountError.set(null);
    this.openAccountSuccess.set(null);

    const body: CreateAccountRequest = {
      customerId: this.openAccountCustomerId,
      accountType: this.openAccountType,
      currency: this.openAccountCurrency,
      createdBy: this.auth.getUserId() ?? undefined
    };

    if (this.openAccountType === 'CHECKING' && this.openAccountOverdraft != null) {
      body.overdraftLimit = this.openAccountOverdraft;
    }
    if (this.openAccountType === 'SAVINGS' && this.openAccountInterest != null) {
      body.interestRate = this.openAccountInterest;
    }

    this.accountService.openAccount(body).subscribe({
      next: account => {
        this.openAccountSuccess.set(account);
        this.openingAccount.set(false);
        this.openAccountOverdraft = null;
        this.openAccountInterest = null;
      },
      error: err => {
        this.openingAccount.set(false);
        const body = err?.error;
        this.openAccountError.set(
          body?.message || body?.error || `Failed to open account (HTTP ${err?.status ?? '?'})`
        );
      }
    });
  }

  /* ── Navigation ──────────────────────────────── */
  manageAccounts(customerId: number): void {
    this.router.navigate(['/customers', customerId, 'accounts']);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
