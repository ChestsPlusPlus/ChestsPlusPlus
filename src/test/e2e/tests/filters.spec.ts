import { sleep, test } from '@plugwright/runner';

// Phase 4 E2E: a filtered hopper on a real 26.3 server. The first source slot is rejected, so this also checks the
// stall avoidance from spike S1b (the hopper must still move the allowed item).

function expectContains(text: string, needle: string) {
  if (!text.includes(needle)) throw new Error(`expected ${JSON.stringify(needle)} in ${JSON.stringify(text)}`);
}

test('an allow filter only lets the allowed item through, even behind a rejected slot', async ({ server }) => {
  for (const command of [
    'forceload add 0 0',
    'setblock 4 -52 12 minecraft:chest',
    'setblock 4 -51 12 minecraft:hopper[facing=down]',
    'setblock 4 -50 12 minecraft:chest{Items:[{Slot:0b,id:"minecraft:dirt",count:3},{Slot:1b,id:"minecraft:stone",count:2}]}',
  ]) {
    await server.execute(command);
  }
  expectContains(await server.execute('cpptest filter 4 -51 12 allow minecraft:stone'), 'indexed=true');
  await sleep(2500);

  const sink = await server.execute('data get block 4 -52 12 Items');
  expectContains(sink, 'minecraft:stone');
  if (sink.includes('minecraft:dirt')) throw new Error(`dirt passed the filter: ${sink}`);
  expectContains(await server.execute('data get block 4 -50 12 Items'), 'minecraft:dirt');
});
