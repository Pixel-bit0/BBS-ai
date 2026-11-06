package dev.aianim;

import dev.aianim.ai.AiDirector;
import dev.aianim.anim.LiveAnimator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiAnimatorClient implements ClientModInitializer
{
    public static final Logger LOGGER = LoggerFactory.getLogger("bbs_ai_animator");

    @Override
    public void onInitializeClient()
    {
        AiConfig.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> AiCommands.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register((client) -> AiDirector.tick());
        WorldRenderEvents.START.register((context) -> LiveAnimator.frame(context.tickDelta()));
    }
}
