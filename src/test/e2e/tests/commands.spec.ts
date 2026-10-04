import { expect, test } from '@plugwright/runner';

// Phase 3 E2E: commands through a real (Via-bridged) client. Kept short: bots are kicked after ~3 s (spike S6).

test('/cl list and /cl open work for a group the bot owns', async ({ player, server }) => {
  await server.execute('cpptest reset');
  await server.execute('forceload add 0 0');
  await server.execute('setblock 10 -50 10 minecraft:chest');
  await server.execute(`cpptest link chestlink ${player.username} e2egroup 10 -50 10`);

  player.chat('/cl list');
  await expect(player).toHaveReceivedMessage('e2egroup');

  player.chat('/cl open e2egroup');
  const gui = await player.gui({ title: /ChestLink: e2egroup/ });
  if (!gui) throw new Error('ChestLink inventory did not open');
});

test('/cpp help lists commands', async ({ player }) => {
  player.chat('/cpp help');
  await expect(player).toHaveReceivedMessage('chestlink add <group>');
});
