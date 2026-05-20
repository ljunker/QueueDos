import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthTokenService } from './auth-token.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthTokenService).token();
  if (!token || request.url.includes('/api/auth/login')) {
    return next(request);
  }

  return next(
    request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    })
  );
};
