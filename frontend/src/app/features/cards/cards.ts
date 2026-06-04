import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { AccountService } from '../../services/account.service';
import { CardService } from '../../services/card.service';
import { Account } from '../../models/account.model';
import { DebitCard } from '../../models/card.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

interface AccountWithCards {
  account: Account;
  cards: DebitCard[];
}

@Component({
  selector: 'app-cards',
  imports: [TopbarComponent, NavTabsComponent],
  templateUrl: './cards.html',
  styleUrl: './cards.css'
})
export class CardsComponent implements OnInit {
  private auth = inject(AuthService);
  private accountService = inject(AccountService);
  private cardService = inject(CardService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  accountsWithCards = signal<AccountWithCards[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  successMsg = signal<string | null>(null);

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
    this.successMsg.set(null);

    this.accountService.getByCustomer(customerId).subscribe({
      next: accounts => {
        if (accounts.length === 0) {
          this.accountsWithCards.set([]);
          this.loading.set(false);
          return;
        }
        const cardRequests = accounts.map(acc =>
          this.cardService.getByAccount(acc.id).pipe(catchError(() => of([] as DebitCard[])))
        );
        forkJoin(cardRequests).subscribe({
          next: cardArrays => {
            this.accountsWithCards.set(
              accounts.map((acc, i) => ({ account: acc, cards: cardArrays[i] }))
            );
            this.loading.set(false);
          }
        });
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load accounts');
      }
    });
  }

  blockCard(card: DebitCard): void {
    this.successMsg.set(null);
    this.error.set(null);
    this.cardService.blockCard(card.id).subscribe({
      next: updated => {
        this.replaceCard(updated);
        this.successMsg.set(`Card ${updated.maskedCardNumber} blocked.`);
      },
      error: err => this.error.set(err?.error?.message || 'Failed to block card.')
    });
  }

  unblockCard(card: DebitCard): void {
    this.successMsg.set(null);
    this.error.set(null);
    this.cardService.unblockCard(card.id).subscribe({
      next: updated => {
        this.replaceCard(updated);
        this.successMsg.set(`Card ${updated.maskedCardNumber} unblocked.`);
      },
      error: err => this.error.set(err?.error?.message || 'Failed to unblock card.')
    });
  }

  private replaceCard(updated: DebitCard): void {
    this.accountsWithCards.update(list =>
      list.map(awc => ({
        ...awc,
        cards: awc.cards.map(c => c.id === updated.id ? updated : c)
      }))
    );
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('en-GB');
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
