export interface SearchMatch {
  start: number;
  end: number;
}

export function findMatches(text: string, query: string): SearchMatch[] {
  if (!query.trim()) return [];
  const lower = text.toLowerCase();
  const q = query.toLowerCase();
  const matches: SearchMatch[] = [];
  let from = 0;
  let idx = lower.indexOf(q, from);
  while (idx !== -1 && matches.length < 500) {
    matches.push({ start: idx, end: idx + q.length });
    from = idx + q.length;
    idx = lower.indexOf(q, from);
  }
  return matches;
}

export interface HighlightSegment {
  text: string;
  match: boolean;
}

export function buildSegments(text: string, matches: SearchMatch[]): HighlightSegment[] {
  if (matches.length === 0) return [{ text, match: false }];
  const segments: HighlightSegment[] = [];
  let cursor = 0;
  for (const m of matches) {
    if (m.start > cursor) {
      segments.push({ text: text.slice(cursor, m.start), match: false });
    }
    segments.push({ text: text.slice(m.start, m.end), match: true });
    cursor = m.end;
  }
  if (cursor < text.length) {
    segments.push({ text: text.slice(cursor), match: false });
  }
  return segments;
}
