import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

interface Customer {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  kycStatus: string;
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class DashboardComponent implements OnInit {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
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
    this.http.get<Customer[]>('http://localhost:8080/api/customers').subscribe({
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
