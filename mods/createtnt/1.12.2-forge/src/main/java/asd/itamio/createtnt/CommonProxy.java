package asd.itamio.createtnt;

/**
 * Common (server/dedicated) proxy. Client-only hooks are no-ops here and are
 * overridden in {@link ClientProxy}.
 */
public class CommonProxy {

    /** Register any client-only event listeners / render hooks. */
    public void registerClientHooks() {
    }

    /** Called from init(). Client proxy uses it to bind nothing extra. */
    public void preRenderInit() {
    }
}