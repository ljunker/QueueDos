import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import {
  BulkUpdateTicketsRequest,
  Priority,
  Project,
  PublicUser,
  SavedTicketFilter,
  Ticket,
  TicketType,
  Workflow
} from '../../core/api.models';
import { MyTicketsFilters } from '../../state/queue.models';
import { MyTicketFiltersComponent } from '../molecules/my-ticket-filters.component';
import { SavedTicketFiltersComponent } from '../molecules/saved-ticket-filters.component';
import { TicketTableComponent } from './ticket-table.component';

@Component({
  selector: 'qd-my-tickets-view',
  standalone: true,
  imports: [MyTicketFiltersComponent, SavedTicketFiltersComponent, TicketTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <qd-my-ticket-filters
      [filters]="filters()"
      [projects]="projects()"
      [priorities]="priorities()"
      (filtersChanged)="filtersChanged.emit($event)" />

    <qd-saved-ticket-filters
      [filters]="savedFilters()"
      (created)="savedFilterCreated.emit($event)"
      (applied)="savedFilterApplied.emit($event)"
      (renamed)="savedFilterRenamed.emit($event)"
      (deleted)="savedFilterDeleted.emit($event)" />

    <qd-ticket-table
      [tickets]="tickets()"
      [projects]="projects()"
      [types]="types()"
      [workflows]="workflows()"
      [priorities]="priorities()"
      [activeUsers]="activeUsers()"
      [allUsers]="users()"
      [showProject]="true"
      (ticketOpened)="ticketOpened.emit($event)"
      (bulkUpdateRequested)="bulkUpdateRequested.emit($event)" />
  `
})
export class MyTicketsViewComponent {
  readonly tickets = input<Ticket[]>([]);
  readonly filters = input.required<MyTicketsFilters>();
  readonly projects = input<Project[]>([]);
  readonly types = input<TicketType[]>([]);
  readonly workflows = input<Workflow[]>([]);
  readonly priorities = input<Priority[]>([]);
  readonly activeUsers = input<PublicUser[]>([]);
  readonly users = input<PublicUser[]>([]);
  readonly savedFilters = input<SavedTicketFilter[]>([]);

  readonly filtersChanged = output<Partial<MyTicketsFilters>>();
  readonly savedFilterCreated = output<string>();
  readonly savedFilterApplied = output<SavedTicketFilter>();
  readonly savedFilterRenamed = output<{ filterId: string; name: string }>();
  readonly savedFilterDeleted = output<string>();
  readonly ticketOpened = output<string>();
  readonly bulkUpdateRequested = output<BulkUpdateTicketsRequest>();
}
