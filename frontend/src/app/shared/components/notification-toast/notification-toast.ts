import { Component, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService, NotificationEvent } from '../../../services/notification.service';

/**
 * Fixed-position toast stack that displays incoming SSE notifications.
 * Mount once in app.html — it subscribes to NotificationService globally.
 *
 * Each toast auto-dismisses after 6 seconds. The user can also close
 * individual toasts or dismiss all at once.
 */
@Component({
  selector: 'app-notification-toast',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-toast.html',
  styleUrl: './notification-toast.css',
})
export class NotificationToastComponent {
  notificationService = inject(NotificationService);

  notifications = this.notificationService.notifications;

  /** Auto-dismiss each new notification after 6 s. */
  constructor() {
    effect(() => {
      const list = this.notifications();
      if (list.length > 0) {
        // Schedule removal of the most recently added item (index 0).
        setTimeout(() => this.notificationService.dismiss(0), 6000);
      }
    });
  }

  icon(type: NotificationEvent['type']): string {
    return type === 'LOAN_DISBURSED' ? '✅' : '❌';
  }

  cssClass(type: NotificationEvent['type']): string {
    return type === 'LOAN_DISBURSED' ? 'toast--success' : 'toast--error';
  }

  dismiss(index: number): void {
    this.notificationService.dismiss(index);
  }
}
