import {HttpErrorResponse} from '@angular/common/http';
import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {Store} from '@ngrx/store';
import {finalize} from 'rxjs';

import {ApiClientService} from '../../core/api-client.service';
import {AuthTokenService} from '../../core/auth-token.service';
import {QueueActions} from '../../state/queue.actions';

@Component({
  selector: 'qd-change-password-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="login-view">
      <section class="login-panel" aria-labelledby="changePasswordTitle">
        <div>
          <p class="eyebrow">QueueDos</p>
          <h1 id="changePasswordTitle">Choose a new password</h1>
          <p class="muted">Your temporary password must be replaced before you can continue.</p>
        </div>

        <form class="stack" [formGroup]="form" (ngSubmit)="submit()">
          <label>
            New password
            <input type="password" autocomplete="new-password" formControlName="newPassword">
          </label>
          <label>
            Confirm password
            <input type="password" autocomplete="new-password" formControlName="confirmation">
          </label>
          <small class="muted">Use at least 8 characters.</small>
          <button type="submit" class="primary" [disabled]="form.invalid || loading()">
            {{ loading() ? 'Saving...' : 'Set password and continue' }}
          </button>
          @if (error()) {
            <p class="error" role="alert">{{ error() }}</p>
          }
          <button type="button" class="ghost" (click)="signOut()">Sign out</button>
        </form>
      </section>
    </main>
  `
})
export class ChangePasswordPageComponent {
  private readonly api = inject(ApiClientService);
  private readonly auth = inject(AuthTokenService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly form = new FormGroup({
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(8)]
    }),
    confirmation: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
  });

  protected submit(): void {
    if (this.form.invalid || this.loading()) return;
    const {newPassword, confirmation} = this.form.getRawValue();
    if (newPassword !== confirmation) {
      this.error.set('Passwords do not match.');
      return;
    }

    this.loading.set(true);
    this.error.set('');
    this.api.changePassword({newPassword}).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.auth.set(response.token, false);
        this.store.dispatch(QueueActions.appStarted({token: response.token, passwordChangeRequired: false}));
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 401) {
          this.auth.clear();
          void this.router.navigateByUrl('/login');
          return;
        }
        this.error.set(error instanceof HttpErrorResponse ? error.error?.message ?? 'Password could not be changed.' : 'Password could not be changed.');
      }
    });
  }

  protected signOut(): void {
    this.auth.clear();
    this.store.dispatch(QueueActions.logoutCompleted());
    void this.router.navigateByUrl('/login');
  }
}
