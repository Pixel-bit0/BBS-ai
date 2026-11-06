package dev.aianim;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.aianim.ai.AiDirector;
import dev.aianim.anim.LiveAnimator;
import dev.aianim.anim.StepPool;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class AiCommands
{
    private AiCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher)
    {
        dispatcher.register(literal("aianim")
            .then(literal("style").then(argument("text", StringArgumentType.greedyString()).executes((ctx) ->
            {
                String text = StringArgumentType.getString(ctx, "text");

                AiDirector.setStyle(text);
                LiveAnimator.enabled = true;
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] Style set: " + text + ". Asking the AI, walk around in a few seconds."));

                return 1;
            })))
            .then(literal("on").executes((ctx) ->
            {
                LiveAnimator.enabled = true;
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] On."));

                return 1;
            }))
            .then(literal("off").executes((ctx) ->
            {
                LiveAnimator.enabled = false;
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] Off."));

                return 1;
            }))
            .then(literal("key").then(argument("apikey", StringArgumentType.greedyString()).executes((ctx) ->
            {
                AiConfig.get().apiKey = StringArgumentType.getString(ctx, "apikey").trim();
                AiConfig.save();
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] API key saved to config/bbs_ai_animator.json."));

                return 1;
            })))
            .then(literal("model").then(argument("name", StringArgumentType.greedyString()).executes((ctx) ->
            {
                AiConfig.get().model = StringArgumentType.getString(ctx, "name").trim();
                AiConfig.save();
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] AI model: " + AiConfig.get().model));

                return 1;
            })))
            .then(literal("strength").then(argument("value", DoubleArgumentType.doubleArg(0, 3)).executes((ctx) ->
            {
                AiConfig.get().strength = DoubleArgumentType.getDouble(ctx, "value");
                AiConfig.save();
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] Strength: " + AiConfig.get().strength));

                return 1;
            })))
            .then(literal("steplength").then(argument("blocks", DoubleArgumentType.doubleArg(0.3, 4)).executes((ctx) ->
            {
                AiConfig.get().stepLength = DoubleArgumentType.getDouble(ctx, "blocks");
                AiConfig.save();
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] Step length: " + AiConfig.get().stepLength + " blocks"));

                return 1;
            })))
            .then(literal("flip").executes((ctx) ->
            {
                AiConfig.get().flipSwing = !AiConfig.get().flipSwing;
                AiConfig.save();
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] Flip swing: " + AiConfig.get().flipSwing + " (use this if limbs swing the wrong way)."));

                return 1;
            }))
            .then(literal("reroll").executes((ctx) ->
            {
                String style = AiDirector.style();

                AiDirector.setStyle(style);
                ctx.getSource().sendFeedback(Text.literal("[AI Anim] Throwing away old steps and asking for new ones."));

                return 1;
            }))
            .then(literal("status").executes((ctx) ->
            {
                String status = "on=" + LiveAnimator.enabled
                    + ", style='" + AiDirector.style() + "'"
                    + ", steps=" + StepPool.size()
                    + ", batches=" + StepPool.batches()
                    + ", waiting=" + AiDirector.inFlight()
                    + ", bones=" + LiveAnimator.bones.size()
                    + ", key=" + (AiConfig.resolveKey().isEmpty() ? "MISSING" : "set");

                ctx.getSource().sendFeedback(Text.literal("[AI Anim] " + status));

                return 1;
            })));
    }
}
