package dev.aianim.bridge;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Every BBS FS call the addon makes lives here. */
public final class BbsBridge
{
    private static String cachedModelId = "";
    private static List<String> cachedBones = List.of();

    private BbsBridge() {}

    /** @return the model form the given entity is morphed into, or null. */
    public static ModelForm modelForm(Entity entity)
    {
        Morph morph = Morph.getMorph(entity);

        if (morph == null)
        {
            return null;
        }

        Form form = morph.getForm();

        return form instanceof ModelForm model ? model : null;
    }

    /** Bone (group) names of the form's model. */
    public static List<String> bones(ModelForm form)
    {
        String id = form.model.get();

        if (id == null)
        {
            return List.of();
        }

        if (!id.equals(cachedModelId) || cachedBones.isEmpty())
        {
            ModelInstance instance = BBSModClient.getModels().getModel(id);

            if (instance == null || instance.getModel() == null)
            {
                return List.of();
            }

            cachedBones = new ArrayList<>(instance.getModel().getAllGroupKeys());
            cachedModelId = id;
        }

        return cachedBones;
    }

    /**
     * Applies rotations (degrees) as a temporary pose overlay and a vertical bob.
     * Runtime values are never saved into the form, so nothing is left behind in the film or the morph.
     */
    public static void apply(ModelForm form, Map<String, float[]> rotations, float bob)
    {
        Pose pose = new Pose();

        for (Map.Entry<String, float[]> entry : rotations.entrySet())
        {
            float[] d = entry.getValue();

            pose.get(entry.getKey()).rotate.set((float) Math.toRadians(d[0]), (float) Math.toRadians(d[1]), (float) Math.toRadians(d[2]));
        }

        Transform transform = new Transform();

        transform.translate.set(0F, bob, 0F);

        form.poseOverlay.setRuntimeValue(pose);
        form.transformOverlay.setRuntimeValue(transform);
    }

    public static void release(ModelForm form)
    {
        form.poseOverlay.setRuntimeValue(null);
        form.transformOverlay.setRuntimeValue(null);
    }
}
