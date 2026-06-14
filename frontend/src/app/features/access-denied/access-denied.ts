import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TopbarComponent } from '../../shared/components/topbar/topbar';
import { NavTabsComponent } from '../../shared/components/nav-tabs/nav-tabs';

@Component({
  selector: 'app-access-denied',
  imports: [TopbarComponent, NavTabsComponent],
  templateUrl: './access-denied.html',
  styleUrl: './access-denied.css'
})
export class AccessDeniedComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = this.auth.getEmail();
  role = this.auth.getRole();

  goToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
