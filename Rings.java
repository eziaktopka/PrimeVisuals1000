package primevisuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Rings around you / the player you fight, drawn as glowing ribbons (ground circles + orbiting comets). */
public class Rings {

    public static int ringRgb(String id, double time) {
        return switch (id) {
            case "white" -> 0xFFFFFF;
            case "purple" -> 0xB066FF;
            case "green" -> 0x50E070;
            case "orange" -> 0xFF9030;
            case "ice" -> 0xA0E8FF;
            case "pink" -> 0xFF70C0;
            case "rainbow" -> java.awt.Color.HSBtoRGB((float) ((time * 0.3) % 1.0), 0.85f, 1.0f) & 0xFFFFFF;
            default -> 0x3FA0FF;
        };
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Rings::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = Config.INSTANCE;
        Entity fight = Fx.fightTarget();
        boolean ringT = fight != null && cfg.ringTarget && fight != mc.player;
        boolean boxT = fight != null && cfg.boxTarget && fight != mc.player;
        boolean selfRing = cfg.ringSelf && mc.player != null;
        if (!ringT && !boxT && !selfRing) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        double time = System.nanoTime() / 1_000_000_000.0;

        String st = cfg.ringStyle == null ? "swoosh" : cfg.ringStyle;
        boolean ground = st.equals("ground") || st.equals("both");
        boolean swoosh = st.equals("swoosh") || st.equals("both");
        boolean comet = st.equals("comet") || st.equals("orbit");
        if (selfRing) {
            boolean visible = cfg.auraFirstPerson || !mc.options.getPerspective().isFirstPerson();
            drawRings(vc, mat, cam, mc.player, cfg.ring, ground, swoosh && visible, comet && visible, time);
        }
        if (ringT) {
            drawRings(vc, mat, cam, fight, cfg.ring, ground, swoosh, comet, time);
        }
        if (boxT) {
            drawBox(vc, mat, cam, fight, ringRgb(cfg.ring, time));
        }
        ms.pop();
        if (vcp instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.getLightning());
        }
    }

    private static void drawRings(VertexConsumer vc, Matrix4f mat, Vec3d cam, Entity e, String colorId,
                                  boolean ground, boolean swoosh, boolean comet, double time) {
        int rgb = ringRgb(colorId, time);
        double cx = e.getX();
        double cy = e.getY();
        double cz = e.getZ();
        if (ground) {
            circle(vc, mat, cam, cx, cy + 0.04, cz, 0.95 + 0.05 * Math.sin(time * 3), 0.05, rgb, 0.95f, time * 0.5, 48);
            circle(vc, mat, cam, cx, cy + 0.04, cz, 1.3 + 0.04 * Math.sin(time * 2.3), 0.03, rgb, 0.6f, -time * 0.4, 56);
            double ph = (time * 0.9) % 1.0;
            circle(vc, mat, cam, cx, cy + 0.045, cz, 0.5 + ph * 1.3, 0.03, rgb, (float) ((1 - ph) * 0.8), 0, 48);
        }
        if (swoosh) {
            // wide crescent "swooshes" that taper to a point at both ends, circling the body on two crossing orbits
            double mid = cy + e.getHeight() * 0.5;
            for (int o = 0; o < 2; o++) {
                double tilt = o == 0 ? 0.55 : -0.55;
                double dir = o == 0 ? 1 : -1;
                double start = time * 3.2 * dir + o * Math.PI;
                double span = 1.9;
                double radius = o == 0 ? 0.92 : 0.8;
                double widthMax = o == 0 ? 0.17 : 0.10;
                float aMax = o == 0 ? 0.95f : 0.6f;
                int n = 24;
                for (int i = 0; i < n; i++) {
                    double t1 = i / (double) n;
                    double t2 = (i + 1) / (double) n;
                    float p1 = (float) Math.pow(Math.sin(Math.PI * t1), 0.7);
                    float p2 = (float) Math.pow(Math.sin(Math.PI * t2), 0.7);
                    Vec3d p = orbitPoint(cx, mid, cz, radius, start + dir * t1 * span, tilt);
                    Vec3d q = orbitPoint(cx, mid, cz, radius, start + dir * t2 * span, tilt);
                    ribbon(vc, mat, cam, p, q, widthMax * p1, widthMax * p2, rgb, aMax * p1, aMax * p2, false);
                }
                int segs = 40;
                for (int i = 0; i < segs; i++) {
                    Vec3d p = orbitPoint(cx, mid, cz, radius, i * (Math.PI * 2 / segs), tilt);
                    Vec3d q = orbitPoint(cx, mid, cz, radius, (i + 1) * (Math.PI * 2 / segs), tilt);
                    ribbon(vc, mat, cam, p, q, 0.01, 0.01, rgb, 0.12f, 0.12f, false);
                }
            }
        }
        if (comet) {
            double mid = cy + e.getHeight() * 0.45;
            double radius = 0.85;
            for (int o = 0; o < 2; o++) {
                double tilt = o == 0 ? 0.5 : -0.5;
                double dir = o == 0 ? 1 : -1;
                double head = time * 5.5 * dir + o * Math.PI;
                int segs = 40;
                for (int i = 0; i < segs; i++) {
                    Vec3d p = orbitPoint(cx, mid, cz, radius, i * (Math.PI * 2 / segs), tilt);
                    Vec3d q = orbitPoint(cx, mid, cz, radius, (i + 1) * (Math.PI * 2 / segs), tilt);
                    ribbon(vc, mat, cam, p, q, 0.012, 0.012, rgb, 0.18f, 0.18f, false);
                }
                int n = 26;
                for (int i = 0; i < n; i++) {
                    double a1 = head - dir * (i * 0.07);
                    double a2 = head - dir * ((i + 1) * 0.07);
                    float al1 = (float) Math.pow(1 - i / (double) n, 1.5);
                    float al2 = (float) Math.pow(1 - (i + 1) / (double) n, 1.5);
                    Vec3d p = orbitPoint(cx, mid, cz, radius, a1, tilt);
                    Vec3d q = orbitPoint(cx, mid, cz, radius, a2, tilt);
                    ribbon(vc, mat, cam, p, q, 0.025 + 0.07 * al1, 0.025 + 0.07 * al2, rgb, al1, al2, false);
                }
                Vec3d headPos = orbitPoint(cx, mid, cz, radius, head, tilt);
                orb(vc, mat, cam, headPos, 0.30, rgb, 0.40f);
                orb(vc, mat, cam, headPos, 0.11, mixWhite(rgb, 0.8f), 0.95f);
            }
        }
    }

    /** Soft round glow that always faces the camera. */
    static void orb(VertexConsumer vc, Matrix4f mat, Vec3d cam, Vec3d pos, double radius, int rgb, float alpha) {
        Vec3d view = pos.subtract(cam);
        Vec3d right = view.crossProduct(new Vec3d(0, 1, 0));
        double rl = right.length();
        if (rl < 1.0e-6) return;
        right = right.multiply(1.0 / rl);
        Vec3d up = right.crossProduct(view);
        double ul = up.length();
        if (ul < 1.0e-6) return;
        up = up.multiply(1.0 / ul);
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a1 = i * (Math.PI * 2 / n);
            double a2 = (i + 1) * (Math.PI * 2 / n);
            Vec3d e1 = pos.add(right.multiply(Math.cos(a1) * radius)).add(up.multiply(Math.sin(a1) * radius));
            Vec3d e2 = pos.add(right.multiply(Math.cos(a2) * radius)).add(up.multiply(Math.sin(a2) * radius));
            vert(vc, mat, pos, rgb, alpha);
            vert(vc, mat, pos, rgb, alpha);
            vert(vc, mat, e2, rgb, 0f);
            vert(vc, mat, e1, rgb, 0f);
        }
    }

    /** Glowing "glass" hitbox around an entity: thin bright edges plus very faint faces. */
    private static void drawBox(VertexConsumer vc, Matrix4f mat, Vec3d cam, Entity e, int rgb) {
        net.minecraft.util.math.Box bb = e.getBoundingBox().expand(0.03);
        double x0 = bb.minX, y0 = bb.minY, z0 = bb.minZ, x1 = bb.maxX, y1 = bb.maxY, z1 = bb.maxZ;
        Vec3d[] c = {
                new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0), new Vec3d(x1, y0, z1), new Vec3d(x0, y0, z1),
                new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0), new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1)
        };
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] ed : edges) {
            ribbon(vc, mat, cam, c[ed[0]], c[ed[1]], 0.008, 0.008, rgb, 0.9f, 0.9f, false);
        }
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        for (int[] f : faces) {
            for (int k = 0; k < 4; k++) vert(vc, mat, c[f[k]], rgb, 0.045f);
        }
    }

    private static Vec3d orbitPoint(double cx, double cy, double cz, double radius, double a, double tilt) {
        double x = Math.cos(a) * radius;
        double z = Math.sin(a) * radius;
        return new Vec3d(cx + x, cy + z * Math.sin(tilt), cz + z * Math.cos(tilt));
    }

    private static void circle(VertexConsumer vc, Matrix4f mat, Vec3d cam, double cx, double cy, double cz,
                               double radius, double halfWidth, int rgb, float alpha, double rot, int segs) {
        for (int i = 0; i < segs; i++) {
            double a1 = rot + i * (Math.PI * 2 / segs);
            double a2 = rot + (i + 1) * (Math.PI * 2 / segs);
            Vec3d p = new Vec3d(cx + Math.cos(a1) * radius, cy, cz + Math.sin(a1) * radius);
            Vec3d q = new Vec3d(cx + Math.cos(a2) * radius, cy, cz + Math.sin(a2) * radius);
            ribbon(vc, mat, cam, p, q, halfWidth, halfWidth, rgb, alpha, alpha, true);
        }
    }

    private static void ribbon(VertexConsumer vc, Matrix4f mat, Vec3d cam, Vec3d a, Vec3d b,
                               double wa, double wb, int rgb, float aa, float ab, boolean flat) {
        Vec3d d = b.subtract(a);
        if (d.lengthSquared() < 1.0e-9) return;
        Vec3d dn = d.normalize();
        Vec3d side;
        if (flat) {
            side = new Vec3d(-dn.z, 0, dn.x);
        } else {
            Vec3d view = a.add(b).multiply(0.5).subtract(cam);
            side = dn.crossProduct(view);
        }
        double sl = side.length();
        if (sl < 1.0e-6) return;
        side = side.multiply(1.0 / sl);
        double[] ws = {5.0, 2.2, 1.0};
        float[] als = {0.10f, 0.28f, 1.0f};
        for (int k = 0; k < 3; k++) {
            double wA = wa * ws[k];
            double wB = wb * ws[k];
            int c = k == 2 ? mixWhite(rgb, 0.65f) : rgb;
            Vec3d sa = side.multiply(wA);
            Vec3d sb = side.multiply(wB);
            vert(vc, mat, a.subtract(sa), c, aa * als[k]);
            vert(vc, mat, a.add(sa), c, aa * als[k]);
            vert(vc, mat, b.add(sb), c, ab * als[k]);
            vert(vc, mat, b.subtract(sb), c, ab * als[k]);
        }
    }

    static int mixWhite(int rgb, float t) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    static void vert(VertexConsumer vc, Matrix4f mat, Vec3d p, int rgb, float alpha) {
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
        vc.vertex(mat, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }
}
