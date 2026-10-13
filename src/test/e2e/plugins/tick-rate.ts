import { definePlugin, type ServerConsole } from '@plugwright/runner';

// Runs the server at options.rate ticks a second for the whole suite. The specs count game ticks, never milliseconds, so the rate only
// changes how long the run takes. At the end it reports the rate the server actually kept up, which a slow machine caps.

let server: ServerConsole | null = null;
let started = { tick: 0, at: 0 };

async function gameTime(console: ServerConsole) {
  return Number((await console.execute('time query gametime')).match(/(\d+)/)?.[1] ?? Number.NaN);
}

export default definePlugin<{ rate?: string }>({
  name: 'tick-rate',

  async setup({ session, options }) {
    server = session.console;
    if (!server) throw new Error('tick-rate needs the server console');
    await server.execute(`tick rate ${options.rate ?? '20'}`);
    started = { tick: await gameTime(server), at: Date.now() };
  },

  async teardown() {
    if (!server) return;
    const ticks = (await gameTime(server)) - started.tick;
    const seconds = (Date.now() - started.at) / 1000;
    console.log(`[tick-rate] ran ${ticks} ticks in ${seconds.toFixed(1)}s: ${(ticks / seconds).toFixed(0)} a second`);
    await server.execute('tick rate 20');
  },
});
