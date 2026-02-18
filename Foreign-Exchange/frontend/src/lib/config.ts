const defaultHttpBase = 'http://localhost:8080';
const defaultWsBase = 'ws://localhost:8080/ws';

export const appConfig = {
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? defaultHttpBase,
  wsUrl: import.meta.env.VITE_WS_URL ?? defaultWsBase,
  apiToken: import.meta.env.VITE_API_TOKEN ?? '',
};
