import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { Project } from '../../core/api.models';
import { ThemeMode } from '../../state/queue.models';

@Component({
  selector: 'qd-workspace-toolbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="toolbar">
      <div>
        <p class="eyebrow">{{ project()?.key ?? 'Project' }}</p>
        <h2>{{ project()?.name ?? 'No project' }}</h2>
      </div>
      <div class="toolbar-actions">
        <button type="button" class="ghost" (click)="themeToggled.emit()">
          {{ theme() === 'dark' ? 'Light mode' : 'Dark mode' }}
        </button>
        <button type="button" class="primary" [disabled]="!project()" (click)="newTicketRequested.emit()">New ticket</button>
      </div>
    </header>
  `
})
export class WorkspaceToolbarComponent {
  readonly project = input<Project | null>(null);
  readonly theme = input<ThemeMode>('light');
  readonly newTicketRequested = output<void>();
  readonly themeToggled = output<void>();
}
