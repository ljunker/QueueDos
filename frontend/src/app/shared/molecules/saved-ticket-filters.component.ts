import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';

import { SavedTicketFilter } from '../../core/api.models';

@Component({
  selector: 'qd-saved-ticket-filters',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="saved-filters" aria-label="Saved filters">
      <select aria-label="Saved filter" [value]="selectedId()" (change)="selectFilter(valueOf($event))">
        <option value="">Saved filters</option>
        @for (filter of filters(); track filter.id) {
          <option [value]="filter.id">{{ filter.name }}</option>
        }
      </select>
      <input
        aria-label="Saved filter name"
        placeholder="Filter name"
        [value]="name()"
        (input)="name.set(valueOf($event))">
      <button type="button" [disabled]="!name().trim()" (click)="created.emit(name().trim())">Save current</button>
      <button type="button" [disabled]="!selectedFilter()" (click)="apply()">Apply</button>
      <button type="button" [disabled]="!selectedFilter() || !name().trim()" (click)="rename()">Rename</button>
      <button type="button" class="danger" [disabled]="!selectedFilter()" (click)="remove()">Delete</button>
    </section>
  `
})
export class SavedTicketFiltersComponent {
  readonly filters = input<SavedTicketFilter[]>([]);

  readonly created = output<string>();
  readonly applied = output<SavedTicketFilter>();
  readonly renamed = output<{ filterId: string; name: string }>();
  readonly deleted = output<string>();

  protected readonly selectedId = signal('');
  protected readonly name = signal('');

  constructor() {
    effect(() => {
      if (!this.selectedId()) return;
      if (!this.filters().some((filter) => filter.id === this.selectedId())) {
        this.selectedId.set('');
        this.name.set('');
      }
    });
  }

  protected selectedFilter(): SavedTicketFilter | null {
    return this.filters().find((filter) => filter.id === this.selectedId()) ?? null;
  }

  protected selectFilter(filterId: string): void {
    this.selectedId.set(filterId);
    this.name.set(this.selectedFilter()?.name ?? '');
  }

  protected apply(): void {
    const filter = this.selectedFilter();
    if (filter) this.applied.emit(filter);
  }

  protected rename(): void {
    const filter = this.selectedFilter();
    const name = this.name().trim();
    if (filter && name) this.renamed.emit({ filterId: filter.id, name });
  }

  protected remove(): void {
    const filter = this.selectedFilter();
    if (filter) this.deleted.emit(filter.id);
  }

  protected valueOf(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
