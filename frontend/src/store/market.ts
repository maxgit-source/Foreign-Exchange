import { create } from 'zustand';
import { Ticker } from '@/types/market';

interface MarketState {
  tickers: Record<string, Ticker>;
  selectedSymbol: string;
  isConnected: boolean;
  
  // Actions
  updateTicker: (ticker: Ticker) => void;
  selectSymbol: (symbol: string) => void;
  setConnectionStatus: (status: boolean) => void;
}

export const useMarketStore = create<MarketState>((set) => ({
  tickers: {
    'BTC-USD': { symbol: 'BTC-USD', price: 45000.00, change24h: 2.5, volume: 1500 },
    'ETH-USD': { symbol: 'ETH-USD', price: 3200.50, change24h: -1.2, volume: 8500 },
    'ARS-USD': { symbol: 'ARS-USD', price: 0.0012, change24h: -0.5, volume: 1000000 },
  }, // Initial dummy data
  selectedSymbol: 'BTC-USD',
  isConnected: false,

  updateTicker: (ticker) => set((state) => ({
    tickers: { ...state.tickers, [ticker.symbol]: ticker }
  })),
  selectSymbol: (symbol) => set({ selectedSymbol: symbol }),
  setConnectionStatus: (status) => set({ isConnected: status }),
}));
