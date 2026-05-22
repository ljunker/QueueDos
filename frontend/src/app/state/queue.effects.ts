import {HttpErrorResponse} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Router} from '@angular/router';
import {Actions, createEffect, ofType} from '@ngrx/effects';
import {Store} from '@ngrx/store';
import {catchError, concatMap, debounceTime, filter, map, of, switchMap, tap, withLatestFrom} from 'rxjs';

import {ApiClientService} from '../core/api-client.service';
import {AuthTokenService} from '../core/auth-token.service';
import {QueueActions} from './queue.actions';
import {selectUrlQueryParams} from './queue.selectors';

@Injectable()
export class QueueEffects {
  private readonly actions$ = inject(Actions);
  private readonly api = inject(ApiClientService);
  private readonly auth = inject(AuthTokenService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);

  readonly appStarted$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.appStarted),
      filter(({ token }) => Boolean(token)),
      map(() => QueueActions.bootstrapRequested())
    )
  );

  readonly login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.loginRequested),
      concatMap(({ request }) =>
        this.api.login(request).pipe(
          tap((response) => this.auth.set(response.token)),
          map((response) => QueueActions.loginSucceeded(response)),
          catchError((error: unknown) => of(QueueActions.loginFailed({ error: errorMessage(error, 'Sign in failed.') })))
        )
      )
    )
  );

  readonly loginSucceeded$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.loginSucceeded),
      tap(() => void this.router.navigateByUrl('/')),
      map(() => QueueActions.bootstrapRequested())
    )
  );

  readonly logout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.logoutRequested),
      tap(() => {
        this.auth.clear();
        void this.router.navigateByUrl('/login');
      }),
      map(() => QueueActions.logoutCompleted())
    )
  );

  readonly bootstrap$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.bootstrapRequested),
      switchMap(() =>
        this.api.bootstrap().pipe(
          map((data) => QueueActions.bootstrapSucceeded({ data })),
          catchError((error: unknown) => {
            if (error instanceof HttpErrorResponse && error.status === 401) {
              this.auth.clear();
              void this.router.navigateByUrl('/login');
              return of(QueueActions.logoutCompleted());
            }
            return of(QueueActions.bootstrapFailed({ error: errorMessage(error, 'Could not load data.') }));
          })
        )
      )
    )
  );

  readonly ticketDialogSaved$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketDialogSaved),
      map((save) =>
        save.mode === 'create'
          ? QueueActions.ticketCreateRequested({ request: save.request })
          : QueueActions.ticketUpdateRequested(save.request)
      )
    )
  );

  readonly createTicket$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketCreateRequested),
      concatMap(({ request }) =>
        this.api.createTicket(request).pipe(
          map((ticket) => QueueActions.mutationSucceeded({ focusTicketId: ticket.id })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Ticket could not be saved.') })))
        )
      )
    )
  );

  readonly updateTicket$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketUpdateRequested),
      concatMap(({ id, request, toStatusId }) =>
        this.api.updateTicket(id, request).pipe(
          switchMap((ticket) => {
            if (ticket.statusId === toStatusId) {
              return of(QueueActions.mutationSucceeded({ focusTicketId: ticket.id }));
            }
            return this.api.transitionTicket(id, { toStatusId }).pipe(
              map(() => QueueActions.mutationSucceeded({ focusTicketId: id }))
            );
          }),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Ticket could not be saved.') })))
        )
      )
    )
  );

  readonly transitionTicket$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketTransitionRequested),
      concatMap(({ ticket, toStatusId }) =>
        this.api.transitionTicket(ticket.id, { toStatusId }).pipe(
            map(() => QueueActions.mutationSucceeded({})),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Transition failed.') })))
        )
      )
    )
  );

  readonly deleteTicket$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketDeleteRequested),
      concatMap(({ ticketId }) =>
        this.api.deleteTicket(ticketId).pipe(
          map(() => QueueActions.mutationSucceeded({})),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Ticket could not be deleted.') })))
        )
      )
    )
  );

  readonly restoreTicket$ = createEffect(() =>
      this.actions$.pipe(
          ofType(QueueActions.ticketRestoreRequested),
          concatMap(({ticketId}) =>
              this.api.restoreTicket(ticketId).pipe(
                  map((ticket) => QueueActions.mutationSucceeded({
                    message: 'Ticket restored.',
                    focusTicketId: ticket.id
                  })),
                  catchError((error: unknown) => of(QueueActions.mutationFailed({error: errorMessage(error, 'Ticket could not be restored.')})))
              )
          )
      )
  );

  readonly saveCommitment$ = createEffect(() =>
      this.actions$.pipe(
          ofType(QueueActions.ticketCommitmentRequested),
          concatMap(({ticketId, committed}) =>
              this.api.saveCommitment(ticketId, committed).pipe(
                  map(() => QueueActions.mutationSucceeded({focusTicketId: ticketId})),
                  catchError((error: unknown) => of(QueueActions.mutationFailed({error: errorMessage(error, 'Commitment could not be saved.')})))
        )
      )
    )
  );

  readonly bulkUpdateTickets$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketsBulkUpdateRequested),
      concatMap(({ request }) =>
        this.api.bulkUpdateTickets(request).pipe(
          map(() => QueueActions.mutationSucceeded({ message: 'Tickets updated.' })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Tickets could not be updated.') })))
        )
      )
    )
  );

  readonly createComment$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.commentCreateRequested),
      concatMap(({ ticketId, request }) =>
        this.api.addComment(ticketId, request).pipe(
          map(() => QueueActions.mutationSucceeded({ focusTicketId: ticketId })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Comment could not be saved.') })))
        )
      )
    )
  );

  readonly createProject$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.projectCreateRequested),
      concatMap(({ request }) =>
        this.api.createProject(request).pipe(
          map((project) => QueueActions.projectCreated({ project })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Project could not be created.') })))
        )
      )
    )
  );

  readonly projectCreated$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.projectCreated),
      map(({ project }) => QueueActions.projectSelected({ projectId: project.id }))
    )
  );

  readonly reloadAfterProjectCreated$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.projectCreated),
      map(() => QueueActions.mutationSucceeded({}))
    )
  );

  readonly createUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.userCreateRequested),
      concatMap(({ request }) =>
        this.api.createUser(request).pipe(
          map(() => QueueActions.mutationSucceeded({})),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'User could not be created.') })))
        )
      )
    )
  );

  readonly updateUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.userUpdateRequested),
      concatMap(({ userId, request }) =>
        this.api.updateUser(userId, request).pipe(
          map(() => QueueActions.mutationSucceeded({})),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'User could not be updated.') })))
        )
      )
    )
  );

  readonly createTicketType$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketTypeCreateRequested),
      concatMap(({ request }) =>
        this.api.createTicketType(request).pipe(
          map(() => QueueActions.mutationSucceeded({})),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Ticket type could not be created.') })))
        )
      )
    )
  );

  readonly deleteTicketType$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.ticketTypeDeleteRequested),
      concatMap(({ typeId }) =>
        this.api.deleteTicketType(typeId).pipe(
          map(() => QueueActions.mutationSucceeded({})),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Ticket type could not be deleted.') })))
        )
      )
    )
  );

  readonly saveWorkflow$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.workflowSaveRequested),
      concatMap(({ projectId, request }) =>
        this.api.saveWorkflow(projectId, request).pipe(
          map(() => QueueActions.mutationSucceeded({ message: 'Workflow saved.' })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Workflow could not be saved.') })))
        )
      )
    )
  );

  readonly createSavedTicketFilter$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.savedTicketFilterCreateRequested),
      concatMap(({ request }) =>
        this.api.createSavedTicketFilter(request).pipe(
          map(() => QueueActions.mutationSucceeded({ message: 'Filter saved.' })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Filter could not be saved.') })))
        )
      )
    )
  );

  readonly updateSavedTicketFilter$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.savedTicketFilterUpdateRequested),
      concatMap(({ filterId, request }) =>
        this.api.updateSavedTicketFilter(filterId, request).pipe(
          map(() => QueueActions.mutationSucceeded({ message: 'Filter updated.' })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Filter could not be updated.') })))
        )
      )
    )
  );

  readonly deleteSavedTicketFilter$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.savedTicketFilterDeleteRequested),
      concatMap(({ filterId }) =>
        this.api.deleteSavedTicketFilter(filterId).pipe(
          map(() => QueueActions.mutationSucceeded({ message: 'Filter deleted.' })),
          catchError((error: unknown) => of(QueueActions.mutationFailed({ error: errorMessage(error, 'Filter could not be deleted.') })))
        )
      )
    )
  );

  readonly createActivityHook$ = createEffect(() =>
      this.actions$.pipe(
          ofType(QueueActions.activityHookCreateRequested),
          concatMap(({request}) =>
              this.api.createActivityHook(request).pipe(
                  map(() => QueueActions.mutationSucceeded({message: 'Slack hook saved.'})),
                  catchError((error: unknown) => of(QueueActions.mutationFailed({error: errorMessage(error, 'Slack hook could not be saved.')})))
              )
          )
      )
  );

  readonly updateActivityHook$ = createEffect(() =>
      this.actions$.pipe(
          ofType(QueueActions.activityHookUpdateRequested),
          concatMap(({hookId, request}) =>
              this.api.updateActivityHook(hookId, request).pipe(
                  map(() => QueueActions.mutationSucceeded({message: 'Slack hook updated.'})),
                  catchError((error: unknown) => of(QueueActions.mutationFailed({error: errorMessage(error, 'Slack hook could not be updated.')})))
              )
          )
      )
  );

  readonly deleteActivityHook$ = createEffect(() =>
      this.actions$.pipe(
          ofType(QueueActions.activityHookDeleteRequested),
          concatMap(({hookId}) =>
              this.api.deleteActivityHook(hookId).pipe(
                  map(() => QueueActions.mutationSucceeded({message: 'Slack hook deleted.'})),
                  catchError((error: unknown) => of(QueueActions.mutationFailed({error: errorMessage(error, 'Slack hook could not be deleted.')})))
              )
          )
      )
  );

  readonly reloadAfterMutation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.mutationSucceeded),
      map(() => QueueActions.bootstrapRequested())
    )
  );

  readonly toastAfterMutation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(QueueActions.mutationSucceeded),
      filter(({ message }) => Boolean(message)),
      map(({ message }) => QueueActions.toastShown({ message: message ?? '' }))
    )
  );

  readonly syncUrl$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(
          QueueActions.projectSelected,
          QueueActions.tabSelected,
          QueueActions.ticketDetailOpened,
          QueueActions.detailClosed,
          QueueActions.filtersChanged,
          QueueActions.myTicketsFiltersChanged,
          QueueActions.savedTicketFilterApplied
        ),
        debounceTime(0),
        withLatestFrom(this.store.select(selectUrlQueryParams)),
        tap(([, queryParams]) => {
          void this.router.navigate([], {
            queryParams,
            replaceUrl: true
          });
        })
      ),
    { dispatch: false }
  );
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    return error.error?.message ?? error.message ?? fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
