import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation } from '@tanstack/react-query';
import { KeyRound, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
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
import { normalizeError } from '@/api/errors';
import { changePassword } from '@/api/userService';
import { clearToken } from '@/utils/authStorage';

interface ChangePasswordDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ChangePasswordDialog({ open, onOpenChange }: ChangePasswordDialogProps) {
  const { t } = useTranslation();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const mutation = useMutation({
    mutationFn: changePassword,
    onSuccess: () => {
      toast.success(t('auth.passwordChange.success'));
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      onOpenChange(false);
      // The backend increments the session version: every existing JWT,
      // including this one, is now invalid. Reset the local session and let
      // ProtectedRoute redirect to the login screen.
      clearToken();
      window.dispatchEvent(new Event('auth:unauthorized'));
    },
    onError: (error) => {
      const normalized = normalizeError(error);
      toast.error(
        normalized.isNetwork
          ? t('errors.network')
          : normalized.detail || t('auth.passwordChange.failed'),
      );
    },
  });

  function handleOpenChange(next: boolean) {
    if (!next) {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    }
    onOpenChange(next);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      toast.error(t('auth.passwordChange.mismatch'));
      return;
    }
    mutation.mutate({ currentPassword, newPassword });
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <KeyRound className="h-4 w-4 text-primary" />
            {t('auth.passwordChange.title')}
          </DialogTitle>
          <DialogDescription>{t('auth.passwordChange.description')}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="current-password">{t('auth.passwordChange.current')}</Label>
            <Input
              id="current-password"
              type="password"
              autoComplete="current-password"
              maxLength={128}
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="new-password">{t('auth.passwordChange.new')}</Label>
            <Input
              id="new-password"
              type="password"
              autoComplete="new-password"
              minLength={12}
              maxLength={128}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
            />
            <p className="text-[11px] text-muted-foreground">{t('auth.passwordChange.hint')}</p>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="confirm-password">{t('auth.passwordChange.confirm')}</Label>
            <Input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              minLength={12}
              maxLength={128}
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
            />
          </div>

          <DialogFooter className="gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              {t('auth.passwordChange.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="me-2 h-4 w-4 animate-spin" />}
              {t('auth.passwordChange.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

