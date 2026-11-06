package dev.aianim.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aianim.AiAnimatorClient;
import dev.aianim.AiConfig;
import dev.aianim.Msg;
import dev.aianim.anim.LiveAnimator;
import dev.aianim.anim.StepPool;
import dev.aianim.anim.Variant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides when to ask the AI for more step variants, builds the prompt and parses the answer.
 * The AI never runs "per frame": it writes batches in the background while the mod plays the pool.
 */
public final class AiDirector
{
    private static final int LOW_POOL = 8;
    private static final long MIN_GAP_MS = 3000;
    private static final long REFRESH_MS = 15000;

    private static final String SYSTEM = """
        You are an animation director for a Minecraft character that walks live.
        You reply with ONLY one JSON object. No prose, no markdown, no code fences.

        Schema:
        {
          "mood": "<max 8 words describing the walk>",
          "idle": { "<bone>": [rx, ry, rz], ... },
          "variants": [
            {
              "foot": "L" or "R",
              "stride": 0.6 to 1.5,
              "bob": 0.0 to 0.12,
              "contact": { "<bone>": [rx, ry, rz], ... },
              "passing": { "<bone>": [rx, ry, rz], ... }
            }
          ]
        }

        Rules:
        - All angles are degrees. Keep every angle between -70 and 70.
        - "contact" is the pose at the moment the named foot lands in front. "passing" is the pose half a step later.
        - For foot "L" the left leg is forward at contact, and the arms counter-swing: left arm back, right arm forward.
          For foot "R" everything is mirrored. Alternate feet fairly.
        - Positive rx swings the end of a limb forward (the direction the character faces). Rotating around z tilts sideways, y twists.
        - Use ONLY bone names from the list you are given, exactly as written. Animate the 6 to 12 bones that matter most
          (legs, arms, hips/body, head, tail, ears, etc. if the rig has them).
        - Every variant must be clearly different from the others: change arm swing, hip sway, head tilt, stride and attitude,
          while staying true to the requested style. Subtle is fine, copies are not.
        - Think like an animator: weight, overlap, secondary motion, personality.
        - "idle" is how the character stands when it is not walking.
        """;

    private static volatile boolean inFlight;
    private static volatile long lastRequest;
    private static volatile String style = "";
    private static volatile boolean warnedNoKey;
    private static volatile boolean warnedNoModel;

    private AiDirector() {}

    public static String style()
    {
        return style;
    }

    public static void setStyle(String text)
    {
        style = text == null ? "" : text.trim();
        StepPool.clear();
        LiveAnimator.reset();
        lastRequest = 0;
        warnedNoKey = false;
        warnedNoModel = false;
    }

    public static boolean inFlight()
    {
        return inFlight;
    }

    /** Called every client tick. */
    public static void tick()
    {
        if (!LiveAnimator.enabled || style.isEmpty() || inFlight)
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastRequest < MIN_GAP_MS)
        {
            return;
        }

        boolean need = StepPool.size() < LOW_POOL || (LiveAnimator.moving && now - lastRequest > REFRESH_MS);

        if (!need)
        {
            return;
        }

        List<String> bones = LiveAnimator.bones;

        if (bones.isEmpty())
        {
            if (!warnedNoModel)
            {
                warnedNoModel = true;
                Msg.say("Morph into a BBS model form first (the AI needs its bones).");
            }

            return;
        }

        String key = AiConfig.resolveKey();

        if (key.isEmpty())
        {
            if (!warnedNoKey)
            {
                warnedNoKey = true;
                Msg.say("No API key. Run /aianim key <your key> or set it in config/bbs_ai_animator.json.");
            }

            return;
        }

        request(key, bones);
    }

    private static void request(String key, List<String> bones)
    {
        inFlight = true;
        lastRequest = System.currentTimeMillis();

        String user = "Style: " + style
            + "\nBones (use only these): " + String.join(", ", bones)
            + "\nBatch number: " + (StepPool.batches() + 1)
            + (StepPool.mood().isEmpty() ? "" : "\nPrevious batch mood: " + StepPool.mood() + " (evolve it, do not repeat it)")
            + "\nReturn 6 variants: 3 with foot L and 3 with foot R.";

        AnthropicClient.ask(key, AiConfig.get().model, SYSTEM, user, 2600).whenComplete((text, error) ->
        {
            inFlight = false;

            if (error != null)
            {
                Throwable cause = error.getCause() != null ? error.getCause() : error;

                AiAnimatorClient.LOGGER.warn("AI request failed: {}", cause.toString());
                Msg.say("AI request failed: " + cause.getMessage());
                lastRequest = System.currentTimeMillis() + 5000;

                return;
            }

            try
            {
                parse(text, bones);
            }
            catch (Exception e)
            {
                AiAnimatorClient.LOGGER.warn("Could not parse AI answer: {}", text, e);
                Msg.say("The AI answer could not be read, retrying.");
            }
        });
    }

    private static void parse(String text, List<String> bones)
    {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start < 0 || end <= start)
        {
            throw new IllegalArgumentException("no JSON object");
        }

        JsonObject root = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();

        Map<String, String> exact = new HashMap<>();

        for (String bone : bones)
        {
            exact.put(bone.toLowerCase(), bone);
        }

        Map<String, float[]> idle = pose(root.has("idle") ? root.getAsJsonObject("idle") : null, exact);
        List<Variant> variants = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("variants");

        for (JsonElement element : array)
        {
            try
            {
                JsonObject v = element.getAsJsonObject();
                boolean left = !v.has("foot") || !v.get("foot").getAsString().trim().toUpperCase().startsWith("R");
                float stride = v.has("stride") ? clamp(v.get("stride").getAsFloat(), 0.5F, 1.8F) : 1F;
                float bob = v.has("bob") ? clamp(v.get("bob").getAsFloat(), 0F, 0.2F) : 0.04F;
                Map<String, float[]> contact = pose(v.getAsJsonObject("contact"), exact);
                Map<String, float[]> passing = pose(v.getAsJsonObject("passing"), exact);

                if (!contact.isEmpty() || !passing.isEmpty())
                {
                    variants.add(new Variant(left, stride, bob, contact, passing));
                }
            }
            catch (Exception ignored)
            {}
        }

        if (variants.isEmpty())
        {
            throw new IllegalArgumentException("no usable variants");
        }

        String mood = root.has("mood") ? root.get("mood").getAsString() : "";
        boolean first = StepPool.size() == 0;

        StepPool.add(variants, idle, mood);

        if (first)
        {
            Msg.say("Animating: " + (mood.isEmpty() ? style : mood) + ". Start walking!");
        }
    }

    private static Map<String, float[]> pose(JsonObject object, Map<String, String> exact)
    {
        Map<String, float[]> out = new HashMap<>();

        if (object == null)
        {
            return out;
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            String bone = exact.get(entry.getKey().toLowerCase());

            if (bone == null || !entry.getValue().isJsonArray())
            {
                continue;
            }

            JsonArray values = entry.getValue().getAsJsonArray();

            if (values.size() < 3)
            {
                continue;
            }

            out.put(bone, new float[] {
                clamp(values.get(0).getAsFloat(), -80F, 80F),
                clamp(values.get(1).getAsFloat(), -80F, 80F),
                clamp(values.get(2).getAsFloat(), -80F, 80F)
            });
        }

        return out;
    }

    private static float clamp(float v, float min, float max)
    {
        return Math.max(min, Math.min(max, v));
    }
}
