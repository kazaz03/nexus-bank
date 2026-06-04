import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CustomerService } from '../../services/customer.service';
import { Customer } from '../../models/customer.model';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-profile',
  imports: [FormsModule, TopbarComponent, NavTabsComponent],
  templateUrl: './profile.html',
  styleUrl: './profile.css'
})
export class ProfileComponent implements OnInit {
  private auth = inject(AuthService);
  private customerService = inject(CustomerService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  profile = signal<Customer | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  editing = signal(false);
  saving = signal(false);
  saveError = signal<string | null>(null);
  saveSuccess = signal(false);

  editFirstName = '';
  editLastName = '';
  editPhone = '';
  editAddress = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.customerService.getMe().subscribe({
      next: p => {
        this.profile.set(p);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        this.error.set(err?.status ? `Failed (HTTP ${err.status})` : 'Failed to load profile');
      }
    });
  }

  startEdit(): void {
    const p = this.profile();
    if (!p) return;
    this.editFirstName = p.firstName;
    this.editLastName = p.lastName;
    this.editPhone = p.phone ?? '';
    this.editAddress = p.address ?? '';
    this.saveError.set(null);
    this.saveSuccess.set(false);
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.saveError.set(null);
  }

  save(): void {
    const p = this.profile();
    if (!p) return;

    if (!this.editFirstName.trim() || !this.editLastName.trim()) {
      this.saveError.set('First name and last name are required.');
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);

    this.customerService.update(p.id, {
      firstName: this.editFirstName.trim(),
      lastName: this.editLastName.trim(),
      phone: this.editPhone.trim() || undefined,
      address: this.editAddress.trim() || undefined
    }).subscribe({
      next: updated => {
        this.profile.set(updated);
        this.saving.set(false);
        this.editing.set(false);
        this.saveSuccess.set(true);
      },
      error: err => {
        this.saving.set(false);
        const body = err?.error;
        this.saveError.set(body?.message || `Save failed (HTTP ${err?.status ?? '?'})`);
      }
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
