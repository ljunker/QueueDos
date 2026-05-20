import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';

import { AuthTokenService } from '../../core/auth-token.service';
import { QueueActions } from '../../state/queue.actions';
import { selectAuthLoading, selectLoginError } from '../../state/queue.selectors';

@Component({
  selector: 'qd-login-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="login-view">
      <section class="login-panel" aria-labelledby="loginTitle">
        <div>
          <p class="eyebrow">QueueDos</p>
          <h1 id="loginTitle">Issue tracking</h1>
        </div>

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
          <label>
            Email
            <input type="email" autocomplete="username" formControlName="email">
          </label>
          <label>
            Password
            <input type="password" autocomplete="current-password" formControlName="password">
          </label>
          <button type="submit" class="primary" [disabled]="form.invalid || loading()">
            {{ loading() ? 'Signing in...' : 'Sign in' }}
          </button>
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }
        </form>
      </section>
    </main>
  `
})
export class LoginPageComponent {
  private readonly store = inject(Store);
  private readonly auth = inject(AuthTokenService);
  private readonly router = inject(Router);

  protected readonly loading = this.store.selectSignal(selectAuthLoading);
  protected readonly error = this.store.selectSignal(selectLoginError);
  protected readonly form = new FormGroup({
    email: new FormControl('admin@queuedos.local', {
      nonNullable: true,
      validators: [Validators.required, Validators.email]
    }),
    password: new FormControl('admin', {
      nonNullable: true,
      validators: [Validators.required]
    })
  });

  constructor() {
    if (this.auth.hasToken()) {
      void this.router.navigateByUrl('/');
    }
  }

  protected submit(): void {
    if (this.form.invalid || this.loading()) return;
    this.store.dispatch(QueueActions.loginRequested({ request: this.form.getRawValue() }));
  }
}
