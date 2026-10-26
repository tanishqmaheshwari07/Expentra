import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  WalletCards,
  CheckCircle2,
  ArrowRight,
  ShieldCheck,
  Zap,
  CreditCard,
  PieChart,
  Sparkles,
  TrendingDown,
  ChevronRight,
} from 'lucide-react';
import { Button, Badge } from '../components/ui';
import { useAuthStore } from '../store/authStore';
import Beams from '../components/Beams';

export const LandingPage: React.FC = () => {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuthStore();

  const handleGetStarted = () => {
    if (isAuthenticated) {
      navigate('/dashboard');
    } else {
      navigate('/register');
    }
  };

  const scrollToPreview = () => {
    document.getElementById('preview-section')?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <div className="min-h-screen bg-black text-neutral-100 flex flex-col selection:bg-neutral-800 selection:text-white relative overflow-x-hidden font-sans">
      {/* ============================================================ */}
      {/* PERSISTENT SINGLE 3D BEAMS ENVIRONMENT LAYER                 */}
      {/* Fixed behind the entire landing-page scroll journey          */}
      {/* ============================================================ */}
      <div className="fixed inset-0 z-0 pointer-events-none overflow-hidden">
        <Beams
          beamWidth={2}
          beamHeight={15}
          beamNumber={12}
          lightColor="#ffffff"
          beamColor="#000000"
          backgroundColor="#000000"
          speed={2}
          noiseIntensity={1.75}
          scale={0.2}
          rotation={0}
        />
      </div>

      {/* Subtle bottom vignette to allow the footer to conclude naturally */}
      <div className="fixed inset-0 z-0 pointer-events-none bg-gradient-to-b from-transparent via-transparent to-black/70" />

      {/* ============================================================ */}
      {/* ALL UI SECTIONS (TRANSLUCENT GLASS & NATURAL DOCUMENT FLOW)   */}
      {/* ============================================================ */}
      <div className="relative z-10 flex flex-col min-h-screen">
        {/* ============================================================ */}
        {/* TOP NAVIGATION BAR                                            */}
        {/* ============================================================ */}
        <header className="sticky top-0 z-50 bg-black/60 backdrop-blur-xl border-b border-white/10 transition-all">
          <div className="max-w-7xl mx-auto px-4 sm:px-8 h-16 sm:h-20 flex items-center justify-between">
            {/* Logo Left */}
            <Link to="/" className="flex items-center gap-2.5 group">
              <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-pill bg-neutral-900/80 border border-white/15 text-white flex items-center justify-center font-bold text-sm sm:text-base shadow-sm shrink-0 group-hover:border-white/30 transition-colors">
                <WalletCards className="w-4 h-4 sm:w-5 sm:h-5 text-white" />
              </div>
              <div className="flex flex-col">
                <span className="text-base sm:text-lg font-extrabold tracking-tight text-white">
                  Expense<span className="text-neutral-300">Tracker</span>
                </span>
                <span className="text-[9px] sm:text-[10px] font-semibold text-neutral-400 uppercase tracking-widest -mt-1">
                  Financial Suite
                </span>
              </div>
            </Link>

            {/* Links Centered */}
            <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-neutral-400">
              <a href="#features" className="hover:text-white transition-colors">
                Product
              </a>
              <a href="#preview-section" className="hover:text-white transition-colors">
                Preview
              </a>
              <a href="#analytics" className="hover:text-white transition-colors">
                Analytics
              </a>
              <a href="#company" className="hover:text-white transition-colors">
                Company
              </a>
            </nav>

            {/* Right Actions */}
            <div className="flex items-center gap-2 sm:gap-4">
              {isAuthenticated ? (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => navigate('/dashboard')}
                  rightIcon={<ArrowRight className="w-4 h-4" />}
                  className="text-xs sm:text-sm bg-white text-black hover:bg-neutral-200"
                >
                  Dashboard
                </Button>
              ) : (
                <>
                  <Link
                    to="/login"
                    className="text-xs sm:text-sm font-medium text-neutral-400 hover:text-white transition-colors px-2.5 py-1.5"
                  >
                    Log in
                  </Link>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={handleGetStarted}
                    className="px-4 sm:px-5 text-xs sm:text-sm shadow-lg bg-white text-black hover:bg-neutral-200 font-semibold"
                  >
                    Get started
                  </Button>
                </>
              )}
            </div>
          </div>
        </header>

        {/* ============================================================ */}
        {/* HERO SECTION                                                 */}
        {/* ============================================================ */}
        <section className="relative pt-16 pb-20 sm:pt-28 sm:pb-36 bg-transparent">
          <div className="max-w-5xl mx-auto px-4 sm:px-6 text-center relative z-10">
            {/* Eyebrow Tag Pill */}
            <motion.div
              initial={{ opacity: 0, y: 15 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5 }}
              className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-pill bg-neutral-950/70 border border-white/15 text-[11px] sm:text-xs font-medium text-neutral-300 mb-8 shadow-sm backdrop-blur-md"
            >
              <span className="w-2 h-2 rounded-full bg-white animate-pulse shadow-[0_0_8px_rgba(255,255,255,0.8)]" />
              <span>Next-Gen Personal Finance & Budgeting</span>
            </motion.div>

            {/* Cinematic H1 Headline */}
            <motion.h1
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.1 }}
              className="text-4xl sm:text-6xl lg:text-7xl font-extrabold text-white tracking-tight leading-[1.12] sm:leading-[1.08]"
            >
              Know exactly where <br className="hidden sm:inline" />
              <span className="bg-gradient-to-r from-white via-neutral-100 to-neutral-400 bg-clip-text text-transparent">
                your money goes
              </span>
            </motion.h1>

            {/* Subtitle Description */}
            <motion.p
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.2 }}
              className="mt-6 text-base sm:text-xl text-[#a3a3a3] max-w-2xl mx-auto leading-relaxed"
            >
              Effortlessly monitor transactions, categorize daily spending, and unlock automated
              budget insights with a sleek, ultra-fast interface.
            </motion.p>

            {/* Trust Indicators */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3 }}
              className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3 sm:gap-8 text-xs sm:text-sm font-medium text-neutral-400"
            >
              <div className="flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-white shrink-0" />
                <span>Trusted by 50K+ users</span>
              </div>
              <div className="flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-white shrink-0" />
                <span>99.9% Cloud Uptime</span>
              </div>
              <div className="flex items-center gap-1.5">
                <CheckCircle2 className="w-4 h-4 text-white shrink-0" />
                <span>Bank-grade 256-bit encryption</span>
              </div>
            </motion.div>

            {/* CTA Buttons */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.4 }}
              className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-3.5 sm:gap-4"
            >
              <Button
                variant="primary"
                size="lg"
                onClick={handleGetStarted}
                className="w-full sm:w-auto px-8 h-12 text-base font-semibold shadow-[0_0_30px_rgba(255,255,255,0.2)] bg-white text-black hover:bg-neutral-200 transition-all"
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Get started free
              </Button>

              <Button
                variant="secondary"
                size="lg"
                onClick={scrollToPreview}
                className="w-full sm:w-auto px-8 h-12 text-base text-white hover:text-neutral-200 bg-black/50 border border-white/20 hover:border-white/40 font-medium backdrop-blur-md transition-all"
              >
                See how it works
              </Button>
            </motion.div>
          </div>
        </section>

        {/* ============================================================ */}
        {/* DASHBOARD PREVIEW MOCKUP SECTION (TRANSLUCENT 3D GLASS)      */}
        {/* ============================================================ */}
        <section id="preview-section" className="py-24 bg-black/40 border-t border-white/10 backdrop-blur-sm relative">
          <div className="max-w-6xl mx-auto px-4 sm:px-8">
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: '-60px' }}
              transition={{ duration: 0.6, ease: 'easeOut' }}
              className="text-center mb-12"
            >
              <Badge variant="accent" className="mb-3 bg-neutral-900/80 border border-white/15 text-neutral-300 backdrop-blur-sm">
                Interactive Mockup
              </Badge>
              <h2 className="text-2xl sm:text-4xl font-extrabold tracking-tight text-white">
                Experience the clarity of modern money management
              </h2>
              <p className="text-sm sm:text-base text-neutral-400 mt-2 max-w-lg mx-auto">
                Everything you need in a single consolidated view, engineered with precision.
              </p>
            </motion.div>

            {/* Interactive Mockup Container */}
            <motion.div
              initial={{ opacity: 0, scale: 0.97, y: 25 }}
              whileInView={{ opacity: 1, scale: 1, y: 0 }}
              viewport={{ once: true, margin: '-40px' }}
              transition={{ duration: 0.7, ease: 'easeOut' }}
            >
              <div className="bg-[#0a0a0c]/85 border border-white/15 shadow-[0_25px_70px_rgba(0,0,0,0.85)] p-6 sm:p-8 rounded-2xl backdrop-blur-xl relative overflow-hidden">
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-stretch">
                  {/* 1. Stat Tile with Mini Bar Chart */}
                  <div className="p-5 rounded-xl bg-neutral-950/70 border border-white/10 backdrop-blur-md flex flex-col justify-between">
                    <div>
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] font-bold uppercase tracking-wider text-neutral-400">
                          Monthly Spending
                        </span>
                        <span className="text-xs font-semibold text-white bg-neutral-900/90 border border-white/10 px-2 py-0.5 rounded-pill flex items-center gap-1">
                          <TrendingDown className="w-3 h-3" /> -8.4%
                        </span>
                      </div>
                      <div className="text-3xl font-extrabold text-white mt-2">₹2,840.50</div>
                      <p className="text-xs text-neutral-400 mt-0.5">Average: ₹94.60 / day</p>
                    </div>

                    {/* Mini Bar Chart Mockup: Subtle dark bars + 1 Highlighted in pure white */}
                    <div className="mt-6 pt-4 border-t border-white/10">
                      <div className="flex items-end justify-between gap-2 h-24 px-1">
                        {[
                          { day: 'M', height: '45%', highlight: false },
                          { day: 'T', height: '60%', highlight: false },
                          { day: 'W', height: '35%', highlight: false },
                          { day: 'T', height: '80%', highlight: false },
                          { day: 'F', height: '95%', highlight: true },
                          { day: 'S', height: '55%', highlight: false },
                          { day: 'S', height: '40%', highlight: false },
                        ].map((bar, i) => (
                          <div key={i} className="flex-1 flex flex-col items-center gap-1.5 h-full justify-end">
                            <div
                              style={{ height: bar.height }}
                              className={`w-full rounded-t-md transition-all duration-300 ${
                                bar.highlight
                                  ? 'bg-white shadow-[0_0_12px_rgba(255,255,255,0.6)]'
                                  : 'bg-neutral-800/80 hover:bg-neutral-700'
                              }`}
                            />
                            <span className={`text-[10px] font-semibold ${bar.highlight ? 'text-white' : 'text-neutral-500'}`}>
                              {bar.day}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* 2. Premium Metallic Monochrome "Card Balance" Tile */}
                  <div className="p-6 rounded-xl bg-gradient-to-br from-neutral-900/90 via-neutral-950/90 to-black/90 border border-white/15 text-white shadow-xl backdrop-blur-md flex flex-col justify-between relative overflow-hidden min-h-[220px]">
                    {/* Decorative card chip & contactless icons */}
                    <div className="flex items-center justify-between relative z-10">
                      <div className="flex items-center gap-2">
                        <div className="w-9 h-7 rounded bg-amber-400/90 border border-amber-300/40 flex items-center justify-center">
                          <div className="w-5 h-4 border border-amber-800/40 rounded-sm" />
                        </div>
                        <CreditCard className="w-5 h-5 text-neutral-400" />
                      </div>
                      <span className="text-xs font-bold uppercase tracking-widest text-neutral-400">
                        ExpenseCard
                      </span>
                    </div>

                    {/* Masked Card Number */}
                    <div className="my-4 relative z-10">
                      <span className="text-xs text-neutral-400 uppercase tracking-wider block text-[10px]">
                        Account Balance
                      </span>
                      <div className="text-2xl font-black tracking-tight text-white mt-0.5">
                        ₹14,250.00
                      </div>
                      <div className="font-mono text-sm tracking-widest text-neutral-400 mt-3">
                        •••• •••• •••• 4289
                      </div>
                    </div>

                    {/* Cardholder Name & Expiry */}
                    <div className="flex items-center justify-between relative z-10 pt-2 border-t border-white/10 text-xs">
                      <div>
                        <span className="text-[9px] uppercase tracking-wider text-neutral-500 block">
                          Cardholder
                        </span>
                        <span className="font-semibold text-neutral-200">ALEX MORGAN</span>
                      </div>
                      <div className="text-right">
                        <span className="text-[9px] uppercase tracking-wider text-neutral-500 block">
                          Expires
                        </span>
                        <span className="font-semibold text-neutral-200">08/29</span>
                      </div>
                    </div>

                    {/* Subtle white atmospheric card glow */}
                    <div className="absolute -top-12 -right-12 w-36 h-36 bg-white/5 rounded-full blur-2xl pointer-events-none" />
                  </div>

                  {/* 3. "Recent Activity" List Tile */}
                  <div className="p-5 rounded-xl bg-neutral-950/70 border border-white/10 backdrop-blur-md flex flex-col justify-between">
                    <div className="flex items-center justify-between pb-3 border-b border-white/10">
                      <span className="text-[11px] font-bold uppercase tracking-wider text-neutral-400">
                        Recent Activity
                      </span>
                      <span className="text-xs font-medium text-white hover:text-neutral-300 cursor-pointer transition-colors">
                        View all
                      </span>
                    </div>

                    <div className="space-y-3 mt-3">
                      {[
                        {
                          name: 'Whole Foods Market',
                          category: 'Groceries',
                          date: 'Today, 2:45 PM',
                          amount: '-₹84.20',
                          isNegative: true,
                        },
                        {
                          name: 'Direct Deposit / Payroll',
                          category: 'Income',
                          date: 'Yesterday',
                          amount: '+₹3,450.00',
                          isNegative: false,
                        },
                        {
                          name: 'Apple Services Subscription',
                          category: 'Digital',
                          date: 'Sep 21',
                          amount: '-₹14.99',
                          isNegative: true,
                        },
                        {
                          name: 'Starbucks Coffee',
                          category: 'Dining',
                          date: 'Sep 20',
                          amount: '-₹6.50',
                          isNegative: true,
                        },
                      ].map((tx, idx) => (
                        <div key={idx} className="flex items-center justify-between text-xs">
                          <div className="flex items-center gap-2.5">
                            <div className="w-7 h-7 rounded-full bg-neutral-900 border border-white/10 flex items-center justify-center text-neutral-200 shrink-0 font-bold">
                              {tx.name.charAt(0)}
                            </div>
                            <div>
                              <div className="font-medium text-white">{tx.name}</div>
                              <div className="text-[10px] text-neutral-500">{tx.date}</div>
                            </div>
                          </div>
                          <div
                            className={`font-semibold ${
                              tx.isNegative ? 'text-neutral-200' : 'text-white font-bold'
                            }`}
                          >
                            {tx.amount}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            </motion.div>
          </div>
        </section>

        {/* ============================================================ */}
        {/* FEATURES HIGHLIGHT SECTION (TRANSLUCENT GLASS CARDS)         */}
        {/* ============================================================ */}
        <section id="features" className="py-24 bg-black/50 border-t border-white/10 backdrop-blur-sm relative">
          <div className="max-w-7xl mx-auto px-4 sm:px-8">
            <div className="text-center max-w-3xl mx-auto mb-16">
              <h2 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
                Engineered for absolute financial peace of mind
              </h2>
              <p className="text-neutral-400 text-base mt-3">
                Everything from automatic category insights to token-based bank security.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5 }}
                className="p-7 rounded-2xl border border-white/10 bg-white/[0.03] hover:bg-white/[0.06] hover:border-white/25 hover:shadow-[0_12px_40px_rgba(255,255,255,0.04)] backdrop-blur-md transition-all group"
              >
                <div className="w-12 h-12 rounded-xl bg-neutral-900/80 border border-white/15 text-white group-hover:border-white/35 flex items-center justify-center mb-5 transition-colors">
                  <PieChart className="w-6 h-6" />
                </div>
                <h3 className="text-lg font-bold text-white">Smart Categorization</h3>
                <p className="text-sm text-neutral-400 mt-2.5 leading-relaxed">
                  Classify expenses across Food, Travel, Shopping, Bills, and custom categories automatically.
                </p>
              </motion.div>

              <motion.div
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: 0.1 }}
                className="p-7 rounded-2xl border border-white/10 bg-white/[0.03] hover:bg-white/[0.06] hover:border-white/25 hover:shadow-[0_12px_40px_rgba(255,255,255,0.04)] backdrop-blur-md transition-all group"
              >
                <div className="w-12 h-12 rounded-xl bg-neutral-900/80 border border-white/15 text-white group-hover:border-white/35 flex items-center justify-center mb-5 transition-colors">
                  <Zap className="w-6 h-6" />
                </div>
                <h3 className="text-lg font-bold text-white">Instant Analytics</h3>
                <p className="text-sm text-neutral-400 mt-2.5 leading-relaxed">
                  Gain real-time visibility into spending trends, monthly budget caps, and variance reports.
                </p>
              </motion.div>

              <motion.div
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.5, delay: 0.2 }}
                className="p-7 rounded-2xl border border-white/10 bg-white/[0.03] hover:bg-white/[0.06] hover:border-white/25 hover:shadow-[0_12px_40px_rgba(255,255,255,0.04)] backdrop-blur-md transition-all group"
              >
                <div className="w-12 h-12 rounded-xl bg-neutral-900/80 border border-white/15 text-white group-hover:border-white/35 flex items-center justify-center mb-5 transition-colors">
                  <ShieldCheck className="w-6 h-6" />
                </div>
                <h3 className="text-lg font-bold text-white">Enterprise-Grade Security</h3>
                <p className="text-sm text-neutral-400 mt-2.5 leading-relaxed">
                  Secure JWT authentication with automatic token rotation and end-to-end data isolation.
                </p>
              </motion.div>
            </div>
          </div>
        </section>

        {/* ============================================================ */}
        {/* MONOCHROME CTA SECTION                                        */}
        {/* ============================================================ */}
        <section className="py-24 bg-black/60 border-t border-white/10 backdrop-blur-sm relative overflow-hidden">
          <div className="max-w-4xl mx-auto px-4 sm:px-6 text-center relative z-10">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6 }}
            >
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-pill bg-neutral-900/80 border border-white/15 text-xs font-semibold text-neutral-300 mb-6 backdrop-blur-sm">
                <Sparkles className="w-3.5 h-3.5 text-white" />
                <span>Ready for complete control?</span>
              </div>

              <h2 className="text-3xl sm:text-5xl font-extrabold text-white tracking-tight">
                Start tracking with absolute precision today
              </h2>

              <p className="mt-4 text-base sm:text-lg text-neutral-400 max-w-xl mx-auto leading-relaxed">
                Join thousands who rely on ExpenseTracker to monitor expenses, save faster, and achieve financial freedom.
              </p>

              <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
                <Button
                  variant="primary"
                  size="lg"
                  onClick={handleGetStarted}
                  className="w-full sm:w-auto px-8 h-12 text-base font-semibold shadow-[0_0_30px_rgba(255,255,255,0.2)] bg-white text-black hover:bg-neutral-200"
                  rightIcon={<ArrowRight className="w-4 h-4" />}
                >
                  Get started for free
                </Button>

                <a
                  href="/swagger-ui.html"
                  className="w-full sm:w-auto inline-flex items-center justify-center px-6 h-12 text-sm font-medium text-neutral-300 hover:text-white bg-black/50 border border-white/20 hover:border-white/40 rounded-pill backdrop-blur-sm transition-colors"
                >
                  Explore API Docs <ChevronRight className="w-4 h-4 ml-1" />
                </a>
              </div>
            </motion.div>
          </div>
        </section>

        {/* ============================================================ */}
        {/* FOOTER (MONOCHROME DARK THEME)                               */}
        {/* ============================================================ */}
        <footer id="company" className="mt-auto bg-black/90 border-t border-white/10 pt-16 pb-12 backdrop-blur-md relative z-10">
          <div className="max-w-7xl mx-auto px-4 sm:px-8">
            <div className="grid grid-cols-2 md:grid-cols-5 gap-8 pb-12 border-b border-white/10">
              {/* Brand column */}
              <div className="col-span-2 space-y-3">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-pill bg-neutral-900 border border-white/15 text-white flex items-center justify-center font-bold text-sm shadow-sm">
                    <WalletCards className="w-4 h-4 text-white" />
                  </div>
                  <span className="text-base font-bold tracking-tight text-white">
                    Expense<span className="text-neutral-300">Tracker</span>
                  </span>
                </div>
                <p className="text-xs text-neutral-400 max-w-sm">
                  Next-generation financial tracking engineered for individuals and modern teams.
                </p>
              </div>

              {/* Link Column 1: Product */}
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-neutral-200 mb-3">Product</h4>
                <ul className="space-y-2 text-xs text-neutral-400">
                  <li><a href="#features" className="hover:text-white transition-colors">Overview</a></li>
                  <li><a href="#preview-section" className="hover:text-white transition-colors">Analytics</a></li>
                  <li><a href="#features" className="hover:text-white transition-colors">Security</a></li>
                  <li><a href="#pricing" className="hover:text-white transition-colors">Integrations</a></li>
                </ul>
              </div>

              {/* Link Column 2: Resources */}
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-neutral-200 mb-3">Resources</h4>
                <ul className="space-y-2 text-xs text-neutral-400">
                  <li><a href="/swagger-ui.html" className="hover:text-white transition-colors">REST API</a></li>
                  <li><a href="#company" className="hover:text-white transition-colors">Documentation</a></li>
                  <li><a href="#company" className="hover:text-white transition-colors">Guides</a></li>
                  <li><a href="#company" className="hover:text-white transition-colors">Support</a></li>
                </ul>
              </div>

              {/* Link Column 3: Company */}
              <div>
                <h4 className="text-xs font-bold uppercase tracking-wider text-neutral-200 mb-3">Company</h4>
                <ul className="space-y-2 text-xs text-neutral-400">
                  <li><a href="#company" className="hover:text-white transition-colors">About</a></li>
                  <li><a href="#company" className="hover:text-white transition-colors">Privacy Policy</a></li>
                  <li><a href="#company" className="hover:text-white transition-colors">Terms of Service</a></li>
                  <li><a href="#company" className="hover:text-white transition-colors">Contact</a></li>
                </ul>
              </div>
            </div>

            <div className="pt-8 flex flex-col sm:flex-row items-center justify-between text-xs text-neutral-500 gap-4">
              <p>© {new Date().getFullYear()} ExpenseTracker Inc. All rights reserved.</p>
              <div className="flex items-center gap-6">
                <span className="hover:text-neutral-300 cursor-pointer">Privacy</span>
                <span className="hover:text-neutral-300 cursor-pointer">Terms</span>
                <span className="hover:text-neutral-300 cursor-pointer">Security</span>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
};

export default LandingPage;