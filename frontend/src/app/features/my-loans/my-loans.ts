import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LoanService } from '../../services/loan.service';
import { Loan, RepaymentSchedule } from '../../models/loan.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-my-loans',
  imports: [TopbarComponent, NavTabsComponent],
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

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const customerId = this.auth.getCustomerId();
    if (!customerId) return;

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
