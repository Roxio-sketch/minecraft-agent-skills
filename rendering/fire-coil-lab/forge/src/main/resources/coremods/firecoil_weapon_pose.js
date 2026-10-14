function initializeCoreMod(){
 var O=Java.type('org.objectweb.asm.Opcodes'),List=Java.type('org.objectweb.asm.tree.InsnList'),Var=Java.type('org.objectweb.asm.tree.VarInsnNode'),Call=Java.type('org.objectweb.asm.tree.MethodInsnNode'),Label=Java.type('org.objectweb.asm.tree.LabelNode'),Insn=Java.type('org.objectweb.asm.tree.InsnNode'),Catch=Java.type('org.objectweb.asm.tree.TryCatchBlockNode');
 var helper='dev/firecoil/WeaponAttachment';
 var render='(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V';
 return {firecoil_actual_weapon_pose:{target:{type:'CLASS',name:'net.minecraft.client.renderer.entity.ItemRenderer'},transformer:function(node){
  var captures=0;
  for(var i=0;i<node.methods.size();i++){var m=node.methods.get(i);if(m.desc!=render)continue;var nodes=m.instructions.toArray(),pop=null;
   for(var j=0;j<nodes.length;j++){var call=nodes[j];if(call.getOpcode()==O.INVOKEVIRTUAL&&call.owner=='com/mojang/blaze3d/vertex/PoseStack'&&(call.name=='popPose'||call.name=='m_85849_')&&call.desc=='()V')pop=call;}
   if(pop==null)throw new Error('Missing item final pose');var hook=new List();
   hook.add(new Var(O.ALOAD,1));hook.add(new Var(O.ALOAD,2));hook.add(new Var(O.ALOAD,4));hook.add(new Var(O.ALOAD,5));hook.add(new Var(O.ALOAD,8));
   hook.add(new Call(O.INVOKESTATIC,helper,'afterItem','(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/resources/model/BakedModel;)V',false));m.instructions.insertBefore(pop,hook);
   var start=new Label(),end=new Label(),handler=new Label(),enter=new List();
   enter.add(new Var(O.ALOAD,1));enter.add(new Var(O.ALOAD,2));enter.add(new Call(O.INVOKESTATIC,helper,'enterItem','(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;)V',false));enter.add(start);m.instructions.insert(enter);
   for(var j=0;j<nodes.length;j++)if(nodes[j].getOpcode()==O.RETURN)m.instructions.insertBefore(nodes[j],new Call(O.INVOKESTATIC,helper,'exitItem','()V',false));
   m.instructions.add(end);m.instructions.add(handler);var local=m.maxLocals++;m.instructions.add(new Var(O.ASTORE,local));m.instructions.add(new Call(O.INVOKESTATIC,helper,'exitItem','()V',false));m.instructions.add(new Var(O.ALOAD,local));m.instructions.add(new Insn(O.ATHROW));m.tryCatchBlocks.add(new Catch(start,end,handler,null));m.maxStack+=5;captures++;
  }
  if(captures!=1)throw new Error('Fire coil item hooks mismatch '+captures);return node;
 }}};
}
