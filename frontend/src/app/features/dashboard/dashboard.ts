import { Component, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CustomerService } from '../../services/customer.service';
import { Customer } from '../../models/customer.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-dashboard',
  imports: [TopbarComponent, NavTabsComponent],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit {
  private auth = inject(AuthService);
  private customerService = inject(CustomerService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  customers = signal<Customer[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    if (this.role === 'TELLER' || this.role === 'ADMIN') {
      this.loadCustomers();
    }
  }

  loadCustomers(): void {
    this.loading.set(true);
    this.customerService.getAll().subscribe({
      next: data => {
        this.customers.set(data);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load customers');
      }
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
