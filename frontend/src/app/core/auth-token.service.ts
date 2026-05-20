import { Injectable, computed, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthTokenService {
  private readonly storageKey = 'queuedosToken';
  readonly token = signal<string | null>(localStorage.getItem(this.storageKey));
  readonly hasToken = computed(() => Boolean(this.token()));

  set(token: string): void {
    localStorage.setItem(this.storageKey, token);
    this.token.set(token);
  }

  clear(): void {
    localStorage.removeItem(this.storageKey);
    this.token.set(null);
  }
}
