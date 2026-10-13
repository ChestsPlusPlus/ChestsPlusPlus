import { test } from '@plugwright/runner';
import { expectContains, expectWithinTicks } from './support.js';

// A filtered hopper on a real 26.3 server. The first source slot is rejected, so this also checks the
// stall avoidance (the hopper must still move the allowed item).

test('an allow filter only lets the allowed item through, even behind a rejected slot', async ({ server }) => {
  for (const command of ['forceload add 0 0', 'setblock 4 -52 12 minecraft:chest', 'setblock 4 -51 12 minecraft:hopper[facing=down]']) {
    await server.execute(command);
  }
  // Filtered before there is anything to pull: at a high tick rate the hopper moves items between two commands.
  expectContains(await server.execute('cpptest filter 4 -51 12 allow minecraft:stone'), 'indexed=true');
  await server.execute('setblock 4 -50 12 minecraft:chest{Items:[{Slot:0b,id:"minecraft:dirt",count:3},{Slot:1b,id:"minecraft:stone",count:2}]}');
  // Dirt is in the first slot, so if the filter let it through it would arrive before the stone.
  const sink = await expectWithinTicks(server, () => server.execute('data get block 4 -52 12 Items'), 'minecraft:stone', 50);
  if (sink.includes('minecraft:dirt')) throw new Error(`dirt passed the filter: ${sink}`);
  expectContains(await server.execute('data get block 4 -50 12 Items'), 'minecraft:dirt');
});
