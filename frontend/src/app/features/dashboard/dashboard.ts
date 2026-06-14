import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CustomerService } from '../../services/customer.service';
import { RegisterCustomerRequest, UpdateCustomerRequest } from '../../services/customer.service';
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
  imports: [FormsModule, RouterLink, TopbarComponent, NavTabsComponent],
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

  /* ── Customer dashboard (own accounts overview) ─ */
  myAccounts = signal<Account[]>([]);
  myAccountsLoading = signal(false);
  myAccountsError = signal<string | null>(null);

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

  /* ── Existing cards for the selected account ──── */
  accountCards = signal<DebitCard[]>([]);
  accountCardsLoading = signal(false);
  cardActionError = signal<string | null>(null);

  /* ── Register new customer (F01) ──────────────── */
  showRegisterForm = signal(false);
  regEmail = '';
  regPassword = '';
  regFirstName = '';
  regLastName = '';
  regDateOfBirth = '';
  regIdCardNumber = '';
  regAddress = '';
  regPhone = '';
  registering = signal(false);
  registerError = signal<string | null>(null);
  registerSuccess = signal<string | null>(null);

  /* ── Edit customer profile (F04) ──────────────── */
  editingCustomerId = signal<number | null>(null);
  editFirstName = '';
  editLastName = '';
  editPhone = '';
  editAddress = '';
  savingCustomer = signal(false);
  editCustomerError = signal<string | null>(null);

  ngOnInit(): void {
    if (this.role === 'TELLER' || this.role === 'ADMIN') {
      this.loadCustomers();
    } else if (this.role === 'CUSTOMER') {
      this.loadMyAccounts();
    }
    if (this.role === 'ADMIN') {
      this.loadStats();
    }
  }

  loadMyAccounts(): void {
    const customerId = this.auth.getCustomerId();
    if (!customerId) {
      this.myAccountsError.set('Customer profile not found. Please log in again.');
      return;
    }
    this.myAccountsLoading.set(true);
    this.myAccountsError.set(null);
    this.accountService.getByCustomer(customerId).subscribe({
      next: data => {
        this.myAccounts.set(data);
        this.myAccountsLoading.set(false);
      },
      error: err => {
        this.myAccountsLoading.set(false);
        this.myAccountsError.set(
          err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load accounts'
        );
      }
    });
  }

  formatAmount(value: number, currency: string): string {
    return `${value.toFixed(2)} ${currency}`;
  }

  viewTransactions(accountId: number): void {
    this.router.navigate(['/transactions', accountId]);
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

  /* ── F01: register new customer ──────────────── */
  toggleRegisterForm(): void {
    this.showRegisterForm.update(v => !v);
    this.registerError.set(null);
    this.registerSuccess.set(null);
  }

  registerCustomer(): void {
    this.registerError.set(null);
    this.registerSuccess.set(null);

    if (!this.regEmail.trim() || !this.regPassword.trim() || !this.regFirstName.trim()
        || !this.regLastName.trim() || !this.regDateOfBirth || !this.regIdCardNumber.trim()) {
      this.registerError.set('Email, password, first/last name, date of birth and ID card number are required.');
      return;
    }

    const body: RegisterCustomerRequest = {
      email: this.regEmail.trim(),
      password: this.regPassword,
      firstName: this.regFirstName.trim(),
      lastName: this.regLastName.trim(),
      dateOfBirth: this.regDateOfBirth,
      idCardNumber: this.regIdCardNumber.trim(),
      address: this.regAddress.trim() || undefined,
      phone: this.regPhone.trim() || undefined
    };

    this.registering.set(true);
    this.customerService.register(body).subscribe({
      next: created => {
        this.registering.set(false);
        this.registerSuccess.set(`Customer ${created.firstName} ${created.lastName} registered.`);
        this.resetRegisterForm();
        this.loadCustomers();
      },
      error: err => {
        this.registering.set(false);
        const b = err?.error;
        this.registerError.set(
          b?.message || b?.error || `Registration failed (HTTP ${err?.status ?? '?'})`
        );
      }
    });
  }

  private resetRegisterForm(): void {
    this.regEmail = '';
    this.regPassword = '';
    this.regFirstName = '';
    this.regLastName = '';
    this.regDateOfBirth = '';
    this.regIdCardNumber = '';
    this.regAddress = '';
    this.regPhone = '';
  }

  /* ── F04: edit any customer profile ──────────── */
  startEditCustomer(c: Customer): void {
    this.editingCustomerId.set(c.id);
    this.editFirstName = c.firstName;
    this.editLastName = c.lastName;
    this.editPhone = c.phone ?? '';
    this.editAddress = c.address ?? '';
    this.editCustomerError.set(null);
  }

  cancelEditCustomer(): void {
    this.editingCustomerId.set(null);
    this.editCustomerError.set(null);
  }

  saveCustomer(c: Customer): void {
    if (!this.editFirstName.trim() || !this.editLastName.trim()) {
      this.editCustomerError.set('First and last name are required.');
      return;
    }

    const body: UpdateCustomerRequest = {
      firstName: this.editFirstName.trim(),
      lastName: this.editLastName.trim(),
      phone: this.editPhone.trim() || undefined,
      address: this.editAddress.trim() || undefined
    };

    this.savingCustomer.set(true);
    this.editCustomerError.set(null);
    this.customerService.update(c.id, body).subscribe({
      next: updated => {
        this.savingCustomer.set(false);
        this.editingCustomerId.set(null);
        this.customers.update(list => list.map(x => x.id === updated.id ? updated : x));
      },
      error: err => {
        this.savingCustomer.set(false);
        const b = err?.error;
        this.editCustomerError.set(b?.message || `Save failed (HTTP ${err?.status ?? '?'})`);
      }
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
      this.accountCards.set([]);
      this.loadAccountsForCustomer(this.selectedCustomerId);
    }
  }

  onAccountChange(): void {
    this.issuedCard.set(null);
    this.issueError.set(null);
    if (this.selectedAccountId) {
      this.loadCardsForAccount(this.selectedAccountId);
    } else {
      this.accountCards.set([]);
    }
  }

  private loadAccountsForCustomer(customerId: number): void {
    this.accountsLoading.set(true);
    this.customerAccounts.set([]);
    this.accountCards.set([]);
    this.accountService.getByCustomer(customerId).subscribe({
      next: accounts => {
        this.customerAccounts.set(accounts);
        this.selectedAccountId = accounts.length > 0 ? accounts[0].id : null;
        this.accountsLoading.set(false);
        if (this.selectedAccountId) {
          this.loadCardsForAccount(this.selectedAccountId);
        }
      },
      error: () => {
        this.accountsLoading.set(false);
      }
    });
  }

  private loadCardsForAccount(accountId: number): void {
    this.accountCardsLoading.set(true);
    this.cardActionError.set(null);
    this.accountCards.set([]);
    this.cardService.getByAccount(accountId).subscribe({
      next: cards => {
        this.accountCards.set(cards);
        this.accountCardsLoading.set(false);
      },
      error: () => {
        this.accountCardsLoading.set(false);
      }
    });
  }

  private replaceAccountCard(updated: DebitCard): void {
    this.accountCards.update(list => list.map(c => c.id === updated.id ? updated : c));
  }

  activateExistingCard(card: DebitCard): void {
    this.cardActionError.set(null);
    this.cardService.activateCard(card.id).subscribe({
      next: updated => this.replaceAccountCard(updated),
      error: err => this.cardActionError.set(
        err?.error?.message || `Activation failed (HTTP ${err?.status ?? '?'})`
      )
    });
  }

  blockExistingCard(card: DebitCard): void {
    this.cardActionError.set(null);
    this.cardService.blockCard(card.id).subscribe({
      next: updated => this.replaceAccountCard(updated),
      error: err => this.cardActionError.set(
        err?.error?.message || `Block failed (HTTP ${err?.status ?? '?'})`
      )
    });
  }

  unblockExistingCard(card: DebitCard): void {
    this.cardActionError.set(null);
    this.cardService.unblockCard(card.id).subscribe({
      next: updated => this.replaceAccountCard(updated),
      error: err => this.cardActionError.set(
        err?.error?.message || `Unblock failed (HTTP ${err?.status ?? '?'})`
      )
    });
  }

  formatCardDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB');
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
        if (this.selectedAccountId) {
          this.loadCardsForAccount(this.selectedAccountId);
        }
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
        this.replaceAccountCard(updated);
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
