import { sleep, test } from '@plugwright/runner';

// A filtered hopper on a real 26.3 server. The first source slot is rejected, so this also checks the
// stall avoidance (the hopper must still move the allowed item).

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

async function filteredMachine(
  server: { execute(command: string): Promise<string> },
  x: number,
  machine: string,
  data: string,
  allowed: string,
  sourceSlot: number,
) {
  for (const command of [
    `forceload add ${x} 64`,
    `setblock ${x} -52 64 minecraft:chest`,
    `setblock ${x} -51 64 minecraft:hopper[facing=down]`,
  ]) await server.execute(command);
  expectContains(await server.execute(`cpptest filter ${x} -51 64 allow minecraft:${allowed}`), 'indexed=true');
  await server.execute(`setblock ${x} -50 64 minecraft:${machine}${data}`);
  await sleep(2500);
  return {
    source: await server.execute(`data get block ${x} -50 64 Items[{Slot:${sourceSlot}b}]`),
    sink: await server.execute(`data get block ${x} -52 64 Items`),
  };
}

for (const [index, machine] of ['furnace', 'blast_furnace', 'smoker'].entries()) {
  test(`stall avoidance leaves ${machine} fuel inside the machine`, async ({ server }) => {
    const data = '{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:5},{Slot:1b,id:"minecraft:coal",count:5},{Slot:2b,id:"minecraft:glass",count:5}]}';
    const result = await filteredMachine(server, 64 + index * 8, machine, data, 'coal', 1);
    if (result.sink.includes('minecraft:coal')) throw new Error(`fuel was extracted: ${result.sink}`);
    expectContains(result.source, 'minecraft:coal');
  });
}

for (const [index, bucket] of ['bucket', 'water_bucket'].entries()) {
  test(`stall avoidance can drain a returned furnace ${bucket}`, async ({ server }) => {
    const data = `{Items:[{Slot:1b,id:"minecraft:${bucket}",count:1},{Slot:2b,id:"minecraft:glass",count:5}]}`;
    const result = await filteredMachine(server, 88 + index * 8, 'furnace', data, bucket, 1);
    expectContains(result.sink, `minecraft:${bucket}`);
    if (result.source.includes(`minecraft:${bucket}`)) throw new Error(`bucket was left behind: ${result.source}`);
  });
}

test('stall avoidance leaves brewing fuel inside the stand', async ({ server }) => {
  const data = '{Fuel:20b,Items:[{Slot:0b,id:"minecraft:potion",count:1},{Slot:4b,id:"minecraft:blaze_powder",count:5}]}';
  const result = await filteredMachine(server, 104, 'brewing_stand', data, 'blaze_powder', 4);
  if (result.sink.includes('minecraft:blaze_powder')) throw new Error(`brewing fuel was extracted: ${result.sink}`);
  expectContains(result.source, 'minecraft:blaze_powder');
});

test('stall avoidance can drain a returned brewing bottle', async ({ server }) => {
  const data = '{Fuel:20b,Items:[{Slot:0b,id:"minecraft:potion",count:1},{Slot:3b,id:"minecraft:glass_bottle",count:1}]}';
  const result = await filteredMachine(server, 112, 'brewing_stand', data, 'glass_bottle', 3);
  expectContains(result.sink, 'minecraft:glass_bottle');
  if (result.source.includes('minecraft:glass_bottle')) throw new Error(`bottle was left behind: ${result.source}`);
});
