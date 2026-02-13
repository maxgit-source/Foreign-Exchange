import { useEffect, useRef } from 'react';
import { useMarketStore } from '@/store/market';

export const useWebSocket = (url: string) => {
  const socketRef = useRef<WebSocket | null>(null);
  const setStatus = useMarketStore(state => state.setConnectionStatus);

  useEffect(() => {
    const connect = () => {
      socketRef.current = new WebSocket(url);

      socketRef.current.onopen = () => {
        console.log('[WS] Connected');
        setStatus(true);
      };

      socketRef.current.onclose = () => {
        console.log('[WS] Disconnected');
        setStatus(false);
        // Reconnect logic would go here
        setTimeout(connect, 3000);
      };

      socketRef.current.onmessage = (event) => {
        // Handle incoming messages
        // const data = JSON.parse(event.data);
      };
    };

    connect();

    return () => {
      socketRef.current?.close();
    };
  }, [url, setStatus]);

  return socketRef.current;
};
