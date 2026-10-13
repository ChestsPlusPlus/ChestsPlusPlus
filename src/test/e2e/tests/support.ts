import { sleep } from '@plugwright/runner';

// Shared by the specs. Not a spec itself: Plugwright only runs *.spec.js.
//
// The suite runs at a raised tick rate (the tick-rate plugin), so waits and deadlines count game ticks, never milliseconds: hoppers,
// golems and the crafter backoff all count ticks, and a slower server then only makes the run slower, not the checks looser.

export type Server = { execute(cmd: string): Promise<string> };

/** How often waits poll the server. A round trip is a tick or two even at high rates, so this bounds how far a wait overshoots. */
const POLL_MS = 10;

export function expectContains(text: string, needle: string) {
  if (!text.includes(needle)) throw new Error(`expected ${JSON.stringify(needle)} in ${JSON.stringify(text)}`);
}

function tickIn(output: string, pattern: RegExp): number {
  const match = output.match(pattern);
  if (!match) throw new Error(`no game tick in ${JSON.stringify(output)}`);
  return Number(match[1]);
}

export async function gameTime(server: Server): Promise<number> {
  return tickIn(await server.execute('time query gametime'), /(\d+)/);
}

export async function waitUntilTick(server: Server, tick: number) {
  while ((await gameTime(server)) < tick) await sleep(POLL_MS);
}

/** Waits for the server to run at least {@code ticks} more ticks. */
export async function waitTicks(server: Server, ticks: number) {
  await waitUntilTick(server, (await gameTime(server)) + ticks);
}

/** Runs {@code command} through the harness, which reports the tick it ran on: a separate query would land ticks later. */
export async function timed(server: Server, command: string): Promise<{ output: string; tick: number }> {
  const output = await server.execute(`cpptest timed ${command}`);
  return { output, tick: tickIn(output, /cpptest timed tick=(\d+)/) };
}

/** Polls {@code probe} until its output contains {@code needle}, failing if the server runs {@code ticks} ticks first. */
export async function expectWithinTicks(server: Server, probe: () => Promise<string>, needle: string, ticks: number): Promise<string> {
  const deadline = (await gameTime(server)) + ticks;
  for (;;) {
    const now = await gameTime(server);
    const output = await probe();
    if (output.includes(needle) || now >= deadline) {
      expectContains(output, needle);
      return output;
    }
    await sleep(POLL_MS);
  }
}

/**
 * Waits for the harness watch on ({@code at}) to see its item, failing unless that was within {@code ticks} ticks of {@code from}. The
 * harness records the tick on the server, so this holds exactly at any tick rate.
 */
export async function expectSeenWithin(server: Server, at: string, from: number, ticks: number) {
  for (;;) {
    const expired = (await gameTime(server)) > from + ticks;
    const seen = await server.execute(`cpptest seen ${at}`);
    if (!seen.includes('pending')) {
      const after = tickIn(seen, /tick=(\d+)/) - from;
      if (after > ticks) throw new Error(`${at} got its item ${after} ticks after tick ${from}, expected within ${ticks}`);
      return;
    }
    if (expired) throw new Error(`${at} got nothing within ${ticks} ticks of tick ${from}`);
    await sleep(POLL_MS);
  }
}
