import { sleep, test } from '@plugwright/runner';

// Phase 5 E2E: real recipe matching (Bukkit.getCraftingRecipe / craftItemResult) and the central crafting ticker.

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
