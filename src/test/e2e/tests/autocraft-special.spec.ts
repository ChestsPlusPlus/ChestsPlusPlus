import { sleep, test } from '@plugwright/runner';
import { appendFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// Experiment: special (complex) recipes and per-slot match modes in a real AutoCrafter. Records observations instead of
// asserting, so every case runs. Results go to build/autocraft-special-results.txt.

const OWNER = 'E2EOwner';
const RESULTS = fileURLToPath(new URL('../../../../../build/autocraft-special-results.txt', import.meta.url));
const ROWS = [4, 8, 14];

type Server = { execute(cmd: string): Promise<string> };

interface Case {
  name: string;
  /** Ghost matrix for `cpptest recipe-items`: slots separated by `|`, optional `@recipe|@exact|@type` per slot. */
  ghost: string;
  /** SNBT item entries for the input chest above the table (Slot is added automatically). */
  inputs: string[];
}

const STRIPE = '[{pattern:"minecraft:stripe_top",color:"red"}]';
const PATTERNED_BANNER = `minecraft:white_banner[banner_patterns=${STRIPE}]`;
const PATTERNED = `"minecraft:banner_patterns":${STRIPE}`;
const BOOK = (title: string, generation: number) =>
  `"minecraft:written_book_content":{title:"${title}",author:"x",generation:${generation},pages:["hi"]}`;
const GHOST_BOOK = 'minecraft:written_book[written_book_content={title:"A",author:"x",pages:["hi"]}]';
const DAMAGED_PICK = 'minecraft:iron_pickaxe[damage=100]';
const PICK = (damage: number, extra = '') => `id:"minecraft:iron_pickaxe",count:1,components:{"minecraft:damage":${damage}${extra}}`;
const STAR = 'minecraft:firework_star[firework_explosion={shape:"small_ball",colors:[I;16711680]}]';
const POTION = 'minecraft:lingering_potion[potion_contents={potion:"minecraft:swiftness"}]';
const arrows = (potion: string) => ['arrow', 'arrow', 'arrow', 'arrow', potion, 'arrow', 'arrow', 'arrow', 'arrow'].map(slot => slot.includes(':') ? slot : `minecraft:${slot}`).join('|');
const OAK = 'minecraft:oak_planks';
const chest = (slot: string) => [slot, slot, slot, slot, '-', slot, slot, slot, slot].join('|');

const REPAIR_INPUTS = [PICK(200), PICK(150)];

const CASES: Case[] = [
  { name: 'repair, default: two damaged pickaxes', ghost: `${DAMAGED_PICK}|${DAMAGED_PICK}`, inputs: REPAIR_INPUTS },
  { name: 'repair, type: two damaged pickaxes', ghost: `${DAMAGED_PICK}@type|${DAMAGED_PICK}@type`, inputs: REPAIR_INPUTS },
  {
    name: 'repair, type: one input enchanted',
    ghost: `${DAMAGED_PICK}@type|${DAMAGED_PICK}@type`,
    inputs: [PICK(200, ',"minecraft:enchantments":{"minecraft:efficiency":3}'), PICK(150)],
  },
  {
    name: 'repair, type: two undamaged pickaxes',
    ghost: `${DAMAGED_PICK}@type|${DAMAGED_PICK}@type`,
    inputs: ['id:"minecraft:iron_pickaxe",count:1', 'id:"minecraft:iron_pickaxe",count:1'],
  },
  {
    name: 'repair, type: damaged diamond pickaxe first',
    ghost: `${DAMAGED_PICK}@type|${DAMAGED_PICK}@type`,
    inputs: ['id:"minecraft:diamond_pickaxe",count:1,components:{"minecraft:damage":100}', ...REPAIR_INPUTS],
  },
  {
    name: 'banner copy, default: patterned first',
    ghost: `${PATTERNED_BANNER}|minecraft:white_banner`,
    inputs: [`id:"minecraft:white_banner",count:1,components:{${PATTERNED}}`, 'id:"minecraft:white_banner",count:1'],
  },
  {
    name: 'banner copy, default: two blanks first',
    ghost: `${PATTERNED_BANNER}|minecraft:white_banner`,
    inputs: ['id:"minecraft:white_banner",count:2', `id:"minecraft:white_banner",count:1,components:{${PATTERNED}}`],
  },
  {
    name: 'banner copy, type: two blanks first',
    ghost: `${PATTERNED_BANNER}@type|minecraft:white_banner@type`,
    inputs: ['id:"minecraft:white_banner",count:2', `id:"minecraft:white_banner",count:1,components:{${PATTERNED}}`],
  },
  {
    name: 'banner copy, default: a different pattern',
    ghost: `${PATTERNED_BANNER}|minecraft:white_banner`,
    inputs: [
      'id:"minecraft:white_banner",count:1,components:{"minecraft:banner_patterns":[{pattern:"minecraft:cross",color:"blue"}]}',
      'id:"minecraft:white_banner",count:1',
    ],
  },
  {
    name: 'book copy, default: a different book',
    ghost: `${GHOST_BOOK}|minecraft:writable_book`,
    inputs: [`id:"minecraft:written_book",count:1,components:{${BOOK('B', 0)}}`, 'id:"minecraft:writable_book",count:1'],
  },
  {
    name: 'book copy, type: a different book',
    ghost: `${GHOST_BOOK}@type|minecraft:writable_book`,
    inputs: [`id:"minecraft:written_book",count:1,components:{${BOOK('B', 0)}}`, 'id:"minecraft:writable_book",count:1'],
  },
  {
    name: 'book copy, type: copy-of-a-copy first',
    ghost: `${GHOST_BOOK}@type|minecraft:writable_book`,
    inputs: [
      `id:"minecraft:written_book",count:1,components:{${BOOK('C', 2)}}`,
      `id:"minecraft:written_book",count:1,components:{${BOOK('B', 0)}}`,
      'id:"minecraft:writable_book",count:2',
    ],
  },
  {
    name: 'map copy, default: a different map id',
    ghost: 'minecraft:filled_map[map_id=1]|minecraft:map',
    inputs: ['id:"minecraft:filled_map",count:1,components:{"minecraft:map_id":7}', 'id:"minecraft:map",count:1'],
  },
  {
    name: 'map copy, exact: a different map id',
    ghost: 'minecraft:filled_map[map_id=1]@exact|minecraft:map',
    inputs: ['id:"minecraft:filled_map",count:1,components:{"minecraft:map_id":7}', 'id:"minecraft:map",count:1'],
  },
  {
    name: 'firework rocket, default: a different star',
    ghost: `minecraft:paper|minecraft:gunpowder|${STAR}`,
    inputs: [
      'id:"minecraft:paper",count:1',
      'id:"minecraft:gunpowder",count:1',
      'id:"minecraft:firework_star",count:1,components:{"minecraft:firework_explosion":{shape:"large_ball",colors:[I;255]}}',
    ],
  },
  {
    name: 'firework rocket, type: a different star',
    ghost: `minecraft:paper|minecraft:gunpowder|${STAR}@type`,
    inputs: [
      'id:"minecraft:paper",count:1',
      'id:"minecraft:gunpowder",count:1',
      'id:"minecraft:firework_star",count:1,components:{"minecraft:firework_explosion":{shape:"large_ball",colors:[I;255]}}',
    ],
  },
  {
    name: 'armour dye, default: named, enchanted chestplate',
    ghost: 'minecraft:leather_chestplate|minecraft:red_dye',
    inputs: [
      'id:"minecraft:leather_chestplate",count:1,components:{"minecraft:custom_name":"Bob","minecraft:enchantments":{"minecraft:protection":2}}',
      'id:"minecraft:red_dye",count:1',
    ],
  },
  {
    name: 'armour dye, type: named, enchanted chestplate',
    ghost: 'minecraft:leather_chestplate@type|minecraft:red_dye',
    inputs: [
      'id:"minecraft:leather_chestplate",count:1,components:{"minecraft:custom_name":"Bob","minecraft:enchantments":{"minecraft:protection":2}}',
      'id:"minecraft:red_dye",count:1',
    ],
  },
  {
    name: 'shield decoration, default: decorated shield first',
    ghost: `minecraft:shield|${PATTERNED_BANNER}`,
    inputs: [
      'id:"minecraft:shield",count:1,components:{"minecraft:base_color":"blue","minecraft:banner_patterns":[{pattern:"minecraft:cross",color:"white"}]}',
      'id:"minecraft:shield",count:1',
      `id:"minecraft:white_banner",count:1,components:{${PATTERNED}}`,
    ],
  },
  {
    name: 'tipped arrow, default: a different potion',
    ghost: arrows(POTION),
    inputs: ['id:"minecraft:arrow",count:8', 'id:"minecraft:lingering_potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:strength"}}'],
  },
  {
    name: 'tipped arrow, type: a different potion',
    ghost: arrows(`${POTION}@type`),
    inputs: ['id:"minecraft:arrow",count:8', 'id:"minecraft:lingering_potion",count:1,components:{"minecraft:potion_contents":{potion:"minecraft:strength"}}'],
  },
  {
    name: 'chest (shaped), default: mixed planks',
    ghost: chest(OAK),
    inputs: ['id:"minecraft:birch_planks",count:4', 'id:"minecraft:oak_planks",count:4'],
  },
  {
    name: 'chest (shaped), type: mixed planks',
    ghost: chest(`${OAK}@type`),
    inputs: ['id:"minecraft:birch_planks",count:4', 'id:"minecraft:oak_planks",count:4'],
  },
];

function record(lines: string[]) {
  const text = lines.join('\n') + '\n\n';
  console.log(text);
  appendFileSync(RESULTS, text);
}

async function run(server: Server, index: number, c: Case) {
  await server.execute('cpptest reset');
  const x = (index % 8) * 2;
  const z = ROWS[Math.floor(index / 8)];
  const items = c.inputs.map((item, slot) => `{Slot:${slot}b,${item}}`).join(',');
  await server.execute('forceload add 0 0');
  await server.execute(`setblock ${x} -50 ${z} minecraft:air`);
  await server.execute(`setblock ${x} -52 ${z} minecraft:air`);
  const setup = [
    await server.execute(`setblock ${x} -50 ${z} minecraft:chest{Items:[${items}]}`),
    await server.execute(`setblock ${x} -51 ${z} minecraft:crafting_table`),
    await server.execute(`setblock ${x} -52 ${z} minecraft:hopper[facing=down]`),
  ];
  const name = `special${index}`;
  const before = await server.execute(`cpptest items ${x} -50 ${z}`);
  const link = await server.execute(`cpptest link autocraft ${OWNER} ${name} ${x} -51 ${z}`);
  const recipe = await server.execute(`cpptest recipe-items ${OWNER} ${name} ${c.ghost}`);
  await sleep(3500);
  record([
    `### ${c.name}`,
    ...setup.filter(line => !line.startsWith('Changed the block')).map(line => `setup: ${line}`),
    `link: ${link.trim()}`,
    `recipe: ${recipe.trim()}`,
    `input chest before: ${before.trim()}`,
    `input chest after: ${(await server.execute(`cpptest items ${x} -50 ${z}`)).trim()}`,
    `output hopper after: ${(await server.execute(`cpptest items ${x} -52 ${z}`)).trim()}`,
  ]);
}

CASES.forEach((c, index) => {
  test(`special recipe: ${c.name}`, async ({ server }) => {
    await run(server, index, c);
  });
});
