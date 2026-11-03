import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

import { toApiError } from './api-error';

/** Every failed call reaches the caller as an ApiError carrying the backend's errorCode and message. */
export const apiErrorInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(catchError(error => throwError(() => toApiError(error))));
