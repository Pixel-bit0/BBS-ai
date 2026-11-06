package dev.aianim.anim;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** A variant with per-step randomness applied, so no two steps are identical. */
public final class Played
{
    private static final Random RANDOM = new Random();

    public final boolean left;
    public final float stride;
    public final float bob;
    public final Map<String, float[]> contact;
    public final Map<String, float[]> passing;

    private Played(boolean left, float stride, float bob, Map<String, float[]> contact, Map<String, float[]> passing)
    {
        this.left = left;
        this.stride = stride;
        this.bob = bob;
        this.contact = contact;
        this.passing = passing;
    }

    public static Played from(Variant variant)
    {
        float scale = 0.88F + RANDOM.nextFloat() * 0.24F;
        float stride = clamp(variant.stride() * (0.92F + RANDOM.nextFloat() * 0.16F), 0.5F, 1.8F);
        float bob = variant.bob() * (0.85F + RANDOM.nextFloat() * 0.3F);

        return new Played(variant.left(), stride, bob, jitter(variant.contact(), scale), jitter(variant.passing(), scale));
    }

    private static Map<String, float[]> jitter(Map<String, float[]> source, float scale)
    {
        Map<String, float[]> out = new HashMap<>();

        for (Map.Entry<String, float[]> entry : source.entrySet())
        {
            float[] in = entry.getValue();
            float[] result = new float[3];

            for (int i = 0; i < 3; i++)
            {
                result[i] = in[i] * scale + (RANDOM.nextFloat() - 0.5F) * 3F;
            }

            out.put(entry.getKey(), result);
        }

        return out;
    }

    private static float clamp(float v, float min, float max)
    {
        return Math.max(min, Math.min(max, v));
    }
}
