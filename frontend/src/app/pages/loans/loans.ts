import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

interface Loan {
  id: number;
  customerId: number;
  accountId: number;
  amountRequested: number;
  amountApproved: number | null;
  interestRate: number | null;
  termMonths: number;
  purpose: string;
  status: string;
  rejectionReason: string | null;
}

interface Page<T> {
  content: T[];
  totalElements: number;
}

@Component({
  selector: 'app-loans',
  imports: [RouterLink],
  templateUrl: './loans.html',
  styleUrl: './loans.css'
})
export class LoansComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  loans = signal<Loan[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  message = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Page<Loan>>('http://localhost:8080/api/loans?size=50').subscribe({
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
      reviewedBy: 3 // demo: LOAN_OFFICER user id
    };
    this.http.post(`http://localhost:8080/api/loans/${loan.id}/review`, body).subscribe({
      next: () => {
        this.message.set(`Loan ${loan.id} approved — waiting for disbursement saga to complete...`);
        setTimeout(() => this.load(), 3000);
      },
      error: err => this.error.set(err?.error?.message || 'Approval failed')
    });
  }

  reject(loan: Loan): void {
    const body = { approved: false, rejectionReason: 'Rejected by officer', reviewedBy: 3 };
    this.http.post(`http://localhost:8080/api/loans/${loan.id}/review`, body).subscribe({
      next: () => {
        this.message.set(`Loan ${loan.id} rejected.`);
        this.load();
      },
      error: err => this.error.set(err?.error?.message || 'Rejection failed')
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
