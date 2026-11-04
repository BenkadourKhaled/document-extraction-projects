import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  ArrowRight,
  FileSearch,
  Languages,
  Loader2,
  LockKeyhole,
  ShieldCheck,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { LanguageSwitcher } from '@/features/extraction/components/LanguageSwitcher';
import { ThemeToggle } from '@/features/extraction/components/ThemeToggle';
import { useAuth } from '@/features/auth/AuthProvider';
import { normalizeError } from '@/api/errors';
import type { ApiError } from '@/api/types';

export function LoginPage() {
  const { t } = useTranslation();
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const from = (location.state as { from?: string } | null)?.from ?? '/';

  if (isAuthenticated) {
    return <Navigate to={from} replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(normalizeError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="relative flex min-h-dvh flex-col overflow-hidden bg-background hero-glow">
      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between px-5 py-5 md:px-8">
        <div className="flex items-center gap-2.5">
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-primary to-sky-500 text-primary-foreground shadow-lg shadow-primary/20 ring-1 ring-white/20">
            <FileSearch className="h-[18px] w-[18px]" />
          </span>
          <span className="flex flex-col leading-tight">
            <span className="text-sm font-semibold tracking-tight">{t('app.title')}</span>
            <span className="text-[10px] font-medium uppercase tracking-[0.18em] text-muted-foreground">
              {t('app.tagline')}
            </span>
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
      </header>

      <main className="relative z-10 flex flex-1 items-center justify-center px-5 pb-12 pt-4">
        <div className="grid w-full max-w-5xl overflow-hidden rounded-[2rem] border border-border/80 bg-card/80 shadow-[0_30px_120px_-40px_hsl(var(--primary)/0.45)] backdrop-blur-xl lg:grid-cols-[1.05fr_0.95fr]">
          <section className="relative hidden overflow-hidden border-e border-border/70 bg-gradient-to-br from-primary/10 via-background to-sky-500/10 p-10 lg:flex lg:flex-col lg:justify-between">
            <div className="pointer-events-none absolute -end-20 -top-20 h-56 w-56 rounded-full bg-primary/15 blur-3xl" />
            <div className="relative">
              <span className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/[0.07] px-3 py-1 text-[10px] font-semibold uppercase tracking-[0.18em] text-primary">
                <ShieldCheck className="h-3.5 w-3.5" />
                {t('auth.badge')}
              </span>
              <h1 className="mt-6 max-w-md text-4xl font-semibold leading-[1.08] tracking-[-0.04em]">
                {t('auth.heroTitle')}
              </h1>
              <p className="mt-4 max-w-sm text-sm leading-relaxed text-muted-foreground">
                {t('auth.heroDescription')}
              </p>
            </div>

            <div className="relative space-y-3">
              {[
                { icon: LockKeyhole, key: 'private' },
                { icon: Languages, key: 'languages' },
                { icon: ShieldCheck, key: 'audit' },
              ].map((item) => {
                const Icon = item.icon;
                return (
                  <div
                    key={item.key}
                    className="flex items-start gap-3 rounded-2xl border border-border/60 bg-background/50 p-3.5"
                  >
                    <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary">
                      <Icon className="h-4 w-4" />
                    </span>
                    <div>
                      <p className="text-[12.5px] font-semibold">{t(`auth.features.${item.key}.title`)}</p>
                      <p className="mt-0.5 text-[11px] leading-relaxed text-muted-foreground">
                        {t(`auth.features.${item.key}.description`)}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          <section className="flex flex-col justify-center p-6 sm:p-10">
            <div className="mx-auto w-full max-w-sm">
              <div className="mb-7">
                <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-primary">
                  {t('auth.eyebrow')}
                </p>
                <h2 className="mt-2 text-2xl font-semibold tracking-tight">{t('auth.title')}</h2>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  {t('auth.subtitle')}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="email">{t('auth.email')}</Label>
                  <Input
                    id="email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    required
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder={t('auth.emailPlaceholder')}
                    className="h-11 rounded-xl"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="password">{t('auth.password')}</Label>
                  <Input
                    id="password"
                    name="password"
                    type="password"
                    autoComplete="current-password"
                    required
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder={t('auth.passwordPlaceholder')}
                    className="h-11 rounded-xl"
                  />
                </div>

                {error && (
                  <p
                    role="alert"
                    className="rounded-xl border border-destructive/30 bg-destructive/5 px-3 py-2 text-xs leading-relaxed text-destructive"
                  >
                    {error.detail}
                  </p>
                )}

                <Button
                  type="submit"
                  size="lg"
                  disabled={submitting}
                  className="group h-11 w-full rounded-xl bg-gradient-to-r from-primary via-primary to-sky-500 text-sm font-semibold shadow-lg shadow-primary/20"
                >
                  {submitting ? (
                    <Loader2 className="me-2 h-4 w-4 animate-spin" />
                  ) : (
                    <LockKeyhole className="me-2 h-4 w-4" />
                  )}
                  {submitting ? t('auth.signingIn') : t('auth.signIn')}
                  {!submitting && (
                    <ArrowRight className="ms-2 h-4 w-4 transition-transform group-hover:translate-x-0.5 rtl:rotate-180" />
                  )}
                </Button>
              </form>

              <p className="mt-6 text-center text-[11px] leading-relaxed text-muted-foreground">
                {t('auth.noSelfSignup')}
              </p>
            </div>
          </section>
        </div>
      </main>
    </div>
  );
}
