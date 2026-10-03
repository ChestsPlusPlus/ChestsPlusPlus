import { sleep, test } from '@plugwright/runner';

// Phase 2 E2E: real hopper ticking through HopperBridge on a 26.3 server. Bots can't stay connected for long through
// ViaBackwards (spike S6), so the world is set up over RCON and asserted through the /cpptest harness.

const OWNER = 'E2EOwner';

async function run(server: { execute(cmd: string): Promise<string> }, ...commands: string[]): Promise<string[]> {
  const out: string[] = [];
  for (const command of commands) out.push(await server.execute(command));
  return out;
}

function expectContains(text: string, needle: string) {
  if (!text.includes(needle)) throw new Error(`expected ${JSON.stringify(needle)} in ${JSON.stringify(text)}`);
}

test('hoppers move items into and out of a ChestLink', async ({ server }) => {
  await run(
    server,
    'cpptest reset',
    'forceload add 0 0',
    'setblock 2 -52 2 minecraft:chest',
    'setblock 2 -51 2 minecraft:hopper[facing=down]',
    'setblock 2 -50 2 minecraft:chest',
  );
  expectContains(await server.execute(`cpptest link chestlink ${OWNER} hoppers 2 -50 2`), 'cpptest link ok');
  await server.execute(
    'setblock 2 -49 2 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:cobblestone",count:3}]}',
  );
  await sleep(2500);

  const sink = await server.execute('data get block 2 -52 2 Items');
  expectContains(sink, 'minecraft:cobblestone');
  expectContains(sink, 'count: 3');
  expectContains(await server.execute(`cpptest group chestlink ${OWNER} hoppers`), 'items={}');
  // The physical chest stays empty: transfers go to the group inventory.
  expectContains(await server.execute('data get block 2 -50 2 Items'), '[]');
});

test('a linked node gets a non-persistent display', async ({ server }) => {
  await run(server, 'cpptest reset', 'forceload add 0 0', 'setblock 6 -50 6 minecraft:barrel');
  expectContains(await server.execute(`cpptest link chestlink ${OWNER} shown 6 -50 6`), 'cpptest link ok');
  expectContains(await server.execute('cpptest displays'), 'tracked=1 entities=2');
  await server.execute('cpptest reset');
  expectContains(await server.execute('cpptest displays'), 'tracked=0 entities=0');
});
