import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LoanService } from '../../services/loan.service';
import { Loan, RepaymentSchedule } from '../../models/loan.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-loans',
  imports: [TopbarComponent, NavTabsComponent],
  templateUrl: './loans.html',
  styleUrl: './loans.css'
})
export class LoansComponent implements OnInit {
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
    this.loading.set(true);
    this.error.set(null);
    this.loanService.getAll().subscribe({
      next: data => {
        this.loans.set(data.content);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load loans');
      }
    });
  }

  approve(loan: Loan): void {
    this.error.set(null);
    const reviewedBy = this.auth.getUserId();
    if (!reviewedBy) {
      this.error.set('Could not determine the reviewing officer. Please log in again.');
      return;
    }
    const body = {
      approved: true,
      amountApproved: loan.amountRequested,
      interestRate: 6.5,
      reviewedBy
    };
    this.loanService.review(loan.id, body).subscribe({
      next: () => {
        this.loans.update(list =>
          list.map(l => l.id === loan.id ? { ...l, status: 'APPROVED' } : l)
        );
      },
      error: err => this.error.set(err?.error?.message || err?.error?.error || 'Approval failed')
    });
  }

  reject(loan: Loan): void {
    this.error.set(null);
    const reviewedBy = this.auth.getUserId();
    if (!reviewedBy) {
      this.error.set('Could not determine the reviewing officer. Please log in again.');
      return;
    }
    const body = { approved: false, rejectionReason: 'Rejected by officer', reviewedBy };
    this.loanService.review(loan.id, body).subscribe({
      next: () => {
        this.loans.update(list =>
          list.map(l => l.id === loan.id ? { ...l, status: 'REJECTED' } : l)
        );
      },
      error: err => this.error.set(err?.error?.message || err?.error?.error || 'Rejection failed')
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
