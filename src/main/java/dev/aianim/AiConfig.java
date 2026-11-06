package dev.aianim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Stored in .minecraft/config/bbs_ai_animator.json. Never commit this file. */
public class AiConfig
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AiConfig instance = new AiConfig();

    /** Anthropic API key. If empty, the ANTHROPIC_API_KEY environment variable is used. */
    public String apiKey = "";
    public String model = "claude-haiku-5-5";
    /** Blocks travelled per step at stride 1.0. */
    public double stepLength = 1.2;
    /** Multiplies every animated angle. */
    public double strength = 1.0;
    /** Negates the X swing of every bone. Toggle if limbs swing the wrong way for your model. */
    public boolean flipSwing = false;

    public static AiConfig get()
    {
        return instance;
    }

    public static Path path()
    {
        return FabricLoader.getInstance().getConfigDir().resolve("bbs_ai_animator.json");
    }

    public static void load()
    {
        try
        {
            Path path = path();

            if (Files.exists(path))
            {
                AiConfig loaded = GSON.fromJson(Files.readString(path), AiConfig.class);

                if (loaded != null)
                {
                    instance = loaded;
                }
            }

            save();
        }
        catch (Exception e)
        {
            AiAnimatorClient.LOGGER.error("Could not load config", e);
        }
    }

    public static void save()
    {
        try
        {
            Files.writeString(path(), GSON.toJson(instance));
        }
        catch (Exception e)
        {
            AiAnimatorClient.LOGGER.error("Could not save config", e);
        }
    }

    public static String resolveKey()
    {
        String key = instance.apiKey == null ? "" : instance.apiKey.trim();

        if (key.isEmpty())
        {
            String env = System.getenv("ANTHROPIC_API_KEY");

            key = env == null ? "" : env.trim();
        }

        return key;
    }
}
