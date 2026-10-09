import { sleep, test } from '@plugwright/runner';

// Container-lock plugins (Bolt, LWC, BlockLocker) stop hoppers by cancelling InventoryMoveItemEvent for a locked block. `cpptest lock`
// stands in for one. A linked block must behave exactly like the vanilla container it is: whatever a hopper or AutoCrafter can't do to
// a locked vanilla chest, it can't do to a locked ChestLink chest either.

const OWNER = 'E2EOwner';
const COBBLE = '{Slot:0b,id:"minecraft:cobblestone",count:3}';

type Server = { execute(cmd: string): Promise<string> };

// Its own chunk, clear of the other specs' blocks: a vanilla chest, a locked ChestLink and an unlocked ChestLink side by side.
const VANILLA = 18;
const LINKED = 21;
const OPEN = 24;
const SETUP = ['cpptest reset', 'forceload add 16 0'];

async function run(server: Server, ...commands: string[]) {
  for (const command of commands) await server.execute(command);
}

function expectContains(text: string, needle: string) {
  if (!text.includes(needle)) throw new Error(`expected ${JSON.stringify(needle)} in ${JSON.stringify(text)}`);
}

/** A chest holding three cobblestone at (x, -50, z), linked into its own group when {@code group} is given. */
async function chest(server: Server, x: number, z: number, group?: string) {
  await server.execute(`setblock ${x} -50 ${z} minecraft:chest{Items:[${COBBLE}]}`);
  if (group) expectContains(await server.execute(`cpptest link chestlink ${OWNER} ${group} ${x} -50 ${z}`), 'cpptest link ok');
}

async function lock(server: Server, x: number, y: number, z: number) {
  expectContains(await server.execute(`cpptest lock ${x} ${y} ${z}`), 'cpptest lock ok');
}

async function group(server: Server, name: string) {
  return server.execute(`cpptest group chestlink ${OWNER} ${name}`);
}

test('a hopper below a locked chest pulls nothing, linked or not', async ({ server }) => {
  await run(server, ...SETUP);
  await chest(server, VANILLA, 8);
  await chest(server, LINKED, 8, 'pullLocked');
  await chest(server, OPEN, 8, 'pullOpen');
  await lock(server, VANILLA, -50, 8);
  await lock(server, LINKED, -50, 8);
  for (const x of [VANILLA, LINKED, OPEN]) await server.execute(`setblock ${x} -51 8 minecraft:hopper[facing=down]`);
  await sleep(2000);

  expectContains(await server.execute(`data get block ${VANILLA} -51 8 Items`), '[]');
  expectContains(await server.execute(`cpptest items ${VANILLA} -50 8`), 'cobblestone');
  expectContains(await server.execute(`data get block ${LINKED} -51 8 Items`), '[]');
  expectContains(await group(server, 'pullLocked'), 'items={COBBLESTONE=3}');
  // Control: the same unlocked ChestLink is drained.
  expectContains(await server.execute(`data get block ${OPEN} -51 8 Items`), 'minecraft:cobblestone');
  expectContains(await group(server, 'pullOpen'), 'items={}');
});

test('a hopper above a locked chest pushes nothing, linked or not', async ({ server }) => {
  await run(server, ...SETUP);
  for (const x of [VANILLA, LINKED, OPEN]) await server.execute(`setblock ${x} -50 10 minecraft:chest`);
  expectContains(await server.execute(`cpptest link chestlink ${OWNER} pushLocked ${LINKED} -50 10`), 'cpptest link ok');
  expectContains(await server.execute(`cpptest link chestlink ${OWNER} pushOpen ${OPEN} -50 10`), 'cpptest link ok');
  await lock(server, VANILLA, -50, 10);
  await lock(server, LINKED, -50, 10);
  for (const x of [VANILLA, LINKED, OPEN]) await server.execute(`setblock ${x} -49 10 minecraft:hopper[facing=down]{Items:[${COBBLE}]}`);
  await sleep(2000);

  expectContains(await server.execute(`data get block ${VANILLA} -49 10 Items`), 'count: 3');
  expectContains(await server.execute(`cpptest items ${VANILLA} -50 10`), 'empty');
  expectContains(await server.execute(`data get block ${LINKED} -49 10 Items`), 'count: 3');
  expectContains(await group(server, 'pushLocked'), 'items={}');
  expectContains(await group(server, 'pushOpen'), 'items={COBBLESTONE=3}');
});

/** A torch AutoCrafter at (x, -51, z) whose only input is the chest above, with a hopper below. */
async function torchCrafter(server: Server, x: number, z: number, name: string) {
  await run(server, `setblock ${x} -51 ${z} minecraft:crafting_table`, `setblock ${x} -52 ${z} minecraft:hopper[facing=down]`);
  expectContains(await server.execute(`cpptest link autocraft ${OWNER} ${name} ${x} -51 ${z}`), 'cpptest link ok');
  expectContains(await server.execute(`cpptest recipe ${OWNER} ${name} -,minecraft:coal,-,-,minecraft:stick,-,-,-,-`), 'result=TORCHx4');
}

test('an AutoCrafter takes nothing from a locked chest, linked or not', async ({ server }) => {
  await run(server, ...SETUP);
  const torchItems = '{Slot:0b,id:"minecraft:coal",count:1},{Slot:1b,id:"minecraft:stick",count:1}';
  for (const x of [VANILLA, LINKED, OPEN]) await server.execute(`setblock ${x} -50 12 minecraft:chest{Items:[${torchItems}]}`);
  expectContains(await server.execute(`cpptest link chestlink ${OWNER} craftLocked ${LINKED} -50 12`), 'cpptest link ok');
  expectContains(await server.execute(`cpptest link chestlink ${OWNER} craftOpen ${OPEN} -50 12`), 'cpptest link ok');
  await lock(server, VANILLA, -50, 12);
  await lock(server, LINKED, -50, 12);
  await torchCrafter(server, VANILLA, 12, 'fromLockedChest');
  await torchCrafter(server, LINKED, 12, 'fromLockedLink');
  await torchCrafter(server, OPEN, 12, 'fromOpenLink');
  await sleep(1500);

  expectContains(await server.execute(`data get block ${VANILLA} -52 12 Items`), '[]');
  expectContains(await server.execute(`cpptest items ${VANILLA} -50 12`), 'coal');
  expectContains(await server.execute(`data get block ${LINKED} -52 12 Items`), '[]');
  expectContains(await group(server, 'craftLocked'), 'COAL=1');
  expectContains(await server.execute(`data get block ${OPEN} -52 12 Items`), 'minecraft:torch');
});

const GOLEM_SITES = [100, 200, 300];
const GOLEM_TAG = 'cpptest_golem';

function golemChunks(x: number) {
  return `${x - 5} -5 ${x + 8} 8`;
}

/** Sites stay beyond each other's search range; the enclosure keeps wandering golems inside entity-ticking chunks. */
async function golemSite(server: Server, x: number, group?: string) {
  await run(server,
    `forceload add ${golemChunks(x)}`,
    `fill ${x - 4} -61 -4 ${x + 7} -57 7 minecraft:glass hollow`,
    `setblock ${x} -60 0 minecraft:copper_chest{Items:[${COBBLE}]}`,
    `setblock ${x + 3} -60 0 minecraft:chest`,
  );
  if (group) expectContains(await server.execute(`cpptest link chestlink ${OWNER} ${group} ${x} -60 0`), 'cpptest link ok');
}

async function waitForGolemDelivery(server: Server) {
  let delivered = '';
  for (let i = 0; i < 40 && !delivered.includes('cobblestone'); i++) {
    await sleep(500);
    delivered = await server.execute('cpptest items 303 -60 0');
  }
  expectContains(delivered, 'cobblestone');
  expectContains(delivered, 'x3');
}

async function expectGolemRefused(server: Server, x: number) {
  const checks = await server.execute(`cpptest lock-checks ${x} -60 0`);
  const refusals = checks.match(/golem-refusals=(\d+)/);
  if (!refusals || Number(refusals[1]) === 0) throw new Error(`golem at ${x} never tried its locked source: ${checks}`);
}

async function golemDiagnostics(server: Server) {
  const commands = ['time query gametime', 'forceload query'];
  for (const x of GOLEM_SITES) {
    commands.push(`cpptest items ${x} -60 0`, `cpptest items ${x + 3} -60 0`, `cpptest lock-checks ${x} -60 0`);
    const entity = `@e[type=minecraft:copper_golem,tag=${GOLEM_TAG}_${x},limit=1]`;
    for (const path of ['Pos', 'Motion', 'equipment', 'Brain.memories']) commands.push(`data get entity ${entity} ${path}`);
  }
  commands.push(`cpptest group chestlink ${OWNER} golemOpen`, `cpptest group chestlink ${OWNER} golemLocked`);
  const lines = [];
  for (const command of commands) lines.push(`${command}: ${(await server.execute(command)).trim()}`);
  return lines.join('\n');
}

test('a copper golem takes nothing from a locked copper chest, linked or not', async ({ server }) => {
  try {
    await run(server, ...SETUP, `kill @e[tag=${GOLEM_TAG}]`);
    await golemSite(server, 100);
    await golemSite(server, 200, 'golemLocked');
    await golemSite(server, 300, 'golemOpen');
    await lock(server, 100, -60, 0);
    await lock(server, 200, -60, 0);
    expectContains(await group(server, 'golemOpen'), 'items={COBBLESTONE=3}');
    for (const x of [200, 300]) expectContains(await server.execute(`cpptest items ${x} -60 0`), 'empty');
    for (const x of GOLEM_SITES) {
      expectContains(await server.execute(`cpptest items ${x + 3} -60 0`), 'empty');
      expectContains(await server.execute(`summon minecraft:copper_golem ${x + 1} -60 2 {Tags:["${GOLEM_TAG}","${GOLEM_TAG}_${x}"]}`), 'Summoned');
    }

    await waitForGolemDelivery(server);
    expectContains(await group(server, 'golemOpen'), 'items={}');
    expectContains(await server.execute('cpptest items 300 -60 0'), 'empty');
    expectContains(await server.execute('cpptest items 103 -60 0'), 'empty');
    expectContains(await server.execute('cpptest items 100 -60 0'), 'cobblestone[]x3');
    expectContains(await server.execute('cpptest items 203 -60 0'), 'empty');
    expectContains(await group(server, 'golemLocked'), 'items={COBBLESTONE=3}');
    for (const x of [100, 200]) await expectGolemRefused(server, x);
  } catch (error) {
    throw new Error(`${error}\n${await golemDiagnostics(server)}`);
  } finally {
    await run(server, `kill @e[tag=${GOLEM_TAG}]`, 'cpptest reset');
    for (const x of GOLEM_SITES) await server.execute(`forceload remove ${golemChunks(x)}`);
  }
});
