import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

interface Account {
  id: number;
  iban: string;
  accountType: string;        // CHECKING | SAVINGS | FOREIGN
  currency: string;
  balance: number;
  overdraftLimit: number | null;
  status: string;             // ACTIVE | CLOSED | FROZEN
}
@Component({
  selector: 'app-accounts',
  imports: [RouterLink],
  templateUrl: './accounts.html',
  styleUrl: './accounts.css'
})
export class AccountsComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role  = this.auth.getRole();

  accounts = signal<Account[]>([]);
  loading  = signal(false);
  error    = signal<string | null>(null);

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

    this.http
      .get<Account[]>(`http://localhost:8080/api/accounts/customer/${customerId}`)
      .subscribe({
        next: data => {
          this.accounts.set(data);
          this.loading.set(false);
        },
        error: err => {
          this.loading.set(false);
          this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load accounts');
        }
      });
  }

  /**
   * Formats balance with two decimals and the currency code, e.g. "3,842.50 BAM".
   */
  formatAmount(value: number, currency: string): string {
    return `${value.toFixed(2)} ${currency}`;
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
