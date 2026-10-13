import { test } from '@plugwright/runner';
import { expectContains, expectSeenWithin, expectWithinTicks, timed, type Server, waitUntilTick } from './support.js';

// Real recipe matching (Bukkit.getCraftingRecipe / craftItemResult) and the central crafting ticker.

const OWNER = 'E2EOwner';
const TORCH = '-,minecraft:coal,-,-,minecraft:stick,-,-,-,-';

// A crafter that keeps failing after its recipe is set at tick R tries again at about R+1, R+21, R+61, R+141, then every 100 ticks
// (autocraft.tick-interval 20, doubling up to 100).
const SETTLED = 65;
const NEXT_TRY = 141;
const LONGEST_BACKOFF = 100;

/** Links the table at (x, -51, z) as a torch AutoCrafter and watches the block below it. Returns the tick the recipe was set on. */
async function torchCrafter(server: Server, name: string, x: number, z: number): Promise<number> {
  expectContains(await server.execute(`cpptest link autocraft ${OWNER} ${name} ${x} -51 ${z}`), 'cpptest link ok');
  expectContains(await server.execute(`cpptest watch ${x} -52 ${z} minecraft:torch`), 'cpptest watch ok');
  const { output, tick } = await timed(server, `cpptest recipe ${OWNER} ${name} ${TORCH}`);
  expectContains(output, 'result=TORCHx4');
  return tick;
}

async function crafter(server: Server, x: number, name: string, below: string): Promise<number> {
  for (const command of [
    'forceload add 0 0',
    `setblock ${x} -50 2 minecraft:chest{Items:[{Slot:0b,id:"minecraft:coal",count:2},{Slot:1b,id:"minecraft:stick",count:2}]}`,
    `setblock ${x} -51 2 minecraft:crafting_table`,
    `setblock ${x} -52 2 ${below}`,
  ]) {
    await server.execute(command);
  }
  return torchCrafter(server, name, x, 2);
}

test('a torch AutoCrafter crafts from the chest above into the hopper below', async ({ server }) => {
  await server.execute('cpptest reset');
  await crafter(server, 8, 'torches', 'minecraft:hopper[facing=down]');
  await server.execute('setblock 8 -53 2 minecraft:chest');
  await expectWithinTicks(server, () => server.execute('data get block 8 -53 2 Items'), 'minecraft:torch', 52);
});

test('a container below only receives crafts while the table is powered', async ({ server }) => {
  await server.execute('cpptest reset');
  const recipe = await crafter(server, 12, 'powered', 'minecraft:chest');
  await waitUntilTick(server, recipe + 26);
  expectContains(await server.execute('data get block 12 -52 2 Items'), '[]');

  // Power the table. The failed attempts above put the crafter into backoff, and commands don't count as input changes, so wait out
  // the next attempt, however far away it is.
  await server.execute('setblock 13 -51 2 minecraft:redstone_block');
  await expectWithinTicks(server, () => server.execute('data get block 12 -52 2 Items'), 'minecraft:torch', LONGEST_BACKOFF + 10);
});

test('setting the recipe crafts on the next tick', async ({ server }) => {
  await server.execute('cpptest reset');
  await server.execute('setblock 0 -52 2 minecraft:air');
  const recipe = await crafter(server, 0, 'instant', 'minecraft:hopper[facing=down]');
  await expectSeenWithin(server, '0 -52 2', recipe, 8);
});

/**
 * Wakes a backed-off crafter by running {@code command} between its tries, and expects its output at {@code at} within {@code ticks}:
 * well before the next scheduled try, so only the wake-up explains it.
 */
async function wake(server: Server, recipe: number, command: string, at: string, ticks: number) {
  const { tick } = await timed(server, command);
  if (tick + ticks >= recipe + NEXT_TRY) throw new Error(`woke at tick ${tick}, too close to the next scheduled try at ${recipe + NEXT_TRY}`);
  await expectSeenWithin(server, at, tick, ticks);
}

/** A crafter with an empty input chest. Returns how to wake it: a hopper feeding the chest (two transfers, 8 ticks apart). */
async function waitingForInput(server: Server) {
  for (const command of [
    'forceload add 0 0',
    'setblock 4 -49 0 minecraft:air',
    'setblock 4 -50 0 minecraft:air',
    'setblock 4 -52 0 minecraft:air',
    'setblock 4 -50 0 minecraft:chest',
    'setblock 4 -51 0 minecraft:crafting_table',
    'setblock 4 -52 0 minecraft:hopper[facing=down]',
  ]) {
    await server.execute(command);
  }
  return async () => {
    const recipe = await torchCrafter(server, 'waiting', 4, 0);
    await waitUntilTick(server, recipe + SETTLED);
    expectContains(await server.execute('data get block 4 -52 0 Items'), '[]');
    const feed = 'setblock 4 -49 0 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal",count:1},{Slot:1b,id:"minecraft:stick",count:1}]}';
    await wake(server, recipe, feed, '4 -52 0', 30);
  };
}

/** A crafter whose output hopper is full. Returns how to wake it: a chest below, so the hopper's first transfer frees a slot. */
async function blockedByOutput(server: Server) {
  const dirt = [0, 1, 2, 3, 4].map(slot => `{Slot:${slot}b,id:"minecraft:dirt",count:1}`).join(',');
  for (const command of [
    'forceload add 0 0',
    'setblock 8 -50 0 minecraft:air',
    'setblock 8 -52 0 minecraft:air',
    'setblock 8 -53 0 minecraft:air',
    'setblock 8 -50 0 minecraft:chest{Items:[{Slot:0b,id:"minecraft:coal",count:1},{Slot:1b,id:"minecraft:stick",count:1}]}',
    'setblock 8 -51 0 minecraft:crafting_table',
    // Five single dirt: no free slot for torches, and nothing below for the hopper to push into yet.
    `setblock 8 -52 0 minecraft:hopper[facing=down]{Items:[${dirt}]}`,
  ]) {
    await server.execute(command);
  }
  return async () => {
    const recipe = await torchCrafter(server, 'blocked', 8, 0);
    await waitUntilTick(server, recipe + SETTLED);
    await wake(server, recipe, 'setblock 8 -53 0 minecraft:chest', '8 -52 0', 20);
  };
}

test('a backed-off crafter crafts as soon as its ingredients arrive or its output drains', async ({ server }) => {
  await server.execute('cpptest reset');
  const wakeUps = [await waitingForInput(server), await blockedByOutput(server)];
  // Building the second fixture after starting the first clock can miss the wake-up window at 1000 TPS.
  await Promise.all(wakeUps.map(wakeUp => wakeUp()));
});
