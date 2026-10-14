package dev.alchemyvfx;

/** Called by the optional host hook patch. Only suppresses legacy effects after a successful volume draw. */
public final class HostHook {
    public static boolean claims(Object furnace) { return Client.claimed.contains(furnace); }
    private HostHook() {}
}
