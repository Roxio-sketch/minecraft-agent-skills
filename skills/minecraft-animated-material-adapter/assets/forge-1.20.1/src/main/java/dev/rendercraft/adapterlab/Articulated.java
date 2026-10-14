package dev.rendercraft.adapterlab;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import java.util.function.Consumer;
/** Two rigid child parts, preserved bind pose, bone-local shader and blade-tip attachment. */
final class Articulated extends BlockEntityWithoutLevelRenderer {
 static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,Demo.ID);
 static final RegistryObject<Item> ITEM=ITEMS.register("articulated",()->new Item(new Item.Properties().stacksTo(1)){
  @Override public void initializeClient(Consumer<IClientItemExtensions> c){c.accept(new IClientItemExtensions(){@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){return instance();}});}
 });
 static Articulated renderer;static ShaderInstance shader;static RenderType type;final ModelPart root,blade;static org.joml.Vector3f lastTip=new org.joml.Vector3f();
 static Articulated instance(){if(renderer==null)renderer=new Articulated();return renderer;}
 Articulated(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());MeshDefinition mesh=new MeshDefinition();var main=mesh.getRoot();main.addOrReplaceChild("grip",CubeListBuilder.create().texOffs(0,0).addBox(-1,0,-1,2,7,2),PartPose.ZERO);main.addOrReplaceChild("blade",CubeListBuilder.create().texOffs(0,0).addBox(-2,-10,-.5f,4,10,1),PartPose.offset(0,0,0));root=LayerDefinition.create(mesh,16,16).bakeRoot();blade=root.getChild("blade");}
 static void register(RegisterShadersEvent e){try{e.registerShader(new ShaderInstance(e.getResourceProvider(),new ResourceLocation(Demo.ID,"material"),DefaultVertexFormat.NEW_ENTITY),s->{shader=s;type=Types.make();});}catch(java.io.IOException ex){throw new RuntimeException(ex);}}
 static void reset(){renderer=null;}
 @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource source,int light,int overlay){if(shader==null||type==null)return;
  if(source instanceof MultiBufferSource.BufferSource buffer)buffer.endBatch();
  float seconds=Minecraft.getInstance().level==null?0:(Minecraft.getInstance().level.getGameTime()%100000+Minecraft.getInstance().getFrameTime())/20f;Recipe r=Demo.Client.recipe;
  root.getAllParts().forEach(ModelPart::resetPose);blade.zRot=(float)Math.sin(seconds*r.frequency*Math.PI*2)*r.amplitude;
  pose.pushPose();try{
   pose.translate(.5,.65,.5);pose.scale(1,-1,-1);shader.safeGetUniform("Time").set(seconds);shader.safeGetUniform("Tint").set(r.color[0],r.color[1],r.color[2]);
   // Consumer submitted immediately, so per-item uniforms do not leak to deferred neighbors.
   var own=MultiBufferSource.immediate(new BufferBuilder(4096));var consumer=ItemRenderer.getFoilBufferDirect(own,type,true,stack.hasFoil());root.render(pose,consumer,light,overlay);own.endBatch();
   pose.pushPose();try{blade.translateAndRotate(pose);lastTip.set(0,-10f/16,0);pose.last().pose().transformPosition(lastTip);}finally{pose.popPose();}
   Demo.Client.draws++;Demo.Client.gl=org.lwjgl.opengl.GL11.glGetError();
  }finally{pose.popPose();}
 }
 static final class Types extends RenderType {
  private Types(){super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
  static RenderType make(){return create("articulated_material",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,4096,true,false,CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTextureState(new TextureStateShard(new ResourceLocation(Demo.ID,"textures/white.png"),false,false)).setTransparencyState(NO_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));}
 }
}
