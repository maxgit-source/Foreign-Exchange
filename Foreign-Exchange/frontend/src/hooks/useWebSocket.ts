import { useEffect, useRef } from 'react';
import { useMarketStore } from '@/store/market';
import type { StreamEvent, TickEvent } from '@/types/api';

const isTickEvent = (event: StreamEvent): event is TickEvent => event.event === 'tick';

export const useWebSocket = (url: string): void => {
  const socketRef = useRef<WebSocket | null>(null);
  const setStatus = useMarketStore((state) => state.setConnectionStatus);
  const upsertTickFromStream = useMarketStore((state) => state.upsertTickFromStream);

  useEffect(() => {
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    let closedByEffect = false;

    const connect = () => {
      socketRef.current = new WebSocket(url);

      socketRef.current.onopen = () => {
        console.log('[WS] Connected');
        setStatus(true);
      };

      socketRef.current.onclose = () => {
        console.log('[WS] Disconnected');
        setStatus(false);
        if (!closedByEffect) {
          reconnectTimer = setTimeout(connect, 3000);
        }
      };

      socketRef.current.onmessage = (event) => {
        try {
          const parsed = JSON.parse(event.data as string) as StreamEvent;
          if (isTickEvent(parsed)) {
            upsertTickFromStream(parsed);
          }
        } catch (error) {
          console.error('[WS] Invalid message payload', error);
        }
      };

      socketRef.current.onerror = (event) => {
        console.error('[WS] Error', event);
      };
    };

    connect();

    return () => {
      closedByEffect = true;
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
      }
      socketRef.current?.close();
    };
  }, [url, setStatus, upsertTickFromStream]);
};
