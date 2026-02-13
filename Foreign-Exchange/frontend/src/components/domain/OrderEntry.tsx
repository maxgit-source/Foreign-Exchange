import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export function OrderEntry() {
  return (
    <Card className="h-full">
      <CardHeader className="py-3 px-4 border-b border-border/50">
        <CardTitle>Place Order</CardTitle>
      </CardHeader>
      <CardContent className="p-4 space-y-4">
        <div className="grid grid-cols-2 gap-2">
          <Button variant="buy" className="w-full">Buy</Button>
          <Button variant="outline" className="w-full hover:bg-danger/10 hover:text-danger hover:border-danger">Sell</Button>
        </div>

        <div className="space-y-2">
          <label className="text-xs text-text/60">Order Type</label>
          <div className="flex bg-zinc-900 rounded-md p-1 border border-border">
            <button className="flex-1 text-xs py-1 rounded bg-zinc-700 text-white font-medium">Limit</button>
            <button className="flex-1 text-xs py-1 text-text/60 hover:text-text">Market</button>
          </div>
        </div>

        <div className="space-y-2">
          <label className="text-xs text-text/60">Price (USD)</label>
          <input 
            type="number" 
            className="w-full bg-background border border-border rounded px-3 py-2 text-sm text-right font-mono focus:outline-none focus:border-primary"
            defaultValue={45000}
          />
        </div>

        <div className="space-y-2">
          <label className="text-xs text-text/60">Amount (BTC)</label>
          <input 
            type="number" 
            className="w-full bg-background border border-border rounded px-3 py-2 text-sm text-right font-mono focus:outline-none focus:border-primary"
            placeholder="0.00"
          />
        </div>

        <div className="pt-2">
           <Button className="w-full" variant="buy">Buy BTC</Button>
        </div>
      </CardContent>
    </Card>
  );
}
