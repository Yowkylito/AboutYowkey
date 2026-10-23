import { dailyReport, handleRequest } from "./handler.js";

/** The Worker's entry point; everything it does is in handler.js, which the tests run without a Workers runtime. */
export default {
  fetch: (request, env, ctx) => handleRequest(request, env, ctx),
  /**
   * The cron in wrangler.toml, at 23:59 the owner's time. The day is taken from the time the run was scheduled
   * for, not from when it starts, so a run that starts a little late still reports the day it was meant for.
   */
  async scheduled(controller, env, ctx) {
    await dailyReport(env, ctx, { now: new Date(controller.scheduledTime) });
  },
};
