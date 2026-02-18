export type StreamEvent = TickEvent | OrderAckEvent;

export interface TickEvent {
  event: 'tick';
  symbol: string;
  timestamp_ns: number;
  price: number;
  quantity: number;
  side: 'buy' | 'sell';
  source: string;
}

export interface OrderAckEvent {
  event: 'order_ack';
  order_id: number;
  accepted: boolean;
  resting: boolean;
  filled_quantity: number;
  remaining_quantity: number;
  reject_reason: string;
}

export interface HealthResponse {
  status: 'ok' | 'degraded' | 'down';
  timestamp?: string;
  ticks_received?: number;
  ticks_decoded?: number;
  decode_errors?: number;
  order_requests?: number;
  order_accepted?: number;
  order_rejected?: number;
  auth_failures?: number;
  rate_limited?: number;
  tracked_symbols?: number;
}
