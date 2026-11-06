package dev.aianim.anim;

import dev.aianim.AiConfig;
import dev.aianim.bridge.BbsBridge;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs every rendered frame. Follows how far the player really walked, plays the AI's step
 * variants in sequence, and blends them smoothly (Catmull-Rom) so steps flow into each other.
 */
public final class LiveAnimator
{
    public static volatile boolean enabled = false;
    public static volatile boolean moving = false;
    /** Bones of the model the player is morphed into (empty when not a BBS model form). */
    public static volatile List<String> bones = List.of();

    private static ModelForm activeForm;
    private static boolean haveLast;
    private static double lastX;
    private static double lastZ;
    private static long lastNanos;
    private static double speed;
    private static double weight;
    private static double phase;

    private static Played prev;
    private static Played cur;
    private static Played next;

    private LiveAnimator() {}

    public static void reset()
    {
        prev = null;
        cur = null;
        next = null;
        phase = 0;
    }

    private static void release()
    {
        if (activeForm != null)
        {
            BbsBridge.release(activeForm);
            activeForm = null;
        }
    }

    public static void frame(float tickDelta)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;

        if (player == null || mc.world == null || !enabled)
        {
            release();
            haveLast = false;
            moving = false;

            return;
        }

        ModelForm form = BbsBridge.modelForm(player);

        if (form == null)
        {
            release();
            bones = List.of();
            moving = false;

            return;
        }

        if (activeForm != null && activeForm != form)
        {
            release();
        }

        bones = BbsBridge.bones(form);

        long now = System.nanoTime();
        Vec3d pos = player.getLerpedPos(tickDelta);

        if (!haveLast)
        {
            lastX = pos.x;
            lastZ = pos.z;
            lastNanos = now;
            haveLast = true;

            return;
        }

        double dt = (now - lastNanos) / 1_000_000_000D;

        if (dt <= 0.0001)
        {
            return;
        }

        double dist = Math.hypot(pos.x - lastX, pos.z - lastZ);

        lastX = pos.x;
        lastZ = pos.z;
        lastNanos = now;

        if (dist > 4)
        {
            dist = 0;
        }

        speed += (dist / dt - speed) * Math.min(1, dt * 10);
        moving = speed > 0.35;
        weight += ((moving ? 1 : 0) - weight) * Math.min(1, dt * 8);

        if (StepPool.size() == 0)
        {
            /* Nothing from the AI yet: leave the model's own animation alone */
            release();

            return;
        }

        if (cur == null)
        {
            cur = Played.from(StepPool.pick(true));
            prev = cur;
            next = Played.from(StepPool.pick(!cur.left));
            phase = 0;
        }

        if (moving)
        {
            phase += dist / (AiConfig.get().stepLength * cur.stride);
        }

        while (phase >= 1)
        {
            phase -= 1;
            prev = cur;
            cur = next;
            next = Played.from(StepPool.pick(!cur.left));
        }

        float t = (float) phase;
        float w = (float) weight;
        float strength = (float) AiConfig.get().strength;
        float flip = AiConfig.get().flipSwing ? -1F : 1F;

        /* Catmull-Rom through the key poses around the current moment */
        Map<String, float[]> p0;
        Map<String, float[]> p1;
        Map<String, float[]> p2;
        Map<String, float[]> p3;
        float local;

        if (t < 0.5F)
        {
            p0 = prev.passing;
            p1 = cur.contact;
            p2 = cur.passing;
            p3 = next.contact;
            local = t / 0.5F;
        }
        else
        {
            p0 = cur.contact;
            p1 = cur.passing;
            p2 = next.contact;
            p3 = next.passing;
            local = (t - 0.5F) / 0.5F;
        }

        Set<String> names = new HashSet<>(StepPool.idle().keySet());

        names.addAll(p0.keySet());
        names.addAll(p1.keySet());
        names.addAll(p2.keySet());
        names.addAll(p3.keySet());

        Map<String, float[]> out = new HashMap<>();

        for (String bone : names)
        {
            float[] idle = StepPool.idle().get(bone);
            float[] result = new float[3];

            for (int axis = 0; axis < 3; axis++)
            {
                float walk = catmull(value(p0, bone, axis), value(p1, bone, axis), value(p2, bone, axis), value(p3, bone, axis), local);
                float rest = idle == null ? 0F : idle[axis];

                result[axis] = (rest * (1F - w) + walk * w) * strength;
            }

            result[0] *= flip;
            out.put(bone, result);
        }

        float bobBlend = prev == null ? 0F : cur.bob + (next.bob - cur.bob) * t;
        float bob = (float) (bobBlend * Math.sin(Math.PI * t)) * w * strength;

        activeForm = form;
        BbsBridge.apply(form, out, bob);
    }

    private static float value(Map<String, float[]> pose, String bone, int axis)
    {
        float[] v = pose.get(bone);

        return v == null ? 0F : v[axis];
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t)
    {
        float t2 = t * t;
        float t3 = t2 * t;

        return 0.5F * ((2F * p1) + (-p0 + p2) * t + (2F * p0 - 5F * p1 + 4F * p2 - p3) * t2 + (-p0 + 3F * p1 - 3F * p2 + p3) * t3);
    }
}
