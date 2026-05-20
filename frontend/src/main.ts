import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, Routes } from '@angular/router';
import { provideEffects } from '@ngrx/effects';
import { provideStore } from '@ngrx/store';
import { provideStoreDevtools } from '@ngrx/store-devtools';

import { AppComponent } from './app/app.component';
import { authGuard } from './app/core/auth.guard';
import { authInterceptor } from './app/core/auth.interceptor';
import { QueueEffects } from './app/state/queue.effects';
import { queueReducer } from './app/state/queue.reducer';

const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./app/pages/login/login-page.component').then((m) => m.LoginPageComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./app/pages/workspace/workspace-page.component').then((m) => m.WorkspacePageComponent)
  },
  { path: '**', redirectTo: '' }
];

bootstrapApplication(AppComponent, {
  providers: [
    provideZonelessChangeDetection(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes),
    provideStore({ queue: queueReducer }),
    provideEffects([QueueEffects]),
    provideStoreDevtools({ maxAge: 25, connectInZone: false })
  ]
}).catch((error: unknown) => console.error(error));
