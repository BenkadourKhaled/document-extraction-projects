import { describe, expect, it } from 'vitest';
import { findMatches, buildSegments } from '@/utils/search';

describe('search utils', () => {
  it('returns no matches for empty query', () => {
    expect(findMatches('hello world', '')).toEqual([]);
  });

  it('finds case-insensitive matches', () => {
    const matches = findMatches('Hello hello HELLO', 'hello');
    expect(matches).toHaveLength(3);
    expect(matches[0]).toEqual({ start: 0, end: 5 });
  });

  it('builds segments around matches', () => {
    const matches = findMatches('aa bb aa', 'aa');
    const segs = buildSegments('aa bb aa', matches);
    expect(segs).toEqual([
      { text: 'aa', match: true },
      { text: ' bb ', match: false },
      { text: 'aa', match: true },
    ]);
  });

  it('handles no matches in segments', () => {
    expect(buildSegments('abc', [])).toEqual([{ text: 'abc', match: false }]);
  });
});
