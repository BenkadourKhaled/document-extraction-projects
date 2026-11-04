import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  ChevronDown,
  FileSearch,
  KeyRound,
  LogOut,
  Plus,
  ShieldCheck,
  UserRound,
  Users,
} from 'lucide-react';
import { LanguageSwitcher } from '@/features/extraction/components/LanguageSwitcher';
import { ThemeToggle } from '@/features/extraction/components/ThemeToggle';
import { ChangePasswordDialog } from '@/features/auth/ChangePasswordDialog';
import { ProfileDialog } from '@/features/auth/ProfileDialog';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuth } from '@/features/auth/AuthProvider';

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

export function Header() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="relative z-30 shrink-0 border-b border-border/70 bg-background/80 backdrop-blur-xl">
      <div className="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-primary/40 to-transparent" />
      <div className="mx-auto flex h-16 max-w-[1600px] items-center justify-between gap-3 px-4 md:px-6 lg:px-8">
        <Link to="/" className="group flex min-w-0 items-center gap-2.5 font-semibold">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-primary to-sky-500 text-primary-foreground shadow-lg shadow-primary/20 ring-1 ring-white/20 transition-transform group-hover:scale-105">
            <FileSearch className="h-[18px] w-[18px]" />
          </span>
          <span className="flex min-w-0 flex-col leading-tight">
            <span className="hidden truncate text-[15px] tracking-tight sm:inline">
              {t('app.title')}
            </span>
            <span className="hidden text-[10px] font-medium uppercase tracking-[0.18em] text-muted-foreground sm:block">
              {user?.organizationName ?? t('app.tagline')}
            </span>
          </span>
        </Link>

        <nav className="flex shrink-0 items-center gap-1.5">
          <Button asChild variant="ghost" size="sm" className="hidden rounded-lg sm:inline-flex">
            <Link to="/">
              <Plus className="me-1.5 h-4 w-4" />
              {t('nav.new')}
            </Link>
          </Button>
          <span className="mx-1 hidden h-5 w-px bg-border sm:block" />
          <LanguageSwitcher />
          <ThemeToggle />

          {user && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="sm"
                  className="ms-1 h-9 gap-2 rounded-xl px-1.5 sm:px-2"
                  aria-label={t('auth.account')}
                >
                  <span className="grid h-7 w-7 place-items-center rounded-lg bg-primary/10 text-[10px] font-semibold text-primary">
                    {initials(user.displayName) || <UserRound className="h-3.5 w-3.5" />}
                  </span>
                  <span className="hidden max-w-[120px] truncate text-[12px] font-medium md:inline">
                    {user.displayName}
                  </span>
                  <ChevronDown className="hidden h-3.5 w-3.5 text-muted-foreground md:block" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-64 rounded-xl">
                <DropdownMenuLabel className="font-normal">
                  <span className="block truncate text-sm font-semibold">{user.displayName}</span>
                  <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                    {user.email}
                  </span>
                  <span className="mt-2 inline-flex items-center gap-1 rounded-full border border-primary/20 bg-primary/[0.07] px-2 py-0.5 text-[10px] font-semibold text-primary">
                    <ShieldCheck className="h-3 w-3" />
                    {t(`auth.role.${user.role}`)}
                  </span>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                {user.role === 'ADMIN' && (
                  <DropdownMenuItem asChild>
                    <Link to="/admin/users">
                      <Users className="me-2 h-4 w-4" />
                      {t('nav.users')}
                    </Link>
                  </DropdownMenuItem>
                )}
                <DropdownMenuItem
                  onSelect={(event) => {
                    event.preventDefault();
                    setProfileOpen(true);
                  }}
                >
                  <UserRound className="me-2 h-4 w-4" />
                  {t('auth.profile.menu')}
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={(event) => {
                    event.preventDefault();
                    setPasswordOpen(true);
                  }}
                >
                  <KeyRound className="me-2 h-4 w-4" />
                  {t('auth.changePassword')}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  onSelect={(event) => {
                    event.preventDefault();
                    void handleLogout();
                  }}
                >
                  <LogOut className="me-2 h-4 w-4" />
                  {t('auth.logout')}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </nav>
      </div>

      <ChangePasswordDialog open={passwordOpen} onOpenChange={setPasswordOpen} />
      <ProfileDialog open={profileOpen} onOpenChange={setProfileOpen} />
    </header>
  );
}
