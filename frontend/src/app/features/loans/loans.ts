import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LoanService } from '../../services/loan.service';
import { Loan } from '../../models/loan.model';
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
    const body = {
      approved: true,
      amountApproved: loan.amountRequested,
      interestRate: 6.5,
      reviewedBy: 3
    };
    this.loanService.review(loan.id, body).subscribe({
      next: () => {
        this.loans.update(list =>
          list.map(l => l.id === loan.id ? { ...l, status: 'APPROVED' } : l)
        );
      },
      error: err => this.error.set(err?.error?.message || 'Approval failed')
    });
  }

  reject(loan: Loan): void {
    const body = { approved: false, rejectionReason: 'Rejected by officer', reviewedBy: 3 };
    this.loanService.review(loan.id, body).subscribe({
      next: () => {
        this.loans.update(list =>
          list.map(l => l.id === loan.id ? { ...l, status: 'REJECTED' } : l)
        );
      },
      error: err => this.error.set(err?.error?.message || 'Rejection failed')
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
