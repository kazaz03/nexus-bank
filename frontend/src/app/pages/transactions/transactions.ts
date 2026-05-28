import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';

interface Transaction {
  id: number;
  accountId: number;
  type: string;
  amount: number;
  currency: string;
  balanceAfter: number;
  counterpartyIban: string | null;
  reference: string | null;
  createdAt: string;
  status: string;
}

interface Page<T> {
  content: T[];
  last: boolean;
  totalElements: number;
}

@Component({
  selector: 'app-transactions',
  imports: [RouterLink, FormsModule],
  templateUrl: './transactions.html',
  styleUrl: './transactions.css'
})
export class TransactionsComponent implements OnInit {
  private http   = inject(HttpClient);
  private auth   = inject(AuthService);
  private router = inject(Router);
  private route  = inject(ActivatedRoute);

  email = this.auth.getEmail();
  role  = this.auth.getRole();

  accountId = 0;

  transactions = signal<Transaction[]>([]);
  loading      = signal(false);
  error        = signal<string | null>(null);
  hasMore      = signal(false);
  total        = signal(0);

  private currentPage = 0;
  private readonly pageSize = 20;

  filterType = '';
  filterFrom = '';
  filterTo   = '';

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

    let url = `http://localhost:8080/api/transactions/account/${this.accountId}`
      + `?page=${this.currentPage}&size=${this.pageSize}`;

    if (this.filterType) {
      url += `&type=${encodeURIComponent(this.filterType)}`;
    }
    if (this.filterFrom) {
      url += `&from=${encodeURIComponent(this.filterFrom + 'T00:00:00')}`;
    }
    if (this.filterTo) {
      url += `&to=${encodeURIComponent(this.filterTo + 'T23:59:59')}`;
    }

    this.http.get<Page<Transaction>>(url).subscribe({
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
