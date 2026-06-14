import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TransactionService } from '../../services/transaction.service';
import { ExchangeRate } from '../../models/exchange-rate.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-exchange-rates',
  imports: [TopbarComponent, NavTabsComponent],
  templateUrl: './exchange-rates.html',
  styleUrl: './exchange-rates.css'
})
export class ExchangeRatesComponent implements OnInit {
  private auth = inject(AuthService);
  private txService = inject(TransactionService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  rates = signal<ExchangeRate[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.txService.getExchangeRates().subscribe({
      next: data => {
        this.rates.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load exchange rates');
      }
    });
  }

  formatDate(dateStr: string | null): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric'
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
