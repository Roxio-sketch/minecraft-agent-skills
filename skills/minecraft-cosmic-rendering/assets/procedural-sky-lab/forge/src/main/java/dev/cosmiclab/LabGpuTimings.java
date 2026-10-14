package dev.cosmiclab;

import org.lwjgl.opengl.*;
import java.util.*;

/** Non-blocking elapsed GPU queries; each pass reports its own work. */
final class LabGpuTimings {
    private record Pending(String name, int query) {}
    private static final ArrayDeque<Pending> pending = new ArrayDeque<>();
    static final Map<String, List<Double>> samples = new LinkedHashMap<>();
    static int begin(String name) {
        poll();
        if (pending.size() >= 32) return 0;
        int query = GL15.glGenQueries(); GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, query); return query;
    }
    static void end(String name, int query) {
        if (query == 0) return;
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED); pending.add(new Pending(name, query));
    }
    static void poll() {
        while (!pending.isEmpty() && GL15.glGetQueryObjecti(pending.peek().query, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            Pending p = pending.remove();
            var values = samples.computeIfAbsent(p.name, key -> new ArrayList<>());
            values.add(GL33.glGetQueryObjectui64(p.query, GL15.GL_QUERY_RESULT) / 1_000_000d);
            if (values.size() > 512) values.remove(0);
            GL15.glDeleteQueries(p.query);
        }
    }
    static void clear() { while (!pending.isEmpty()) GL15.glDeleteQueries(pending.remove().query); samples.clear(); }
    static void release() {
        while (!pending.isEmpty()) GL15.glDeleteQueries(pending.remove().query);
        samples.clear();
    }
}
