import { useEffect, useState } from 'react';

interface CopyState {
  copied: boolean;
}

export function useCopyToClipboard(resetMs = 2000): [CopyState, (text: string) => Promise<boolean>] {
  const [state, setState] = useState<CopyState>({ copied: false });

  async function copy(text: string): Promise<boolean> {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
      } else {
        const el = document.createElement('textarea');
        el.value = text;
        el.style.position = 'fixed';
        el.style.opacity = '0';
        document.body.appendChild(el);
        el.select();
        document.execCommand('copy');
        document.body.removeChild(el);
      }
      setState({ copied: true });
      return true;
    } catch {
      setState({ copied: false });
      return false;
    }
  }

  useEffect(() => {
    if (!state.copied) return;
    const t = setTimeout(() => setState({ copied: false }), resetMs);
    return () => clearTimeout(t);
  }, [state.copied, resetMs]);

  return [state, copy];
}
