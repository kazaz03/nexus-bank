import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationService } from './services/notification.service';
import { NotificationToastComponent } from './shared/components/notification-toast/notification-toast';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NotificationToastComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit, OnDestroy {
  protected readonly title = signal('frontend');

  private auth = inject(AuthService);
  private notificationService = inject(NotificationService);

  ngOnInit(): void {
    // Open the SSE stream if a session is already active (e.g. page refresh).
    if (this.auth.isLoggedIn()) {
      this.notificationService.connect();
    }
  }

  ngOnDestroy(): void {
    this.notificationService.disconnect();
  }
}
