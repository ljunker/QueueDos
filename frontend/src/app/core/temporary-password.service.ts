import {Injectable, signal} from '@angular/core';

import {TemporaryPasswordState} from '../state/queue.models';

@Injectable({providedIn: 'root'})
export class TemporaryPasswordService {
  readonly credential = signal<TemporaryPasswordState | null>(null);

  show(credential: TemporaryPasswordState): void {
    this.credential.set(credential);
  }

  clear(): void {
    this.credential.set(null);
  }
}
