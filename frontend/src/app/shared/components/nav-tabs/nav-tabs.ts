import { Component, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-nav-tabs',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav-tabs.html',
  styleUrl: './nav-tabs.css'
})
export class NavTabsComponent {
  @Input() role: string | null = null;
}
