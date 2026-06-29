import { Injectable } from '@angular/core';

import { ThemeMode } from '../state/queue.models';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly storageKey = 'queuedos.theme';

  readInitialTheme(): ThemeMode {
    const theme = this.readStoredTheme();
    this.apply(theme);
    return theme;
  }

  apply(theme: ThemeMode): void {
    document.documentElement.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(this.storageKey, theme);
    } catch {
      // Keep the selected theme for the current document even when storage is unavailable.
    }
  }

  private readStoredTheme(): ThemeMode {
    try {
      const value = localStorage.getItem(this.storageKey);
      return value === 'dark' ? 'dark' : 'light';
    } catch {
      return 'light';
    }
  }
}
