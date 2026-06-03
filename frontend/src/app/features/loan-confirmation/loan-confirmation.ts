import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LoanApplicationResponse } from '../../models/loan.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-loan-confirmation',
  imports: [RouterLink, TopbarComponent, NavTabsComponent],
  templateUrl: './loan-confirmation.html',
  styleUrl: './loan-confirmation.css'
})
export class LoanConfirmationComponent implements OnInit {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  loan = signal<LoanApplicationResponse | null>(null);

  ngOnInit(): void {
    const state = history.state as { loan?: LoanApplicationResponse };
    if (state?.loan) {
      this.loan.set(state.loan);
    } else {
      this.router.navigate(['/apply-loan']);
    }
  }

  formatAmount(value: number, currency: string): string {
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value) + ' ' + currency;
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
