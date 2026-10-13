import { sleep } from '@plugwright/runner';

// Shared by the specs. Not a spec itself: Plugwright only runs *.spec.js.

export type Server = { execute(cmd: string): Promise<string> };

export function expectContains(text: string, needle: string) {
  if (!text.includes(needle)) throw new Error(`expected ${JSON.stringify(needle)} in ${JSON.stringify(text)}`);
}

/**
 * Polls {@code probe} until its output contains {@code needle}, failing if it still doesn't after {@code withinMs}. Returns the
 * last output. The last poll lands on the deadline, so a timing assertion is no looser than a sleep of the same length.
 */
export async function expectEventually(probe: () => Promise<string>, needle: string, withinMs: number): Promise<string> {
  const deadline = Date.now() + withinMs;
  let output = await probe();
  while (!output.includes(needle) && Date.now() < deadline) {
    await sleep(Math.min(100, deadline - Date.now()));
    output = await probe();
  }
  expectContains(output, needle);
  return output;
}
