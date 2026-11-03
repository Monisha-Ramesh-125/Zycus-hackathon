import { HttpErrorResponse } from '@angular/common/http';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly errorCode: string,
    message: string
  ) {
    super(message);
  }
}

export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) {
    return err;
  }
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return new ApiError(0, 'NETWORK', 'Cannot reach the server.');
    }
    const body = err.error as { errorCode?: string; message?: string } | null;
    if (body && typeof body === 'object' && body.errorCode) {
      return new ApiError(err.status, body.errorCode, body.message ?? '');
    }
    return new ApiError(err.status, `HTTP_${err.status}`, err.statusText || 'The request failed.');
  }
  return new ApiError(-1, 'UNKNOWN', err instanceof Error ? err.message : 'Something unexpected went wrong.');
}

const FRIENDLY: Record<string, string> = {
  NETWORK: "Can't reach the SlotMind server. Check that the backend is running.",
  SLOT_UNAVAILABLE:
    'That slot is no longer free: it was just booked or cancelled. This suggestion is out of date, so ask for a new slot.',
  CONCURRENT_UPDATE: 'Someone else changed this at the same moment. The list was refreshed; please check and try again.',
  NO_SLOT_AVAILABLE: 'No open slot fits this appointment right now.',
  DATA_INTEGRITY: 'The change could not be saved. Please try again.'
};

/** Backend messages for validation and state errors are already readable, so they are shown as they are. */
export function friendlyMessage(error: ApiError): string {
  return FRIENDLY[error.errorCode] ?? (error.message || 'Something went wrong. Please try again.');
}

/** Errors after which the data on screen is probably stale. */
export function suggestsRefresh(error: ApiError): boolean {
  return ['SLOT_UNAVAILABLE', 'CONCURRENT_UPDATE', 'INVALID_STATE'].includes(error.errorCode)
    || error.errorCode.endsWith('_NOT_FOUND');
}
