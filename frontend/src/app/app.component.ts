import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';

import { AuthTokenService } from './core/auth-token.service';
import { ThemeService } from './core/theme.service';
import { QueueActions } from './state/queue.actions';

@Component({
  selector: 'qd-root',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <router-outlet />
  `
})
export class AppComponent {
  private readonly auth = inject(AuthTokenService);
  private readonly theme = inject(ThemeService);
  private readonly store = inject(Store);

  constructor() {
    this.store.dispatch(QueueActions.themeInitialized({ theme: this.theme.readInitialTheme() }));
    this.store.dispatch(QueueActions.appStarted({ token: this.auth.token() }));
  }
}
