import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LoanService } from '../../services/loan.service';
import { Loan, RepaymentSchedule } from '../../models/loan.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-my-loans',
  imports: [FormsModule, RouterLink, TopbarComponent, NavTabsComponent],
  templateUrl: './my-loans.html',
  styleUrl: './my-loans.css'
})
export class MyLoansComponent implements OnInit {
  private auth = inject(AuthService);
  private loanService = inject(LoanService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  loans = signal<Loan[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  expandedLoanId = signal<number | null>(null);
  schedule = signal<RepaymentSchedule[]>([]);
  scheduleLoading = signal(false);
  scheduleError = signal<string | null>(null);

  /* ── Edit pending loan ───────────────────────── */
  editingLoanId = signal<number | null>(null);
  editAmount: number | null = null;
  editCurrency = '';
  editTermMonths: number | null = null;
  editPurpose = '';
  saving = signal(false);
  saveError = signal<string | null>(null);

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
    this.loanService.getByCustomer(+customerId).subscribe({
      next: data => {
        this.loans.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load loans');
      }
    });
  }

  toggleDetails(loan: Loan): void {
    if (this.editingLoanId() === loan.id) {
      this.cancelEdit();
    }

    if (this.expandedLoanId() === loan.id) {
      this.expandedLoanId.set(null);
      this.schedule.set([]);
      return;
    }

    this.expandedLoanId.set(loan.id);
    this.schedule.set([]);
    this.scheduleError.set(null);

    if (loan.status === 'DISBURSED') {
      this.scheduleLoading.set(true);
      this.loanService.getSchedule(loan.id).subscribe({
        next: data => {
          this.schedule.set(data);
          this.scheduleLoading.set(false);
        },
        error: () => {
          this.scheduleLoading.set(false);
          this.scheduleError.set('Could not load repayment schedule.');
        }
      });
    }
  }

  startEdit(loan: Loan): void {
    this.editingLoanId.set(loan.id);
    this.editAmount = loan.amountRequested;
    this.editCurrency = loan.currency;
    this.editTermMonths = loan.termMonths;
    this.editPurpose = loan.purpose;
    this.saveError.set(null);

    if (this.expandedLoanId() !== loan.id) {
      this.expandedLoanId.set(loan.id);
      this.schedule.set([]);
    }
  }

  cancelEdit(): void {
    this.editingLoanId.set(null);
    this.saveError.set(null);
  }

  saveEdit(loan: Loan): void {
    if (this.editAmount == null || this.editAmount < 100) {
      this.saveError.set('Amount must be at least 100.');
      return;
    }
    if (!this.editTermMonths || this.editTermMonths < 1) {
      this.saveError.set('Term must be at least 1 month.');
      return;
    }
    if (!this.editPurpose.trim()) {
      this.saveError.set('Purpose is required.');
      return;
    }

    const ops = [];
    if (this.editAmount !== loan.amountRequested) {
      ops.push({ op: 'replace' as const, path: '/amountRequested', value: this.editAmount });
    }
    if (this.editCurrency !== loan.currency) {
      ops.push({ op: 'replace' as const, path: '/currency', value: this.editCurrency });
    }
    if (this.editTermMonths !== loan.termMonths) {
      ops.push({ op: 'replace' as const, path: '/termMonths', value: this.editTermMonths });
    }
    if (this.editPurpose.trim() !== loan.purpose) {
      ops.push({ op: 'replace' as const, path: '/purpose', value: this.editPurpose.trim() });
    }

    if (ops.length === 0) {
      this.cancelEdit();
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);

    this.loanService.patch(loan.id, ops).subscribe({
      next: updated => {
        this.loans.update(list => list.map(l => l.id === updated.id ? updated : l));
        this.saving.set(false);
        this.editingLoanId.set(null);
      },
      error: err => {
        this.saving.set(false);
        const body = err?.error;
        this.saveError.set(
          body?.message || body?.error || `Save failed (HTTP ${err?.status ?? '?'})`
        );
      }
    });
  }

  remainingBalance(loan: Loan): number {
    return this.schedule()
      .filter(s => s.status !== 'PAID')
      .reduce((sum, s) => sum + s.amountDue, 0);
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }

  formatAmount(amount: number | null, currency = 'BAM'): string {
    if (amount === null || amount === undefined) return '—';
    return `${amount.toFixed(2)} ${currency}`;
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
