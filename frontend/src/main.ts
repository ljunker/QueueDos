import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, Routes } from '@angular/router';

import { AppComponent } from './app/app.component';
import { authInterceptor } from './app/core/auth.interceptor';
import { BoardPageComponent } from './app/features/board/board-page.component';
import { LoginPageComponent } from './app/features/login/login-page.component';

const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: '', component: BoardPageComponent },
  { path: '**', redirectTo: '' }
];

bootstrapApplication(AppComponent, {
  providers: [
    provideZonelessChangeDetection(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes)
  ]
}).catch((error: unknown) => console.error(error));
