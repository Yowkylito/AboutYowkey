import { handleRequest } from "./handler.js";

/** The Worker's entry point; everything it does is in handler.js, which the tests run without a Workers runtime. */
export default {
  fetch: (request, env) => handleRequest(request, env),
};
