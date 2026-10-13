import { definePlugin, sleep } from '@plugwright/runner';

export default definePlugin({
  name: 'fixture-chunks',

  async setup({ session }) {
    const server = session.console;
    if (!server) throw new Error('fixture-chunks needs the server console');
    // Keep temporary player chunk tickets away from fixtures as bots join and leave.
    await server.execute('setworldspawn 1024 -60 1024');
    await server.execute('forceload add 0 0 31 15');
    // A force-load ticket loads the chunk before its neighbours are ready to tick it. Wait before bots add temporary tickets.
    const deadline = Date.now() + 30_000;
    for (;;) {
      const levels = await Promise.all([0, 1].map(x => server.execute(`cpptest chunk ${x} 0`)));
      if (levels.every(level => level.includes('level=ENTITY_TICKING forced=true'))) return;
      if (Date.now() >= deadline) throw new Error(`fixture chunks did not become entity-ticking: ${levels.join('; ')}`);
      await sleep(10);
    }
  },
});
