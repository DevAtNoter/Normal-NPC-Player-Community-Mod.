package net.devatnoter.normalnpcplayer.client.renderer;

import net.devatnoter.normalnpcplayer.client.model.BabyNPCPlayerModel;
import net.devatnoter.normalnpcplayer.entity.BabyNPCPlayerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BabyNPCPlayerRenderer extends GeoEntityRenderer<BabyNPCPlayerEntity> {

    public BabyNPCPlayerRenderer(EntityRendererProvider.Context context) {
        super(context, new BabyNPCPlayerModel());

        // ขนาดเงาใต้ตัว
        this.shadowRadius = 0.35F;
    }
}