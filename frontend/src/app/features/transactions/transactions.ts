import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { TransactionService } from '../../services/transaction.service';
import { Transaction } from '../../models/transaction.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-transactions',
  imports: [RouterLink, FormsModule, TopbarComponent, NavTabsComponent],
  templateUrl: './transactions.html',
  styleUrl: './transactions.css'
})
export class TransactionsComponent implements OnInit {
  private auth = inject(AuthService);
  private transactionService = inject(TransactionService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  accountId = 0;

  transactions = signal<Transaction[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  hasMore = signal(false);
  total = signal(0);

  private currentPage = 0;
  private readonly pageSize = 20;

  filterType = '';
  filterFrom = '';
  filterTo = '';

  ngOnInit(): void {
    const raw = this.route.snapshot.paramMap.get('accountId');
    this.accountId = raw ? +raw : 0;
    this.fetchPage(true);
  }

  applyFilters(): void {
    this.fetchPage(true);
  }

  loadMore(): void {
    this.currentPage++;
    this.fetchPage(false);
  }

  private fetchPage(reset: boolean): void {
    if (reset) {
      this.currentPage = 0;
      this.transactions.set([]);
      this.hasMore.set(false);
    }

    this.loading.set(true);
    this.error.set(null);

    this.transactionService.getByAccount(this.accountId, {
      page: this.currentPage,
      size: this.pageSize,
      type: this.filterType || undefined,
      from: this.filterFrom || undefined,
      to: this.filterTo || undefined
    }).subscribe({
      next: data => {
        this.transactions.update(list => [...list, ...data.content]);
        this.hasMore.set(!data.last);
        this.total.set(data.totalElements);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(
          err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load transactions'
        );
      }
    });
  }

  formatAmount(value: number, currency: string): string {
    return `${value.toFixed(2)} ${currency}`;
  }

  formatDate(dateStr: string): string {
    const d = new Date(dateStr);
    return d.toLocaleDateString('en-GB') + ' '
      + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
