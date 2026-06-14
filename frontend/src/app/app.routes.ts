import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login';
import { DashboardComponent } from './features/dashboard/dashboard';
import { LoansComponent } from './features/loans/loans';
import { AccountsComponent } from './features/accounts/accounts';
import { CardsComponent } from './features/cards/cards';
import { ProfileComponent } from './features/profile/profile';
import { TransferComponent } from './features/transfer/transfer';
import { TransactionsComponent } from './features/transactions/transactions';
import { ApplyLoanComponent } from './features/apply-loan/apply-loan';
import { LoanConfirmationComponent } from './features/loan-confirmation/loan-confirmation';
import { MyLoansComponent } from './features/my-loans/my-loans';
import { CustomerAccountsComponent } from './features/customer-accounts/customer-accounts';
import { StatementComponent } from './features/statement/statement';
import { ExchangeRatesComponent } from './features/exchange-rates/exchange-rates';
import { AccessDeniedComponent } from './features/access-denied/access-denied';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

const CUSTOMER_ONLY = { roles: ['CUSTOMER'] };

export const routes: Routes = [
  { path: '',                                     redirectTo: '/login', pathMatch: 'full' },
  { path: 'login',                                component: LoginComponent },
  { path: 'access-denied',                        component: AccessDeniedComponent,       canActivate: [authGuard] },

  { path: 'dashboard',                            component: DashboardComponent,          canActivate: [authGuard] },

  // Customer-only screens (rely on the logged-in customer's own profile/accounts)
  { path: 'accounts',                             component: AccountsComponent,           canActivate: [authGuard, roleGuard], data: CUSTOMER_ONLY },
  { path: 'cards',                                component: CardsComponent,              canActivate: [authGuard, roleGuard], data: CUSTOMER_ONLY },
  { path: 'profile',                              component: ProfileComponent,            canActivate: [authGuard, roleGuard], data: CUSTOMER_ONLY },
  { path: 'transfer',                             component: TransferComponent,           canActivate: [authGuard, roleGuard], data: CUSTOMER_ONLY },
  { path: 'apply-loan',                           component: ApplyLoanComponent,          canActivate: [authGuard, roleGuard], data: CUSTOMER_ONLY },
  { path: 'loan-confirmation',                    component: LoanConfirmationComponent,   canActivate: [authGuard, roleGuard], data: CUSTOMER_ONLY },
  { path: 'my-loans',                             component: MyLoansComponent,            canActivate: [authGuard, roleGuard], data: CUSTOMER_ONLY },

  // Loan officer / admin
  { path: 'loans',                                component: LoansComponent,              canActivate: [authGuard, roleGuard], data: { roles: ['LOAN_OFFICER', 'ADMIN'] } },

  // Teller / admin
  { path: 'customers/:customerId/accounts',       component: CustomerAccountsComponent,   canActivate: [authGuard, roleGuard], data: { roles: ['TELLER', 'ADMIN'] } },

  // Shared (customer sees own, staff sees any) — auth only
  { path: 'transactions/:accountId',              component: TransactionsComponent,       canActivate: [authGuard] },
  { path: 'statement/:accountId',                 component: StatementComponent,          canActivate: [authGuard] },
  { path: 'exchange-rates',                       component: ExchangeRatesComponent,      canActivate: [authGuard] },

  { path: '**',                                   redirectTo: '/login' }
];
