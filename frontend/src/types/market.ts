export interface Ticker {
  symbol: string;
  price: number;
  change24h: number;
  volume: number;
}

export interface Trade {
  id: string;
  price: number;
  amount: number;
  side: 'buy' | 'sell';
  time: number;
}

export interface OrderBookLevel {
  price: number;
  size: number;
  total: number;
}
