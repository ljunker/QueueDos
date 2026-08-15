declare const QUEUEDOS_VERSION: string | undefined;

export const APPLICATION_VERSION =
  typeof QUEUEDOS_VERSION === 'undefined' ? 'dev' : QUEUEDOS_VERSION;

export const APPLICATION_VERSION_LABEL =
  APPLICATION_VERSION === 'dev' ? APPLICATION_VERSION : `v${APPLICATION_VERSION}`;
