import { expect, test } from '@plugwright/runner';

// Phase 0 smoke tests. The bot speaks 26.1; ViaVersion/ViaBackwards bridge it to the 26.3 server (plan §10.3.1).

test('bot joins, /cpp version replies', async ({ player }) => {
  await player.makeOp();
  player.chat('/cpp version');
  await expect(player).toHaveReceivedMessage('ChestsPlusPlus v');
});

test('test harness answers over RCON', { requires: { console: true } }, async ({ server }) => {
  const response = await server.execute('cpptest plugin');
  if (!response.includes('cpptest plugin enabled=true')) {
    throw new Error(`Unexpected /cpptest response: ${JSON.stringify(response)}`);
  }
});

test('test harness is console-only, even for ops', async ({ player }) => {
  await player.makeOp();
  player.chat('/cpptest ping');
  await expect(player).toHaveReceivedMessage('Unknown or incomplete command');
});
