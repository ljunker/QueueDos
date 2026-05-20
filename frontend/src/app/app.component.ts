import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AuthTokenService } from './core/auth-token.service';

@Component({
  selector: 'qd-root',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  template: `
    <header class="app-header">
      <a routerLink="/" class="brand" aria-label="QueueDos home">
        <span class="mark">Q</span>
        <span>QueueDos</span>
      </a>
      @if (auth.hasToken()) {
        <button type="button" class="ghost" (click)="logout()">Sign out</button>
      }
    </header>

    <main class="app-main">
      <router-outlet />
    </main>
  `
})
export class AppComponent {
  protected readonly auth = inject(AuthTokenService);
  private readonly router = inject(Router);

  protected logout(): void {
    this.auth.clear();
    void this.router.navigateByUrl('/login');
  }
}
