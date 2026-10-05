package dev.vfxworkbench.effects.lightning;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector4d;

import java.util.ArrayList;
import java.util.List;

/** Independent projection/coverage regressions; no render context or game is needed. */
final class HaloGeometryCheck {
    private static int assertions;

    public static void main(String[] args) { run(); }

    static void run() {
        assertions = 0;
        checkThickCorners();
        checkWidthsAndDiskEnergy();
        checkClippingAndAttributes();
        checkCameraAndBounds();
        checkSubdivision();
        checkInvalidInputs();
        System.out.println("PASS: halo convex short/thick/acute strips, actual view projection, near/far interpolation, butt caps, viewport bounds and subdivision-stable maximum coverage (" + assertions + " assertions)");
    }

    private static HaloGeometry.Point point(double x, double y, double z, double radius) {
        return new HaloGeometry.Point(new Vec3(x, y, z), radius, .5, .8);
    }

    private static Matrix4f perspective(double degrees, int width, int height) {
        return new Matrix4f().perspective((float)Math.toRadians(degrees), width / (float)height, .05f, 1024);
    }

    private static void checkThickCorners() {
        double length = .199, radius = 1.154;
        double[] a = {0, radius}, b = {0, -radius}, c = {length + radius, -radius}, d = {length - radius, radius};
        require(cross(a, b, c) > 0 && cross(a, c, d) < 0, "former 90 degree shared miter counterexample no longer reproduces");
        Matrix4f clip = perspective(70, 1920, 1080);
        LightningRecipe recipe = new LightningRecipe();
        List<List<HaloGeometry.Point>> cases = List.of(
            List.of(point(-length, 0, -10, radius), point(0, 0, -10, radius), point(0, length, -10, radius)),
            List.of(point(-length, 0, -10, 2), point(0, 0, -10, 2), point(-length, .00001, -10, 2)),
            List.of(point(-.000001, 0, -10, 2), point(0, 0, -10, 2), point(0, .000001, -10, 2)),
            List.of(point(-length, 0, -10, .946), point(0, 0, -10, 1.154), point(0, length, -10, 1.269))
        );
        for (var points : cases) {
            HaloGeometry.Mesh mesh = HaloGeometry.build(points, clip, 1920, 1080, recipe);
            require(mesh.body().size() == 8 && mesh.round().size() == 4, "thick corner did not produce two independent bodies and one interior disk");
            checkMesh(mesh, clip, 1920, 1080);
        }
        var collinear = HaloGeometry.build(List.of(point(0, 0, -2, 2), point(0, 0, -20, 2), point(0, 0, -80, 2)), clip, 1920, 1080, recipe);
        require(collinear.empty() && collinear.body().isEmpty() && collinear.round().isEmpty(), "view-collinear segments produced a sheet or an isolated disk");
        var nearlyCollinear = HaloGeometry.build(List.of(point(0, 0, -2, 2), point(.00001, 0, -20, 2), point(.00001, .00001, -80, 2)), clip, 1920, 1080, recipe);
        require(!nearlyCollinear.empty(), "finite small projected segments were lost");
        checkMesh(nearlyCollinear, clip, 1920, 1080);
    }

    private static void checkWidthsAndDiskEnergy() {
        LightningRecipe recipe = new LightningRecipe();
        for (int[] viewport : List.of(new int[]{854, 480}, new int[]{1920, 1080}, new int[]{3840, 2160}))
            for (double fov : new double[]{40, 70, 110}) for (double distance : new double[]{2, 48, 480})
                for (double radius : new double[]{.001, .07, 1.154, 2}) {
                    int width = viewport[0], height = viewport[1];
                    Matrix4f clip = perspective(fov, width, height);
                    // This pinhole oracle does not use the helper's inverse VP.
                    double nativeHalf = radius * clip.m11() * height / (2 * distance);
                    double renderedHalf = Math.max(.75, nativeHalf);
                    var mesh = HaloGeometry.build(List.of(point(0, -.2, -distance, radius), point(0, .2, -distance, radius)), clip, width, height, recipe);
                    require(mesh.body().size() == 4 && mesh.round().isEmpty(), "a single line grew round terminal caps");
                    checkMesh(mesh, clip, width, height);
                    double[] p0 = project(mesh.body().get(0).p(), clip, width, height), p1 = project(mesh.body().get(1).p(), clip, width, height);
                    double half = Math.hypot(p0[0] - p1[0], p0[1] - p1[1]) * .5;
                    close(half, renderedHalf, 2e-4, "physical width or actual pixel minimum changed");
                    close(mesh.body().get(0).u(), nativeHalf / renderedHalf, 2e-6, "strip coverage is not native/rendered width");
                    close(half * mesh.body().get(0).u(), nativeHalf, 2e-4, "strip integrated energy changed");
                    if (nativeHalf >= .75) close(mesh.body().get(0).p().distanceTo(mesh.body().get(1).p()) * .5, radius, 2e-7, "ordinary physical radius was scaled");

                    var diskMesh = HaloGeometry.build(List.of(point(0, -.2, -distance, radius), point(0, 0, -distance, radius), point(.2, 0, -distance, radius)), clip, width, height, recipe);
                    require(diskMesh.round().size() == 4, "missing interior round joint");
                    checkMesh(diskMesh, clip, width, height);
                    double[] bottomLeft = project(diskMesh.round().get(0).p(), clip, width, height);
                    double[] topRight = project(diskMesh.round().get(2).p(), clip, width, height);
                    double rx = (topRight[0] - bottomLeft[0]) * .5, ry = (topRight[1] - bottomLeft[1]) * .5;
                    float correction = Math.abs(diskMesh.round().get(0).u());
                    close(rx, renderedHalf, 2e-4, "round joint x half-width");
                    close(ry, renderedHalf, 2e-4, "round joint y half-width");
                    double expectedCorrection = Math.pow(nativeHalf / renderedHalf, 2);
                    close(correction, expectedCorrection, Math.max(1e-10, expectedCorrection * 2e-6), "round joint lost squared area correction");
                    close(rx * ry * correction, nativeHalf * nativeHalf, Math.max(2e-10, nativeHalf * nativeHalf * 2e-6), "round joint integrated area energy changed");
                }
    }

    private static void checkClippingAndAttributes() {
        Matrix4f clip = perspective(70, 854, 480);
        LightningRecipe recipe = new LightningRecipe();
        recipe.gradientEnabled = true; recipe.glowColor = "#ffffff";
        recipe.gradientStart = "#ff0000"; recipe.gradientEnd = "#00ff00";
        var a = new HaloGeometry.Point(new Vec3(.001, 0, -.01), .002, 0, .1);
        var b = new HaloGeometry.Point(new Vec3(.01, .03, -2), .012, 1, .9);
        var near = HaloGeometry.build(List.of(a, b), clip, 854, 480, recipe);
        require(near.body().size() == 4, "a near-plane crossing was dropped rather than clipped");
        checkMesh(near, clip, 854, 480);
        Vec3 clippedCenter = near.body().get(0).p().add(near.body().get(1).p()).scale(.5);
        double t = (clippedCenter.z - a.p().z) / (b.p().z - a.p().z);
        double actualNear = clip.m32() / (clip.m22() - 1.0);
        close(-clippedCenter.z, actualNear, 1e-6, "near clip is not the actual projection's plane");
        require(t > 0 && t < 1, "near crossing did not interpolate its attributes");
        close(near.body().get(0).alpha(), .1 + .8 * t, 1e-6, "near clip alpha interpolation");
        close(near.body().get(0).p().distanceTo(near.body().get(1).p()) * .5, .002 + .01 * t, 1e-7, "near clip physical radius interpolation");
        close(near.body().get(0).rgb()[0], Math.round(255 * (1 - t)) / 255f, 1e-6, "near clip along/red interpolation");
        close(near.body().get(0).rgb()[1], Math.round(255 * t) / 255f, 1e-6, "near clip along/green interpolation");

        var far = HaloGeometry.build(List.of(new HaloGeometry.Point(new Vec3(0, 0, -10), 1, .2, .3), new HaloGeometry.Point(new Vec3(1, 2, -2000), 2, .8, .9)), clip, 854, 480, recipe);
        require(far.body().size() == 4, "a far-plane crossing was dropped rather than clipped");
        checkMesh(far, clip, 854, 480);
        Vec3 farCenter = far.body().get(2).p().add(far.body().get(3).p()).scale(.5);
        double actualFar = clip.m32() / (clip.m22() + 1.0);
        close(-farCenter.z, actualFar, .2, "far clip did not follow actual float projection");
        double tf = (-farCenter.z - 10) / 1990;
        close(far.body().get(2).alpha(), .3 + .6 * tf, 1e-6, "far clip alpha interpolation");
        double farNativeRadius = 1 + tf;
        double farNativePixels = farNativeRadius * clip.m11() * 480 / (-2 * farCenter.z);
        double[] farPlus = project(far.body().get(2).p(), clip, 854, 480), farMinus = project(far.body().get(3).p(), clip, 854, 480);
        double farRenderedPixels = Math.hypot(farPlus[0] - farMinus[0], farPlus[1] - farMinus[1]) * .5;
        require(farNativePixels < .75 && far.body().get(2).u() < 1, "far clipping sample did not exercise subpixel coverage");
        close(farRenderedPixels, .75, 2e-5, "far clip did not retain minimum raster coverage");
        close(far.body().get(2).u(), farNativePixels / .75, 1e-6, "far clip coverage did not compensate interpolated radius");
        close(far.body().get(2).p().distanceTo(far.body().get(3).p()) * .5 * far.body().get(2).u(), farNativeRadius, 2e-7, "far clip native radius interpolation after coverage compensation");

        var both = HaloGeometry.build(List.of(point(0, 0, 1, .01), point(1, 2, -2000, .01)), clip, 854, 480, recipe);
        require(both.body().size() == 4, "a segment spanning both planes was lost");
        checkMesh(both, clip, 854, 480);
        var rejected = HaloGeometry.build(List.of(point(0, 0, .1, 2), point(0, 1, .5, 2)), clip, 854, 480, recipe);
        require(rejected.empty(), "behind-camera vertices entered the halo mesh");
        var cutJoint = HaloGeometry.build(List.of(point(-.2, -.2, -2, .07), point(0, 0, .01, .07), point(.2, .2, -2, .07)), clip, 854, 480, recipe);
        require(cutJoint.body().size() == 8 && cutJoint.round().isEmpty(), "an invisible joint bridged a near-plane gap");
        checkMesh(cutJoint, clip, 854, 480);
    }

    private static void checkCameraAndBounds() {
        int width = 854, height = 480;
        LightningRecipe recipe = new LightningRecipe();
        Matrix4f projection = perspective(70, width, height);
        Matrix4f view = new Matrix4f().rotateY(.67f).rotateX(-.41f).rotateZ(.23f);
        Matrix4f actual = new Matrix4f(projection).mul(view);
        Matrix4d inverseView = new Matrix4d(view).invert();
        var points = new ArrayList<HaloGeometry.Point>();
        for (Vec3 p : List.of(new Vec3(-.2, -.2, -10), new Vec3(.1, 0, -10), new Vec3(0, .3, -10))) {
            Vector4d world = inverseView.transform(new Vector4d(p.x, p.y, p.z, 1));
            points.add(new HaloGeometry.Point(new Vec3(world.x, world.y, world.z), 1.154, .5, .8));
        }
        var mesh = HaloGeometry.build(points, actual, width, height, recipe);
        require(mesh.body().size() == 8 && mesh.round().size() == 4, "actual camera rotation lost geometry");
        checkMesh(mesh, actual, width, height);
        var baseline = HaloGeometry.build(List.of(point(-.2, -.2, -10, 1.154), point(.1, 0, -10, 1.154), point(0, .3, -10, 1.154)), projection, width, height, recipe);
        for (int i = 0; i < mesh.body().size(); i++) {
            double[] observed = project(mesh.body().get(i).p(), actual, width, height);
            double[] expected = project(baseline.body().get(i).p(), projection, width, height);
            close(observed[0], expected[0], 3e-4, "actual view x differs from camera-space oracle");
            close(observed[1], expected[1], 3e-4, "actual view y differs from camera-space oracle");
        }
        var full = HaloGeometry.build(List.of(point(0, -.01, -.06, 2), point(0, .01, -.06, 2)), projection, width, height, recipe);
        checkMesh(full, projection, width, height);
        require(full.bounds().x() == 0 && full.bounds().width() == width, "large near halo bounds were not clamped to the viewport");
        var outside = HaloGeometry.build(List.of(point(10000, 0, -10, 2), point(10000, 1, -10, 2)), projection, width, height, recipe);
        require(outside.empty() && outside.bounds().width() == 0, "offscreen halo acquired a visible scissor");
        var ortho = new Matrix4f().ortho(-4, 4, -3, 3, .05f, 1024);
        var orthographic = HaloGeometry.build(List.of(point(0, -.2, -48, .003), point(0, 0, -48, .003), point(.2, 0, -48, .003)), ortho, 800, 600, recipe);
        checkMesh(orthographic, ortho, 800, 600);
        close(orthographic.body().get(0).u(), .4, 1e-6, "orthographic actual pixel coverage");
        close(Math.abs(orthographic.round().get(0).u()), .16, 1e-6, "orthographic joint area coverage");
    }

    private static void checkSubdivision() {
        Matrix4f clip = perspective(70, 1920, 1080);
        LightningRecipe recipe = new LightningRecipe();
        // Subdividing a straight wide line must not introduce unnecessary disks,
        // inflate its radius, change its union coverage or round its butt caps.
        var coarse = line(16, 1.154, .8);
        var fine = line(256, 1.154, .8);
        var a = HaloGeometry.build(coarse, clip, 1920, 1080, recipe);
        var b = HaloGeometry.build(fine, clip, 1920, 1080, recipe);
        checkMesh(a, clip, 1920, 1080); checkMesh(b, clip, 1920, 1080);
        require(a.round().isEmpty() && b.round().isEmpty(), "collinear subdivisions introduced round joints");
        for (double x : new double[]{-.75, -.25, 0, .25, .75}) for (double y : new double[]{0, .2, .6, 1.153}) {
            Vec3 sample = new Vec3(x, y, -10);
            double[] screen = project(sample, clip, 1920, 1080);
            double observedA = maxCoverage(a, clip, 1920, 1080, screen[0], screen[1]);
            double observedB = maxCoverage(b, clip, 1920, 1080, screen[0], screen[1]);
            double expected = .8 * profile(Math.abs(y) / 1.154);
            close(observedA, expected, 3e-6, "coarse union did not retain original Gaussian");
            close(observedB, expected, 3e-6, "fine union accumulated short-segment energy");
            close(observedA, observedB, 3e-6, "subdivision changed halo union brightness");
        }
        for (double x : new double[]{-1.1, 1.1}) {
            double[] screen = project(new Vec3(x, 0, -10), clip, 1920, 1080);
            close(maxCoverage(a, clip, 1920, 1080, screen[0], screen[1]), 0, 1e-6, "coarse line gained rounded terminal caps");
            close(maxCoverage(b, clip, 1920, 1080, screen[0], screen[1]), 0, 1e-6, "fine line gained rounded terminal caps");
        }
    }

    private static List<HaloGeometry.Point> line(int subdivisions, double radius, double alpha) {
        var result = new ArrayList<HaloGeometry.Point>();
        for (int i = 0; i <= subdivisions; i++) result.add(new HaloGeometry.Point(new Vec3(-1 + 2.0 * i / subdivisions, 0, -10), radius, i / (double)subdivisions, alpha));
        return result;
    }

    private static void checkInvalidInputs() {
        Matrix4f clip = perspective(70, 854, 480);
        LightningRecipe recipe = new LightningRecipe();
        var points = List.of(point(0, -.2, -10, .07), point(0, .2, -10, .07));
        require(HaloGeometry.build(points, clip, 0, 480, recipe).empty(), "invalid viewport produced vertices");
        require(HaloGeometry.build(points, new Matrix4f().zero(), 854, 480, recipe).empty(), "singular inverse projection produced vertices");
        require(HaloGeometry.build(List.of(point(0, 0, -10, Double.NaN), point(0, .2, -10, .07)), clip, 854, 480, recipe).empty(), "nonfinite radius entered the mesh");
        require(HaloGeometry.build(List.of(point(0, 0, -10, 0), point(0, .2, -10, .07)), clip, 854, 480, recipe).empty(), "zero radius entered the mesh");
        var gap = HaloGeometry.build(List.of(point(0, -.2, -10, .07), point(Double.NaN, 0, -10, .07), point(0, .1, -10, .07), point(0, .2, -10, .07)), clip, 854, 480, recipe);
        require(gap.body().size() == 4 && gap.round().isEmpty(), "invalid gap was bridged or gained a round endcap");
        checkMesh(gap, clip, 854, 480);
        var clamped = HaloGeometry.build(List.of(new HaloGeometry.Point(new Vec3(0, -.2, -10), .07, 0, -3), new HaloGeometry.Point(new Vec3(0, .2, -10), .07, 1, 4)), clip, 854, 480, recipe);
        require(clamped.body().get(0).alpha() == 0 && clamped.body().get(2).alpha() == 1, "CPU alpha envelope is not clamped");
        checkMesh(clamped, clip, 854, 480);
    }

    private static double[] project(Vec3 point, Matrix4f actualClip, int width, int height) {
        Vector4d p = new Matrix4d(actualClip).transform(new Vector4d(point.x, point.y, point.z, 1));
        return new double[]{(p.x / p.w + 1) * width * .5, (p.y / p.w + 1) * height * .5, p.z / p.w, p.w};
    }

    private static void checkMesh(HaloGeometry.Mesh mesh, Matrix4f clip, int width, int height) {
        var bounds = mesh.bounds();
        require(bounds.x() >= 0 && bounds.y() >= 0 && bounds.width() >= 0 && bounds.height() >= 0
            && bounds.x() + bounds.width() <= width && bounds.y() + bounds.height() <= height, "scissor is outside the actual viewport");
        require(mesh.body().size() % 4 == 0 && mesh.round().size() % 4 == 0, "incomplete halo quad");
        for (var vertices : List.of(mesh.body(), mesh.round())) {
            for (int offset = 0; offset < vertices.size(); offset += 4) {
                double[][] p = new double[4][];
                double[][] packed = new double[4][];
                for (int j = 0; j < 4; j++) {
                    var vertex = vertices.get(offset + j);
                    require(Double.isFinite(vertex.p().x) && Double.isFinite(vertex.p().y) && Double.isFinite(vertex.p().z)
                        && Float.isFinite((float)vertex.p().x) && Float.isFinite((float)vertex.p().y) && Float.isFinite((float)vertex.p().z), "invalid CPU/GPU position");
                    require(Float.isFinite(vertex.alpha()) && vertex.alpha() >= 0 && vertex.alpha() <= 1
                        && Float.isFinite(vertex.u()) && Float.isFinite(vertex.v()), "invalid envelope/UV");
                    for (float channel : vertex.rgb()) require(Float.isFinite(channel) && channel >= 0 && channel <= 1, "invalid material tint");
                    p[j] = project(vertex.p(), clip, width, height);
                    packed[j] = project(new Vec3((float)vertex.p().x, (float)vertex.p().y, (float)vertex.p().z), clip, width, height);
                    require(p[j][3] > 0 && p[j][2] >= -1 - 2e-7 && p[j][2] <= 1 + 2e-7, "halo vertex crossed the actual near/far planes");
                    if (!bounds.empty()) {
                        double x = Math.max(0, Math.min(width, p[j][0])), y = Math.max(0, Math.min(height, p[j][1]));
                        require(x >= bounds.x() - 1e-4 && x <= bounds.x() + bounds.width() + 1e-4
                            && y >= bounds.y() - 1e-4 && y <= bounds.y() + bounds.height() + 1e-4, "scissor misses a projected primitive");
                    }
                }
                // Independent screen determinants detect crossed rails, concave
                // quads and winding inversions; a mere finite miter cannot.
                require(cross(p[0], p[1], p[2]) > 0 && cross(p[0], p[2], p[3]) > 0, "halo quad triangles fold or reverse");
                require(cross(packed[0], packed[1], packed[2]) > 0 && cross(packed[0], packed[2], packed[3]) > 0, "float vertex transport folded or collapsed a halo quad");
                for (int j = 0; j < 4; j++) {
                    require(cross(p[j], p[(j + 1) % 4], p[(j + 2) % 4]) > 0, "nonconvex halo quad");
                    require(cross(packed[j], packed[(j + 1) % 4], packed[(j + 2) % 4]) > 0, "nonconvex transported halo quad");
                }
                if (vertices == mesh.round()) {
                    float coverage = Math.abs(vertices.get(offset).u());
                    require(coverage > 0 && coverage <= 1, "invalid round flat coverage");
                    for (int j = 0; j < 4; j++) {
                        close(Math.abs(vertices.get(offset + j).u()), coverage, 0, "round coverage is not flat");
                        close(Math.abs(vertices.get(offset + j).v()), 1, 0, "round disk y UV is not normalized");
                    }
                }
            }
        }
    }

    private static double maxCoverage(HaloGeometry.Mesh mesh, Matrix4f clip, int width, int height, double x, double y) {
        double result = 0;
        for (var vertices : List.of(mesh.body(), mesh.round())) for (int i = 0; i < vertices.size(); i += 4) {
            double[][] screen = new double[4][];
            for (int j = 0; j < 4; j++) screen[j] = project(vertices.get(i + j).p(), clip, width, height);
            for (int[] indices : List.of(new int[]{0, 1, 2}, new int[]{0, 2, 3})) {
                double[] p = {x, y};
                double area = cross(screen[indices[0]], screen[indices[1]], screen[indices[2]]);
                double a = cross(screen[indices[1]], screen[indices[2]], p) / area;
                double b = cross(screen[indices[2]], screen[indices[0]], p) / area;
                double c = 1 - a - b;
                if (a < -1e-8 || b < -1e-8 || c < -1e-8) continue;
                var v0 = vertices.get(i + indices[0]); var v1 = vertices.get(i + indices[1]); var v2 = vertices.get(i + indices[2]);
                double u = v0.u() * a + v1.u() * b + v2.u() * c;
                double v = v0.v() * a + v1.v() * b + v2.v() * c;
                double alpha = v0.alpha() * a + v1.alpha() * b + v2.alpha() * c;
                double coverage, distance;
                if (vertices == mesh.round()) {
                    coverage = Math.abs(v0.u()); distance = Math.hypot(u / coverage, v);
                } else {
                    coverage = u; distance = Math.abs(v * 2 - 1);
                }
                result = Math.max(result, alpha * coverage * profile(distance));
            }
        }
        return result;
    }

    private static double profile(double distance) { return distance > 1 ? 0 : Math.max(0, Math.exp(-5 * distance * distance) - Math.exp(-5)); }
    private static double cross(double[] a, double[] b, double[] c) { return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]); }
    private static void close(double actual, double expected, double tolerance, String label) { require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, label + ": " + actual + " != " + expected); }
    private static void require(boolean condition, String label) { assertions++; if (!condition) throw new AssertionError(label); }
}
