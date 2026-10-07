package dev.chronicle.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

/** Luminous films contribute color without darkening terrain or writing a false opaque depth surface. */
public final class PsychicRenderType {
    public static final RenderType FILM=new RenderType("psychokinesis_prismatic_film",DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,65536,false,false,()->{
        RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE);
        RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(false);RenderSystem.disableCull();
    },()->{RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);}){};
    private PsychicRenderType(){}
}
