import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

const data = [
  { price: 44800, bid: 120, ask: 0 },
  { price: 44900, bid: 80, ask: 0 },
  { price: 45000, bid: 0, ask: 0 }, // Mid price
  { price: 45100, ask: 50, bid: 0 },
  { price: 45200, ask: 150, bid: 0 },
];

export function DepthChart() {
  return (
    <Card className="h-full flex flex-col">
      <CardHeader className="py-2 px-4 border-b border-border/50">
        <CardTitle className="text-xs uppercase text-text/50">Market Depth</CardTitle>
      </CardHeader>
      <CardContent className="flex-1 p-0 min-h-[150px]">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data}>
            <defs>
              <linearGradient id="colorBid" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#10b981" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorAsk" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#ef4444" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="#ef4444" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <XAxis dataKey="price" hide />
            <YAxis hide />
            <Tooltip 
              contentStyle={{ backgroundColor: '#18181b', borderColor: '#27272a', fontSize: '12px' }}
              itemStyle={{ color: '#e4e4e7' }}
            />
            <Area type="step" dataKey="bid" stroke="#10b981" fillOpacity={1} fill="url(#colorBid)" />
            <Area type="step" dataKey="ask" stroke="#ef4444" fillOpacity={1} fill="url(#colorAsk)" />
          </AreaChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}
