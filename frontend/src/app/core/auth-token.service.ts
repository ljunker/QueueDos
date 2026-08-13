import { Injectable, computed, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthTokenService {
  private readonly storageKey = 'queuedosToken';
  private readonly passwordChangeStorageKey = 'queuedosPasswordChangeRequired';
  readonly token = signal<string | null>(localStorage.getItem(this.storageKey));
  readonly passwordChangeRequired = signal(localStorage.getItem(this.passwordChangeStorageKey) === 'true');
  readonly hasToken = computed(() => Boolean(this.token()));

  set(token: string, passwordChangeRequired = false): void {
    localStorage.setItem(this.storageKey, token);
    if (passwordChangeRequired) {
      localStorage.setItem(this.passwordChangeStorageKey, 'true');
    } else {
      localStorage.removeItem(this.passwordChangeStorageKey);
    }
    this.token.set(token);
    this.passwordChangeRequired.set(passwordChangeRequired);
  }

  clear(): void {
    localStorage.removeItem(this.storageKey);
    localStorage.removeItem(this.passwordChangeStorageKey);
    this.token.set(null);
    this.passwordChangeRequired.set(false);
  }
}
