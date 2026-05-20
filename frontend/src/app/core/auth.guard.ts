import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthTokenService } from './auth-token.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthTokenService);
  const router = inject(Router);

  return auth.hasToken() || router.parseUrl('/login');
};
