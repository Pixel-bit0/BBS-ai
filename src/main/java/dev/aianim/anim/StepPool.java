package dev.aianim.anim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** Rolling pool of AI-authored step variants plus the AI's idle pose. */
public final class StepPool
{
    private static final int MAX = 18;
    private static final Random RANDOM = new Random();

    private static final List<Variant> VARIANTS = new CopyOnWriteArrayList<>();
    private static volatile Map<String, float[]> idle = Map.of();
    private static volatile String mood = "";
    private static volatile int batches;

    private StepPool() {}

    public static synchronized void clear()
    {
        VARIANTS.clear();
        idle = Map.of();
        mood = "";
        batches = 0;
    }

    public static synchronized void add(List<Variant> incoming, Map<String, float[]> newIdle, String newMood)
    {
        VARIANTS.addAll(incoming);

        while (VARIANTS.size() > MAX)
        {
            VARIANTS.remove(0);
        }

        if (newIdle != null && !newIdle.isEmpty())
        {
            idle = newIdle;
        }

        if (newMood != null && !newMood.isBlank())
        {
            mood = newMood;
        }

        batches++;
    }

    public static int size()
    {
        return VARIANTS.size();
    }

    public static Map<String, float[]> idle()
    {
        return idle;
    }

    public static String mood()
    {
        return mood;
    }

    public static int batches()
    {
        return batches;
    }

    /** Picks a random variant for the wanted foot, preferring newer ones. Falls back to any variant. */
    public static Variant pick(boolean wantLeft)
    {
        List<Variant> snapshot = new ArrayList<>(VARIANTS);

        if (snapshot.isEmpty())
        {
            return null;
        }

        List<Variant> matching = new ArrayList<>();

        for (Variant variant : snapshot)
        {
            if (variant.left() == wantLeft)
            {
                matching.add(variant);
            }
        }

        List<Variant> source = matching.isEmpty() ? snapshot : matching;
        /* Squaring biases toward the end of the list, which holds the newest variants */
        double r = RANDOM.nextDouble();
        int index = (int) Math.min(source.size() - 1, Math.floor(Math.sqrt(r) * source.size()));

        return source.get(index);
    }
}
