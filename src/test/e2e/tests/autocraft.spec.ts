import { sleep, test } from '@plugwright/runner';

// Real recipe matching (Bukkit.getCraftingRecipe / craftItemResult) and the central crafting ticker.

const OWNER = 'E2EOwner';

function expectContains(text: string, needle: string) {
  if (!text.includes(needle)) throw new Error(`expected ${JSON.stringify(needle)} in ${JSON.stringify(text)}`);
}

async function crafter(server: { execute(cmd: string): Promise<string> }, x: number, name: string, below: string) {
  for (const command of [
    'forceload add 0 0',
    `setblock ${x} -50 2 minecraft:chest{Items:[{Slot:0b,id:"minecraft:coal",count:2},{Slot:1b,id:"minecraft:stick",count:2}]}`,
    `setblock ${x} -51 2 minecraft:crafting_table`,
    `setblock ${x} -52 2 ${below}`,
  ]) {
    await server.execute(command);
  }
  expectContains(await server.execute(`cpptest link autocraft ${OWNER} ${name} ${x} -51 2`), 'cpptest link ok');
  expectContains(
    await server.execute(`cpptest recipe ${OWNER} ${name} -,minecraft:coal,-,-,minecraft:stick,-,-,-,-`),
    'result=TORCHx4',
  );
}

test('a torch AutoCrafter crafts from the chest above into the hopper below', async ({ server }) => {
  await server.execute('cpptest reset');
  await crafter(server, 8, 'torches', 'minecraft:hopper[facing=down]');
  await server.execute('setblock 8 -53 2 minecraft:chest');
  await sleep(2600);
  expectContains(await server.execute('data get block 8 -53 2 Items'), 'minecraft:torch');
});

test('a container below only receives crafts while the table is powered', async ({ server }) => {
  await server.execute('cpptest reset');
  await crafter(server, 12, 'powered', 'minecraft:chest');
  await sleep(1300);
  expectContains(await server.execute('data get block 12 -52 2 Items'), '[]');

  // Power the table. The failed attempts above put the crafter into backoff (1 s, then 2 s, ...); commands don't
  // count as input changes, so wait out the next attempt.
  await server.execute('setblock 13 -51 2 minecraft:redstone_block');
  await sleep(3500);
  expectContains(await server.execute('data get block 12 -52 2 Items'), 'minecraft:torch');
});

test('setting the recipe crafts on the next tick', async ({ server }) => {
  await server.execute('cpptest reset');
  await server.execute('setblock 0 -52 2 minecraft:air');
  await crafter(server, 0, 'instant', 'minecraft:hopper[facing=down]');
  await sleep(400);
  expectContains(await server.execute('data get block 0 -52 2 Items'), 'minecraft:torch');
});

test('a waiting crafter crafts as soon as a hopper delivers its ingredients', async ({ server }) => {
  await server.execute('cpptest reset');
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
  expectContains(await server.execute(`cpptest link autocraft ${OWNER} waiting 4 -51 0`), 'cpptest link ok');
  expectContains(await server.execute(`cpptest recipe ${OWNER} waiting -,minecraft:coal,-,-,minecraft:stick,-,-,-,-`), 'result=TORCHx4');

  // Empty input: the crafter fails and backs off (1 s, 2 s, 4 s...), so after 3 s its next scheduled try is seconds away.
  await sleep(3000);
  expectContains(await server.execute('data get block 4 -52 0 Items'), '[]');

  // A hopper feeds the chest (two transfers, 8 ticks apart); each transfer wakes the crafter.
  await server.execute('setblock 4 -49 0 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:coal",count:1},{Slot:1b,id:"minecraft:stick",count:1}]}');
  await sleep(1500);
  expectContains(await server.execute('data get block 4 -52 0 Items'), 'minecraft:torch');
});

test('a crafter blocked by a full output crafts as soon as the output hopper drains', async ({ server }) => {
  await server.execute('cpptest reset');
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
  expectContains(await server.execute(`cpptest link autocraft ${OWNER} blocked 8 -51 0`), 'cpptest link ok');
  expectContains(await server.execute(`cpptest recipe ${OWNER} blocked -,minecraft:coal,-,-,minecraft:stick,-,-,-,-`), 'result=TORCHx4');
  await sleep(3000);

  // The hopper starts pushing dirt out; its first transfer frees a slot and wakes the crafter.
  await server.execute('setblock 8 -53 0 minecraft:chest');
  await sleep(1000);
  const output = (await server.execute('data get block 8 -52 0 Items')) + (await server.execute('data get block 8 -53 0 Items'));
  expectContains(output, 'minecraft:torch');
});
