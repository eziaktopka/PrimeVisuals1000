package primevisuals;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Silent lightning bolt after a kill: fractal bolt that strikes down from the sky in 3 ticks, branches, flicker,
 * ground flash and a faint screen flash. Drawn as glowing additive ribbons.
 */
public class Bolts {
    private static final int LIFE = 22;
    private static final float[] FLICKER = {1f, 1f, 1f, 0.5f, 1f, 0.55f, 0.95f, 0.7f, 0.9f, 0.6f, 0.85f, 0.7f};
    private static final List<Bolt> ACTIVE = new ArrayList<>();
    private static float flash;

    private record Branch(List<Vec3d> pts, float scale, int attach) {}

    private static class Bolt {
        double x, y, z;
        int rgb;
        int age;
        List<Vec3d> main;
        final List<Branch> branches = new ArrayList<>();
    }

    public static int colorOf(String id) {
        return switch (id) {
            case "blue" -> 0x4FA8FF;
            case "purple" -> 0xB066FF;
            case "gold" -> 0xFFC93A;
            case "red" -> 0xFF4545;
            default -> 0xFFFFFF;
        };
    }

    // ------------------------------------------------------------------ generation

    private static List<Vec3d> jag(Vec3d a, Vec3d b, double amp, int iters, ThreadLocalRandom r) {
        List<Vec3d> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        for (int it = 0; it < iters; it++) {
            List<Vec3d> next = new ArrayList<>();
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3d p = pts.get(i);
                Vec3d q = pts.get(i + 1);
                Vec3d m = p.add(q).multiply(0.5);
                m = m.add((r.nextDouble() - 0.5) * 2 * amp, (r.nextDouble() - 0.5) * amp * 0.3, (r.nextDouble() - 0.5) * 2 * amp);
                next.add(p);
                next.add(m);
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            amp *= 0.55;
        }
        return pts;
    }

    public static void spawn(ClientWorld w, double x, double y, double z, String color) {
        if (color == null || "off".equals(color)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Bolt b = new Bolt();
        b.x = x;
        b.y = y;
        b.z = z;
        b.rgb = colorOf(color);
        Vec3d start = new Vec3d(x + (r.nextDouble() - 0.5) * 10, y + 36 + r.nextDouble() * 8, z + (r.nextDouble() - 0.5) * 10);
        Vec3d end = new Vec3d(x, y + 0.05, z);
        b.main = jag(start, end, 5.5, 6, r);
        int size = b.main.size();
        for (int k = 0; k < 11; k++) {
            int idx = 8 + r.nextInt(size - 16);
            Vec3d p = b.main.get(idx);
            double dirX = (r.nextDouble() - 0.5) * 2;
            double dirZ = (r.nextDouble() - 0.5) * 2;
            double len = 3 + r.nextDouble() * 6;
            Vec3d q = new Vec3d(p.x + dirX * len, Math.max(y + 0.05, p.y - 6 - r.nextDouble() * 9), p.z + dirZ * len);
            List<Vec3d> br = jag(p, q, 2.2, 4, r);
            b.branches.add(new Branch(br, 0.55f, idx));
            if (r.nextDouble() < 0.5) {
                Vec3d m = br.get(br.size() / 2);
                Vec3d q2 = new Vec3d(m.x + (r.nextDouble() - 0.5) * 5, Math.max(y + 0.05, m.y - 3 - r.nextDouble() * 4), m.z + (r.nextDouble() - 0.5) * 5);
                b.branches.add(new Branch(jag(m, q2, 1.0, 3, r), 0.3f, idx));
            }
        }
        ACTIVE.add(b);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && mc.player.squaredDistanceTo(x, y, z) < 3600) flash = 1f;
        sparks(w, b);
    }

    private static void sparks(ClientWorld w, Bolt b) {
        if (w == null) return;
        for (int i = 0; i < b.main.size(); i += 2) {
            Vec3d p = b.main.get(i);
            w.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    public static void tick(ClientWorld w) {
        flash = Math.max(0f, flash - 0.25f);
        for (Iterator<Bolt> it = ACTIVE.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            b.age++;
            if (b.age == 4 || b.age == 8) sparks(w, b);
            if (b.age > LIFE) it.remove();
        }
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Bolts::render);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (flash <= 0f) return;
            int a = (int) (255 * 0.18f * flash);
            ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), (a << 24) | 0xFFFFFF);
        });
    }

    // ------------------------------------------------------------------ rendering

    private static void render(WorldRenderContext ctx) {
        if (ACTIVE.isEmpty()) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        for (Bolt b : ACTIVE) drawBolt(vc, mat, cam, b);
        ms.pop();
        if (vcp instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.getLightning());
        }
    }

    private static void drawBolt(VertexConsumer vc, Matrix4f mat, Vec3d cam, Bolt b) {
        float f = b.age < FLICKER.length ? FLICKER[b.age] : 0.6f;
        if (b.age >= 14) f *= Math.max(0f, (LIFE - b.age) / 8.0f);
        if (f <= 0f) return;
        int size = b.main.size();
        // the bolt strikes down from the sky during the first 3 ticks
        double grow = Math.min(1.0, (b.age + 1) / 3.0);
        int reveal = (int) Math.ceil(grow * (size - 1));

        // ground flash once the bolt has arrived
        if (b.age >= 2 && b.age <= 6) {
            double rad = 3.8 - (b.age - 2) * 0.55;
            Vec3d c = new Vec3d(b.x, b.y + 0.06, b.z);
            float al = 0.7f * f;
            for (int i = 0; i < 18; i++) {
                double a1 = i * (Math.PI * 2 / 18);
                double a2 = (i + 1) * (Math.PI * 2 / 18);
                Vec3d e1 = new Vec3d(b.x + Math.cos(a1) * rad, b.y + 0.06, b.z + Math.sin(a1) * rad);
                Vec3d e2 = new Vec3d(b.x + Math.cos(a2) * rad, b.y + 0.06, b.z + Math.sin(a2) * rad);
                Rings.vert(vc, mat, c, b.rgb, al);
                Rings.vert(vc, mat, c, b.rgb, al);
                Rings.vert(vc, mat, e2, b.rgb, 0f);
                Rings.vert(vc, mat, e1, b.rgb, 0f);
            }
        }

        for (int i = 0; i < reveal; i++) {
            Vec3d p = b.main.get(i);
            Vec3d q = b.main.get(i + 1);
            double wp = 0.035 + 0.003 * p.distanceTo(cam);
            double wq = 0.035 + 0.003 * q.distanceTo(cam);
            beam(vc, mat, cam, p, q, wp, wq, b.rgb, f, f);
        }
        for (Branch br : b.branches) {
            if (br.attach() > reveal) continue;
            int n = br.pts().size();
            for (int i = 0; i + 1 < n; i++) {
                Vec3d p = br.pts().get(i);
                Vec3d q = br.pts().get(i + 1);
                float taper = 1f - (i / (float) n) * 0.6f;
                double wp = (0.035 + 0.003 * p.distanceTo(cam)) * br.scale() * taper;
                double wq = (0.035 + 0.003 * q.distanceTo(cam)) * br.scale() * taper;
                beam(vc, mat, cam, p, q, wp, wq, b.rgb, f * 0.75f, f * 0.55f);
            }
        }
    }

    /** One bolt segment: four additive layers (wide haze, glow, bright body, white-hot core). */
    private static void beam(VertexConsumer vc, Matrix4f mat, Vec3d cam, Vec3d a, Vec3d b,
                             double wa, double wb, int rgb, float aa, float ab) {
        Vec3d d = b.subtract(a);
        if (d.lengthSquared() < 1.0e-9) return;
        Vec3d dn = d.normalize();
        Vec3d view = a.add(b).multiply(0.5).subtract(cam);
        Vec3d side = dn.crossProduct(view);
        double sl = side.length();
        if (sl < 1.0e-6) return;
        side = side.multiply(1.0 / sl);
        double[] ws = {7.0, 3.0, 1.6, 1.0};
        float[] als = {0.07f, 0.20f, 0.50f, 1.0f};
        for (int k = 0; k < 4; k++) {
            int c = k == 3 ? Rings.mixWhite(rgb, 0.8f) : rgb;
            Vec3d sa = side.multiply(wa * ws[k]);
            Vec3d sb = side.multiply(wb * ws[k]);
            Rings.vert(vc, mat, a.subtract(sa), c, aa * als[k]);
            Rings.vert(vc, mat, a.add(sa), c, aa * als[k]);
            Rings.vert(vc, mat, b.add(sb), c, ab * als[k]);
            Rings.vert(vc, mat, b.subtract(sb), c, ab * als[k]);
        }
    }
}
