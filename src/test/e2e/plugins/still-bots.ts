import { definePlugin } from '@plugwright/runner';

// Through ViaBackwards, the 26.3 server rejects the bots' movement packets: each join logs "moved too quickly!" and about a second
// later, when mineflayer sends its once-a-second position update, kicks the bot with "Invalid move player packet received". No spec
// moves a bot, so the bots stop sending movement and stay where they spawned.

const MOVEMENT_PACKETS = new Set(['position', 'position_look', 'look', 'flying']);

export default definePlugin({
  name: 'still-bots',

  onPlayerCreate(player) {
    const client = player.bot._client;
    const write = client.write.bind(client);
    client.write = (name, params) => {
      if (!MOVEMENT_PACKETS.has(name)) write(name, params);
    };
  },
});
