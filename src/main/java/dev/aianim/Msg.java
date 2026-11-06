package dev.aianim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Chat feedback that is safe to call from any thread. */
public final class Msg
{
    private Msg() {}

    public static void say(String text)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        mc.execute(() ->
        {
            if (mc.player != null)
            {
                mc.player.sendMessage(Text.literal("[AI Anim] " + text), false);
            }
        });
    }
}
