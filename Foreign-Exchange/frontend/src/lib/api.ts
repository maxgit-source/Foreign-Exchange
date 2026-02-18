import type { HealthResponse } from '@/types/api';
import { appConfig } from '@/lib/config';

export async function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  if (appConfig.apiToken) {
    headers.Authorization = `Bearer ${appConfig.apiToken}`;
  }

  const response = await fetch(`${appConfig.apiBaseUrl}/api/v1/health`, {
    method: 'GET',
    headers,
    signal,
  });

  if (!response.ok) {
    throw new Error(`Health check failed: ${response.status}`);
  }

  return (await response.json()) as HealthResponse;
}
