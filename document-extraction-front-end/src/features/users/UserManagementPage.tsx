import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Loader2,
  ShieldCheck,
  UserCheck,
  UserPlus,
  Users,
  UserX,
  KeyRound,
} from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { useAuth } from '@/features/auth/AuthProvider';
import { normalizeError } from '@/api/errors';
import { createUser, listUsers, resetUserPassword, updateUser } from '@/api/userService';
import type { CreateUserInput, ManagedUser, UserRole } from '@/api/types';
import { cn } from '@/lib/utils';

const ROLES: UserRole[] = ['ADMIN', 'REVIEWER', 'USER'];

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

const EMPTY_FORM: CreateUserInput = {
  displayName: '',
  email: '',
  password: '',
  role: 'USER',
};

export function UserManagementPage() {
  const { user } = useAuth();
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }
  return <UserManagementContent currentUserId={user.id} />;
}

function UserManagementContent({ currentUserId }: { currentUserId: string }) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState<CreateUserInput>(EMPTY_FORM);
  const [resetTarget, setResetTarget] = useState<ManagedUser | null>(null);
  const [resetPassword, setResetPassword] = useState('');

  const usersQuery = useQuery({
    queryKey: ['users'],
    queryFn: listUsers,
  });

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      toast.success(t('users.created'));
      setForm(EMPTY_FORM);
      setDialogOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error) => {
      const normalized = normalizeError(error);
      toast.error(
        normalized.isNetwork
          ? t('errors.network')
          : normalized.detail || t('users.createFailed'),
      );
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: string; input: { role?: UserRole; enabled?: boolean } }) =>
      updateUser(id, input),
    onSuccess: () => {
      toast.success(t('users.updated'));
      void queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (error) => {
      const normalized = normalizeError(error);
      toast.error(
        normalized.isNetwork
          ? t('errors.network')
          : normalized.detail || t('users.updateFailed'),
      );
    },
  });

  const resetMutation = useMutation({
    mutationFn: ({ id, password }: { id: string; password: string }) =>
      resetUserPassword(id, password),
    onSuccess: () => {
      toast.success(t('users.resetSuccess'));
      setResetTarget(null);
      setResetPassword('');
    },
    onError: (error) => {
      const normalized = normalizeError(error);
      toast.error(
        normalized.isNetwork
          ? t('errors.network')
          : normalized.detail || t('users.resetFailed'),
      );
    },
  });

  function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createMutation.mutate({
      ...form,
      displayName: form.displayName.trim(),
      email: form.email.trim(),
    });
  }

  const users = usersQuery.data ?? [];
  const loadError = usersQuery.isError ? normalizeError(usersQuery.error) : null;

  return (
    <div className="mx-auto flex min-h-0 w-full max-w-6xl flex-1 flex-col gap-5 overflow-y-auto p-4 md:p-6 lg:p-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-2 inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/[0.07] px-3 py-1 text-[11px] font-semibold text-primary">
            <ShieldCheck className="h-3.5 w-3.5" />
            {t('auth.role.ADMIN')}
          </div>
          <h1 className="text-xl font-semibold tracking-tight">{t('users.title')}</h1>
          <p className="mt-1 max-w-2xl text-sm text-muted-foreground">{t('users.subtitle')}</p>
        </div>
        <Button onClick={() => setDialogOpen(true)} className="w-full rounded-xl sm:w-auto">
          <UserPlus className="me-2 h-4 w-4" />
          {t('users.create')}
        </Button>
      </div>

      <Card className="overflow-hidden rounded-2xl border-border/70 bg-card/80">
        <CardContent className="p-0">
          {usersQuery.isLoading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              {t('users.loading')}
            </div>
          ) : loadError ? (
            <div className="px-5 py-12 text-center">
              <p className="text-sm font-medium text-destructive">
                {loadError.isNetwork ? t('errors.network') : loadError.detail}
              </p>
              <Button
                variant="outline"
                size="sm"
                className="mt-4"
                onClick={() => void usersQuery.refetch()}
              >
                {t('errors.retry')}
              </Button>
            </div>
          ) : users.length === 0 ? (
            <div className="flex flex-col items-center gap-3 px-5 py-16 text-center">
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-primary/10 text-primary">
                <Users className="h-5 w-5" />
              </span>
              <div>
                <p className="text-sm font-semibold">{t('users.emptyTitle')}</p>
                <p className="mt-1 text-xs text-muted-foreground">{t('users.emptyHint')}</p>
              </div>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('users.name')}</TableHead>
                  <TableHead className="w-[170px]">{t('users.role')}</TableHead>
                  <TableHead className="hidden w-[120px] sm:table-cell">
                    {t('users.status')}
                  </TableHead>
                  <TableHead className="w-[64px] text-end">{t('users.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.map((managedUser) => {
                  const isSelf = managedUser.id === currentUserId;
                  return (
                    <TableRow
                      key={managedUser.id}
                      className={cn(!managedUser.enabled && 'opacity-60')}
                    >
                      <TableCell className="py-3">
                        <div className="flex min-w-0 items-center gap-3">
                          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-primary/10 text-[10px] font-semibold text-primary">
                            {initials(managedUser.displayName)}
                          </span>
                          <div className="min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="truncate text-sm font-medium">
                                {managedUser.displayName}
                              </span>
                              {isSelf && (
                                <span className="rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                                  {t('users.you')}
                                </span>
                              )}
                            </div>
                            <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                              {managedUser.email}
                            </span>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <Select
                          value={managedUser.role}
                          disabled={isSelf || updateMutation.isPending}
                          onValueChange={(role) =>
                            updateMutation.mutate({
                              id: managedUser.id,
                              input: { role: role as UserRole },
                            })
                          }
                        >
                          <SelectTrigger
                            className="h-8 w-[150px] rounded-lg text-xs"
                            aria-label={`${t('users.role')} ${managedUser.displayName}`}
                          >
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {ROLES.map((role) => (
                              <SelectItem key={role} value={role}>
                                {t(`auth.role.${role}`)}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </TableCell>
                      <TableCell className="hidden sm:table-cell">
                        <Badge
                          variant={managedUser.enabled ? 'secondary' : 'outline'}
                          className={cn(
                            'rounded-full text-[10px]',
                            managedUser.enabled && 'text-success',
                          )}
                        >
                          {t(managedUser.enabled ? 'users.active' : 'users.disabled')}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-end">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8 rounded-lg text-muted-foreground hover:text-primary"
                            disabled={isSelf || resetMutation.isPending}
                            aria-label={`${t('users.resetPassword')} ${managedUser.displayName}`}
                            title={t('users.resetPassword')}
                            onClick={() => setResetTarget(managedUser)}
                          >
                            <KeyRound className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8 rounded-lg text-muted-foreground hover:text-foreground"
                            disabled={isSelf || updateMutation.isPending}
                            aria-label={`${t(
                              managedUser.enabled ? 'users.disable' : 'users.enable',
                            )} ${managedUser.displayName}`}
                            title={t(
                              managedUser.enabled ? 'users.disable' : 'users.enable',
                            )}
                            onClick={() =>
                              updateMutation.mutate({
                                id: managedUser.id,
                                input: { enabled: !managedUser.enabled },
                              })
                            }
                          >
                            {managedUser.enabled ? (
                              <UserX className="h-4 w-4" />
                            ) : (
                              <UserCheck className="h-4 w-4" />
                            )}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open);
          if (!open) setForm(EMPTY_FORM);
        }}
      >
        <DialogContent className="rounded-2xl sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{t('users.createTitle')}</DialogTitle>
            <DialogDescription>{t('users.createDescription')}</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="user-display-name">{t('users.displayName')}</Label>
                <Input
                  id="user-display-name"
                  value={form.displayName}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, displayName: event.target.value }))
                  }
                  autoComplete="name"
                  maxLength={160}
                  required
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="user-email">{t('users.email')}</Label>
                <Input
                  id="user-email"
                  type="email"
                  value={form.email}
                  onChange={(event) =>
                    setForm((current) => ({ ...current, email: event.target.value }))
                  }
                  autoComplete="email"
                  maxLength={320}
                  required
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="user-password">{t('users.password')}</Label>
              <Input
                id="user-password"
                type="password"
                value={form.password}
                onChange={(event) =>
                  setForm((current) => ({ ...current, password: event.target.value }))
                }
                autoComplete="new-password"
                minLength={12}
                maxLength={128}
                required
              />
              <p className="text-[11px] text-muted-foreground">{t('users.passwordHint')}</p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="user-role">{t('users.role')}</Label>
              <Select
                value={form.role}
                onValueChange={(role) =>
                  setForm((current) => ({ ...current, role: role as UserRole }))
                }
              >
                <SelectTrigger id="user-role" className="rounded-lg">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ROLES.map((role) => (
                    <SelectItem key={role} value={role}>
                      {t(`auth.role.${role}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-[11px] leading-relaxed text-muted-foreground">
                {t('users.roleHint')}
              </p>
            </div>

            <DialogFooter className="gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setDialogOpen(false);
                  setForm(EMPTY_FORM);
                }}
              >
                {t('users.cancel')}
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending && <Loader2 className="me-2 h-4 w-4 animate-spin" />}
                {t('users.submit')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog
        open={resetTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setResetTarget(null);
            setResetPassword('');
          }
        }}
      >
        <DialogContent className="rounded-2xl sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{t('users.resetTitle')}</DialogTitle>
            <DialogDescription>
              {t('users.resetDescription', { name: resetTarget?.displayName ?? '' })}
            </DialogDescription>
          </DialogHeader>

          <form
            onSubmit={(event: FormEvent<HTMLFormElement>) => {
              event.preventDefault();
              if (resetTarget) {
                resetMutation.mutate({ id: resetTarget.id, password: resetPassword });
              }
            }}
            className="space-y-4"
          >
            <div className="space-y-1.5">
              <Label htmlFor="reset-password">{t('users.resetPassword')}</Label>
              <Input
                id="reset-password"
                type="password"
                value={resetPassword}
                onChange={(event) => setResetPassword(event.target.value)}
                autoComplete="new-password"
                minLength={12}
                maxLength={128}
                required
              />
              <p className="text-[11px] text-muted-foreground">{t('users.passwordHint')}</p>
            </div>
            <DialogFooter className="gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setResetTarget(null);
                  setResetPassword('');
                }}
              >
                {t('users.cancel')}
              </Button>
              <Button type="submit" disabled={resetMutation.isPending}>
                {resetMutation.isPending && <Loader2 className="me-2 h-4 w-4 animate-spin" />}
                {t('users.resetSubmit')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
