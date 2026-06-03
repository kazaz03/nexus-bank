import { Component, Input, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'app-topbar',
  templateUrl: './topbar.html',
  styleUrl: './topbar.css'
})
export class TopbarComponent {
  @Input() email: string | null = null;
  @Input() role: string | null = null;
  @Output() logout = new EventEmitter<void>();
}
