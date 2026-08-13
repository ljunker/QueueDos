import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthTokenService } from './auth-token.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthTokenService);
  const router = inject(Router);

  if (!auth.hasToken()) return router.parseUrl('/login');
  return auth.passwordChangeRequired() ? router.parseUrl('/change-password') : true;
};

export const passwordChangeGuard: CanActivateFn = () => {
  const auth = inject(AuthTokenService);
  const router = inject(Router);

  if (!auth.hasToken()) return router.parseUrl('/login');
  return auth.passwordChangeRequired() || router.parseUrl('/');
};
