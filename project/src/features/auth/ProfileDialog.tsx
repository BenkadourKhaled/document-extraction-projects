import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Loader2, UserRound } from 'lucide-react';
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
import { updateProfile } from '@/api/authService';
import type { UpdateProfileInput } from '@/api/types';
import { useAuth } from '@/features/auth/AuthProvider';

interface ProfileDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function ProfileDialog({ open, onOpenChange }: ProfileDialogProps) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<UpdateProfileInput>({
    displayName: user?.displayName ?? '',
    email: user?.email ?? '',
  });

  useEffect(() => {
    if (open && user) {
      setForm({ displayName: user.displayName, email: user.email });
    }
  }, [open, user]);

  const mutation = useMutation({
    mutationFn: updateProfile,
    onSuccess: (updated) => {
      queryClient.setQueryData(['auth', 'me'], updated);
      toast.success(t('auth.profile.saved'));
      onOpenChange(false);
    },
    onError: (error) => {
      const normalized = normalizeError(error);
      toast.error(
        normalized.isNetwork
          ? t('errors.network')
          : normalized.detail || t('auth.profile.failed'),
      );
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate({
      displayName: form.displayName.trim(),
      email: form.email.trim(),
    });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-2xl sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <UserRound className="h-4 w-4 text-primary" />
            {t('auth.profile.title')}
          </DialogTitle>
          <DialogDescription>{t('auth.profile.description')}</DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="profile-display-name">{t('auth.profile.displayName')}</Label>
            <Input
              id="profile-display-name"
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
            <Label htmlFor="profile-email">{t('auth.profile.email')}</Label>
            <Input
              id="profile-email"
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

          <DialogFooter className="gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t('auth.profile.cancel')}
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending && <Loader2 className="me-2 h-4 w-4 animate-spin" />}
              {t('auth.profile.submit')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
