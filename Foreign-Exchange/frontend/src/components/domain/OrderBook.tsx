import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

const ASKS = [
  { price: 45050.5, size: 0.5, total: 0.5 },
  { price: 45048.0, size: 1.2, total: 1.7 },
  { price: 45045.5, size: 0.8, total: 2.5 },
];

const BIDS = [
  { price: 45040.0, size: 2.0, total: 2.0 },
  { price: 45038.5, size: 0.5, total: 2.5 },
  { price: 45035.0, size: 5.0, total: 7.5 },
];

export function OrderBook() {
  return (
    <Card className="h-full flex flex-col">
      <CardHeader className="py-3 px-4 border-b border-border/50">
        <CardTitle>Order Book</CardTitle>
      </CardHeader>
      <CardContent className="flex-1 overflow-auto p-0 font-mono text-xs">
        {/* Header */}
        <div className="grid grid-cols-3 p-2 text-text/50 bg-surface sticky top-0">
          <span>Price (USD)</span>
          <span className="text-right">Size</span>
          <span className="text-right">Total</span>
        </div>

        {/* Asks (Sells) - Reverse order */}
        <div className="flex flex-col-reverse">
          {ASKS.map((level, i) => (
            <div key={i} className="grid grid-cols-3 px-2 py-0.5 hover:bg-zinc-800/50 cursor-pointer relative">
              <span className="text-danger">{level.price.toFixed(2)}</span>
              <span className="text-right text-text/80">{level.size.toFixed(4)}</span>
              <span className="text-right text-text/50">{level.total.toFixed(4)}</span>
              {/* Depth Bar */}
              <div 
                className="absolute top-0 right-0 h-full bg-danger/10 -z-10" 
                style={{ width: `${Math.min(level.total * 10, 100)}%` }}
              />
            </div>
          ))}
        </div>

        {/* Spread */}
        <div className="py-2 text-center text-sm font-bold text-text bg-zinc-800/20 border-y border-border/30 my-1">
          45,042.50 <span className="text-xs font-normal text-text/50">Spread: 5.50 (0.01%)</span>
        </div>

        {/* Bids (Buys) */}
        <div>
          {BIDS.map((level, i) => (
            <div key={i} className="grid grid-cols-3 px-2 py-0.5 hover:bg-zinc-800/50 cursor-pointer relative">
              <span className="text-accent">{level.price.toFixed(2)}</span>
              <span className="text-right text-text/80">{level.size.toFixed(4)}</span>
              <span className="text-right text-text/50">{level.total.toFixed(4)}</span>
              <div 
                className="absolute top-0 right-0 h-full bg-accent/10 -z-10" 
                style={{ width: `${Math.min(level.total * 10, 100)}%` }}
              />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
