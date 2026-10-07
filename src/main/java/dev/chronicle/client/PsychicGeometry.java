package dev.chronicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Continuous prismatic films and soft pressure ripples, emitted as position/color quads. */
public final class PsychicGeometry {
    private static final int SEGMENTS = 64, BANDS = 24;
    private static final double TAU = Math.PI * 2;

    /** An expanding pressure wave is a translucent ribbon with transparent inner/outer edges. */
    public static void ring(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 normal, double radius, int color, float alpha) {
        if (radius <= .001 || alpha <= 0) return;
        Frame frame = frame();
        Basis basis = basis(normal);
        double width = Math.min(.7, Math.max(.065, radius * .055));
        double inner = Math.max(0, radius - width), outer = radius + width;
        Point[][] points = new Point[5][SEGMENTS + 1];
        float opacity = Math.min(.15f, Math.max(0, alpha) * .22f);
        for (int band = 0; band <= 4; band++) {
            double t = band / 4.;
            double r = inner + (outer - inner) * t;
            float feather = (float)Math.pow(Math.sin(Math.PI * t), 1.5);
            for (int segment = 0; segment <= SEGMENTS; segment++) {
                double angle = segment * TAU / SEGMENTS;
                Vec3 radial = basis.right.scale(Math.cos(angle)).add(basis.up.scale(Math.sin(angle)));
                Vec3 point = center.add(radial.scale(r));
                points[band][segment] = tint(frame, point, basis.normal, color, opacity * feather, angle / TAU + .06 * t, false);
            }
        }
        mesh(pose, out, points, 4, SEGMENTS);
    }

    /** A smooth hemisphere or sphere; adjacent quads share positions and vertex colors. */
    public static void shell(PoseStack pose, VertexConsumer out, Vec3 center, double radius, boolean dome, int color, float alpha) {
        if (radius <= .001 || alpha <= 0) return;
        surface(pose,out,center,radius,radius,dome,color,Math.min(.3f,alpha*.85f),SEGMENTS,BANDS);
    }

    /** Finite plane protection is a filled, softly feathered disc, never an outline. */
    public static void disc(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 normal, double radius, int color, float alpha) {
        if (radius <= .001 || alpha <= 0) return;
        Frame frame = frame();
        Basis basis = basis(normal);
        int bands = 12;
        Point[][] points = new Point[bands + 1][SEGMENTS + 1];
        float opacity=Math.min(.3f,alpha*.85f);
        for (int band = 0; band <= bands; band++) {
            double t = band / (double)bands;
            float feather = (float)(1 - smooth(.82, 1, t));
            for (int segment = 0; segment <= SEGMENTS; segment++) {
                double angle = segment * TAU / SEGMENTS;
                Vec3 point = center.add(basis.right.scale(Math.cos(angle) * radius * t)).add(basis.up.scale(Math.sin(angle) * radius * t));
                // Near the center the angular phase vanishes, so its shared vertices agree.
                double phase = Math.sin(angle) * t * .18 + Math.cos(angle) * t * .12;
                points[band][segment] = tint(frame, point, basis.normal, color, opacity * feather, phase, true);
            }
        }
        mesh(pose, out, points, bands, SEGMENTS);
    }

    public static void aura(PoseStack pose, VertexConsumer out, Vec3 center, double width, double height, int color) {
        if (width <= 0 || height <= 0) return;
        surface(pose, out, center, width, height, false, color, .055f, 48, 16);
    }

    /** Damage is conveyed by fading film and impact ripples; the former wire cracks are removed. */
    public static void cracks(PoseStack pose, VertexConsumer out, Vec3 center, Vec3 normal, double radius, int color, float damage) {}

    private static void surface(PoseStack pose, VertexConsumer out, Vec3 center, double width, double height, boolean dome, int color, float opacity, int segments, int bands) {
        Frame frame = frame();
        Point[][] points = new Point[bands + 1][segments + 1];
        boolean outside=frame.camera.distanceToSqr(center)>Math.pow(Math.max(width,height),2);
        for (int band = 0; band <= bands; band++) {
            double latitude = (dome ? 0 : -Math.PI / 2) + band * (dome ? Math.PI / 2 : Math.PI) / bands;
            double horizontal = Math.cos(latitude), y = Math.sin(latitude);
            float edge = dome ? (float)smooth(0, .09, y) : 1;
            for (int segment = 0; segment <= segments; segment++) {
                double longitude = segment * TAU / segments;
                Vec3 unit = new Vec3(Math.cos(longitude) * horizontal, y, Math.sin(longitude) * horizontal);
                Vec3 point = center.add(unit.x * width, unit.y * height, unit.z * width);
                Vec3 normal = new Vec3(unit.x / width, unit.y / height, unit.z / width).normalize();
                // A spatially continuous phase prevents a seam or pinwheel at the poles.
                double phase = unit.x * .12 + unit.y * .14 + unit.z * .09;
                float far=outside&&normal.dot(frame.camera.subtract(point))<0?.35f:1;
                points[band][segment]=tint(frame,point,normal,color,opacity*edge*far,phase,true);
            }
        }
        mesh(pose, out, points, bands, segments);
    }

    private static Point tint(Frame frame, Vec3 point, Vec3 normal, int color, float opacity, double phase, boolean fresnel) {
        Vec3 eye = frame.camera.subtract(point).normalize();
        double facing = Math.abs(normal.dot(eye));
        double edge = Math.pow(1 - Math.min(1, facing), 2.5);
        float alpha=opacity*(fresnel?(float)(.5+edge*.5):1);
        double hue = frame.time * .00075 + phase + normal.dot(eye) * .12;
        int spectral = Mth.hsvToRgb((float)(hue - Math.floor(hue)), .48f, 1);
        float mix = (float)(.28 + edge * .2); // Persisted aura hue remains the majority color.
        float r = channel(color, 16) * (1 - mix) + channel(spectral, 16) * mix;
        float g = channel(color, 8) * (1 - mix) + channel(spectral, 8) * mix;
        float b = channel(color, 0) * (1 - mix) + channel(spectral, 0) * mix;
        return new Point(point,r,g,b,Math.min(.3f,Math.max(0,alpha)));
    }

    private static float channel(int color, int shift) { return ((color >> shift) & 255) / 255f; }

    private static void mesh(PoseStack pose, VertexConsumer out, Point[][] points, int bands, int segments) {
        for (int band = 0; band < bands; band++) for (int segment = 0; segment < segments; segment++) {
            vertex(pose, out, points[band][segment]);
            vertex(pose, out, points[band][segment + 1]);
            vertex(pose, out, points[band + 1][segment + 1]);
            vertex(pose, out, points[band + 1][segment]);
        }
    }

    private static void vertex(PoseStack pose, VertexConsumer out, Point point) {
        out.vertex(pose.last().pose(), (float)point.position.x, (float)point.position.y, (float)point.position.z).color(point.r, point.g, point.b, point.a).endVertex();
    }

    private static Frame frame() {
        Minecraft mc = Minecraft.getInstance();
        return new Frame(mc.gameRenderer.getMainCamera().getPosition(), mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime());
    }

    private static Basis basis(Vec3 input) {
        Vec3 normal = input == null || input.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : input.normalize();
        Vec3 right = Math.abs(normal.y) > .95 ? new Vec3(1, 0, 0) : normal.cross(new Vec3(0, 1, 0)).normalize();
        return new Basis(normal, right, normal.cross(right).normalize());
    }

    private static double smooth(double from, double to, double value) {
        double t = Math.max(0, Math.min(1, (value - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }

    private record Frame(Vec3 camera, double time) {}
    private record Basis(Vec3 normal, Vec3 right, Vec3 up) {}
    private record Point(Vec3 position, float r, float g, float b, float a) {}
    private PsychicGeometry() {}
}
