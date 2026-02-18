import { OrderBook } from "@/components/domain/OrderBook";
import { OrderEntry } from "@/components/domain/OrderEntry";
import { DepthChart } from "@/components/domain/DepthChart";
import { useMarketStore } from "@/store/market";
import { useWebSocket } from "@/hooks/useWebSocket";
import { appConfig } from "@/lib/config";
import { fetchHealth } from "@/lib/api";
import { Toaster, toast } from 'sonner';
import { useEffect } from "react";
import { motion } from "framer-motion";

function App() {
  const { isConnected, tickers, selectedSymbol } = useMarketStore();
  const wsUrl = appConfig.apiToken
    ? `${appConfig.wsUrl}${appConfig.wsUrl.includes('?') ? '&' : '?'}token=${encodeURIComponent(appConfig.apiToken)}`
    : appConfig.wsUrl;
  useWebSocket(wsUrl);

  const selectedTicker = tickers[selectedSymbol] ?? Object.values(tickers)[0];
  const headerSymbol = selectedTicker?.symbol ?? selectedSymbol;
  const headerPrice = selectedTicker
    ? selectedTicker.price.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : '--';
  const change24h = selectedTicker?.change24h ?? 0;
  const changePositive = change24h >= 0;

  // Phase 31: Notifications System Demo
  useEffect(() => {
    if (isConnected) {
      toast.success('Connected to Argentum Exchange Core');
    }
  }, [isConnected]);

  useEffect(() => {
    const controller = new AbortController();
    fetchHealth(controller.signal).catch(() => {
      toast.warning('API health endpoint unavailable. Running in stream-only mode.');
    });
    return () => controller.abort();
  }, []);

  return (
    <div className="min-h-screen bg-background text-text flex flex-col font-sans selection:bg-primary/30">
      <Toaster position="bottom-right" theme="dark" />
      
      {/* Navbar with Glassmorphism (Fase 33) */}
      <header className="h-12 border-b border-border flex items-center px-4 justify-between bg-surface/70 backdrop-blur-md sticky top-0 z-50">
        <div className="flex items-center gap-4">
          <div className="font-bold text-lg tracking-tight bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent">
            ARGENTUM<span className="text-white font-light">FX</span>
          </div>
          <div className="h-4 w-[1px] bg-border mx-2"></div>
          
          {/* Animated Ticker (Fase 34) */}
          <motion.div 
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex items-center gap-3 text-sm font-mono"
          >
            <span className="font-bold text-white">{headerSymbol}</span>
            <span className={changePositive ? "text-accent" : "text-danger"}>{headerPrice}</span>
            <span className={`text-xs px-1 rounded ${changePositive ? 'text-accent bg-accent/10' : 'text-danger bg-danger/10'}`}>
              {changePositive ? '+' : ''}{change24h.toFixed(2)}%
            </span>
          </motion.div>
        </div>
        
        <div className="flex items-center gap-4">
           {/* Mock Account Info */}
           <div className="text-xs text-right hidden sm:block">
              <div className="text-text/60">Balance</div>
              <div className="font-mono text-white">12.5402 BTC</div>
           </div>
           
           <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-zinc-900 border border-border">
              <div className={`h-2 w-2 rounded-full ${isConnected ? 'bg-accent shadow-[0_0_8px_rgba(16,185,129,0.5)]' : 'bg-danger'}`}></div>
              <span className="text-[10px] font-bold tracking-wider text-text/70 uppercase">
                {isConnected ? 'LIVE' : 'OFFLINE'}
              </span>
           </div>
        </div>
      </header>

      {/* Main Grid Layout */}
      <main className="flex-1 p-2 grid grid-cols-12 gap-2 overflow-hidden h-[calc(100vh-48px)]">
        
        {/* Left Panel: Chart & Data */}
        <div className="col-span-12 lg:col-span-9 flex flex-col gap-2 h-full">
           {/* Chart Area */}
           <div className="flex-[3] bg-surface border border-border rounded-lg relative overflow-hidden group">
              <div className="absolute inset-0 flex items-center justify-center text-text/30 group-hover:text-text/50 transition-colors">
                 <div className="text-center">
                    <p className="text-2xl font-light">TradingView Chart</p>
                    <p className="text-xs mt-2">Professional Mode</p>
                 </div>
              </div>
           </div>
           
           {/* Bottom Panel: Positions & Depth */}
           <div className="flex-1 min-h-[200px] grid grid-cols-3 gap-2">
              <div className="col-span-2 bg-surface border border-border rounded-lg p-4">
                 <div className="flex justify-between items-center mb-4">
                    <h4 className="text-sm font-semibold">Open Positions</h4>
                    <span className="text-xs text-text/50">PnL: <span className="text-accent">+$1,240.50</span></span>
                 </div>
                 {/* Mock Table */}
                 <table className="w-full text-xs text-left">
                    <thead className="text-text/50 border-b border-border">
                       <tr>
                          <th className="pb-2">Symbol</th>
                          <th className="pb-2">Size</th>
                          <th className="pb-2">Entry</th>
                          <th className="pb-2 text-right">PnL</th>
                       </tr>
                    </thead>
                    <tbody className="font-mono">
                       <tr className="border-b border-border/10">
                          <td className="py-2 text-accent">Buy BTC</td>
                          <td className="py-2">0.5000</td>
                          <td className="py-2">44,100.00</td>
                          <td className="py-2 text-right text-accent">+471.25</td>
                       </tr>
                    </tbody>
                 </table>
              </div>
              <div className="col-span-1">
                 <DepthChart />
              </div>
           </div>
        </div>

        {/* Right Panel: Trading Tools */}
        <div className="col-span-12 lg:col-span-3 flex flex-col gap-2 h-full">
          <div className="flex-[3] min-h-0">
            <OrderBook />
          </div>
          <div className="flex-[2]">
             <OrderEntry />
          </div>
        </div>

      </main>
    </div>
  )
}

export default App
