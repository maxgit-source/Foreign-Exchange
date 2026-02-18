import { create } from 'zustand';
import type { Ticker } from '@/types/market';
import type { TickEvent } from '@/types/api';

interface MarketState {
  tickers: Record<string, Ticker>;
  selectedSymbol: string;
  isConnected: boolean;
  
  // Actions
  updateTicker: (ticker: Ticker) => void;
  upsertTickFromStream: (tick: TickEvent) => void;
  selectSymbol: (symbol: string) => void;
  setConnectionStatus: (status: boolean) => void;
}

export const useMarketStore = create<MarketState>((set) => ({
  tickers: {},
  selectedSymbol: 'BTC/USDT',
  isConnected: false,

  updateTicker: (ticker) => set((state) => ({
    tickers: { ...state.tickers, [ticker.symbol]: ticker }
  })),
  upsertTickFromStream: (tick) => set((state) => {
    const prev = state.tickers[tick.symbol];
    const change24h = prev && prev.price > 0
      ? ((tick.price - prev.price) / prev.price) * 100
      : 0;
    const volume = (prev?.volume ?? 0) + tick.quantity;

    return {
      tickers: {
        ...state.tickers,
        [tick.symbol]: {
          symbol: tick.symbol,
          price: tick.price,
          change24h,
          volume,
          lastUpdateNs: tick.timestamp_ns,
          source: tick.source,
        },
      },
      selectedSymbol: state.selectedSymbol || tick.symbol,
    };
  }),
  selectSymbol: (symbol) => set({ selectedSymbol: symbol }),
  setConnectionStatus: (status) => set({ isConnected: status }),
}));
