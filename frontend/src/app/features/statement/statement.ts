import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TransactionService } from '../../services/transaction.service';
import { StatementResponse } from '../../models/statement.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-statement',
  imports: [FormsModule, TopbarComponent, NavTabsComponent],
  templateUrl: './statement.html',
  styleUrl: './statement.css'
})
export class StatementComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);
  private txService = inject(TransactionService);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  accountId = 0;

  fromDate = '';
  toDate = '';

  statement = signal<StatementResponse | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.accountId = Number(this.route.snapshot.paramMap.get('accountId'));

    const now = new Date();
    const firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    this.fromDate = this.toISODate(firstOfMonth);
    this.toDate = this.toISODate(now);
  }

  generate(): void {
    if (!this.fromDate || !this.toDate) {
      this.error.set('Please select both start and end dates.');
      return;
    }
    if (this.fromDate > this.toDate) {
      this.error.set('Start date must be before end date.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.statement.set(null);

    this.txService.getStatement(this.accountId, this.fromDate, this.toDate).subscribe({
      next: data => {
        this.statement.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        const body = err?.error;
        this.error.set(
          body?.message || body?.error || `Failed to load statement (HTTP ${err?.status ?? '?'})`
        );
      }
    });
  }

  formatAmount(value: number | null, currency = ''): string {
    if (value === null || value === undefined) return '—';
    return `${Number(value).toFixed(2)}${currency ? ' ' + currency : ''}`;
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }

  formatDateTime(dateStr: string | null): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  private toISODate(d: Date): string {
    return d.toISOString().split('T')[0];
  }

  goBack(): void {
    const role = this.role;
    if (role === 'TELLER' || role === 'ADMIN') {
      this.router.navigate(['/dashboard']);
    } else {
      this.router.navigate(['/accounts']);
    }
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
