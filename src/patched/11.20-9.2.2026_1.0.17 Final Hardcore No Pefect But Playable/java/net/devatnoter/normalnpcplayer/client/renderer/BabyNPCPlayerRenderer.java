package net.devatnoter.normalnpcplayer.client.renderer;

import net.devatnoter.normalnpcplayer.client.model.BabyNPCPlayerModel;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BabyNPCPlayerRenderer extends GeoEntityRenderer<BabyNPCPlayerEntity> {

    public BabyNPCPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new BabyNPCPlayerModel());

        // ขนาดเงาใต้ตัว
        this.shadowRadius = 0.35F;
    }


    @Override
    public boolean shouldRender(
            BabyNPCPlayerEntity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        // Hide only the local player's Baby passenger in first person.
        if (minecraft.player != null
                && minecraft.options.getCameraType().isFirstPerson()
                && entity.getVehicle() == minecraft.player) {
            return false;
        }

        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }
}