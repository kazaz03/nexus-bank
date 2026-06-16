import { Injectable, inject, signal, OnDestroy } from '@angular/core';
import { AuthService } from '../core/services/auth.service';

export interface NotificationEvent {
  type: 'LOAN_DISBURSED' | 'LOAN_REJECTED' | 'LOAN_APPROVED';
  loanId: number;
  message: string;
}

/**
 * Opens a Server-Sent Events connection to loan-service and exposes
 * incoming notifications as an Angular signal.
 *
 * The browser's native EventSource cannot set Authorization headers, so the
 * JWT is forwarded as a ?token= query parameter. The gateway's
 * JwtAuthenticationFilter accepts this on the /api/loans/notifications/** path.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private auth = inject(AuthService);

  readonly notifications = signal<NotificationEvent[]>([]);

  private eventSource: EventSource | null = null;

  /** Opens the SSE connection. Call once when the user is logged in. */
  connect(): void {
    if (this.eventSource) return; // already connected

    const token = this.auth.getToken();
    if (!token) return;

    const url = `/api/loans/notifications/subscribe?token=${encodeURIComponent(token)}`;
    this.eventSource = new EventSource(url);

    this.eventSource.addEventListener('notification', (e: MessageEvent) => {
      try {
        const event: NotificationEvent = JSON.parse(e.data);
        this.notifications.update(list => [event, ...list].slice(0, 20)); // keep last 20
      } catch {
        // ignore malformed payloads
      }
    });

    this.eventSource.onerror = () => {
      // EventSource reconnects automatically; log but don't crash.
      console.warn('[NotificationService] SSE connection error — browser will retry.');
    };
  }

  /** Closes the SSE connection. Call on logout. */
  disconnect(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }

  /** Remove a single notification (e.g. after the user dismisses the toast). */
  dismiss(index: number): void {
    this.notifications.update(list => list.filter((_, i) => i !== index));
  }

  /** Clear all notifications. */
  clearAll(): void {
    this.notifications.set([]);
  }
}
