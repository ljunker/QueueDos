import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiClientService } from '../../core/api-client.service';
import { AuthTokenService } from '../../core/auth-token.service';

@Component({
  selector: 'qd-login-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <section class="auth-panel">
      <div>
        <p class="eyebrow">QueueDos</p>
        <h1>Issue tracking</h1>
      </div>

      <form [formGroup]="form" class="stack" (ngSubmit)="submit()">
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
  `
})
export class LoginPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly auth = inject(AuthTokenService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal('');
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

  protected submit(): void {
    if (this.form.invalid || this.loading()) return;

    this.loading.set(true);
    this.error.set('');

    this.api.login(this.form.getRawValue()).subscribe({
      next: (response) => {
        this.auth.set(response.token);
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        this.error.set(error instanceof HttpErrorResponse ? error.error?.message ?? error.message : 'Sign in failed.');
        this.loading.set(false);
      }
    });
  }
}
