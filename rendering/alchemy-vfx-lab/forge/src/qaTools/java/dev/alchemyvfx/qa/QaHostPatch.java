package dev.alchemyvfx.qa;

import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.*;

/** Produces a separate QA host JAR. The input and host source are only read. */
public final class QaHostPatch {
    private static final String EFFECTS="cn/blockforge/generated/generatedmod/client/AlchemyFurnaceEffects";
    private static final String GATE="cn/blockforge/generated/generatedmod/client/AlchemyVolumeExternalHook";
    private static byte[] patch(byte[] original) {
        ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,0);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            boolean hooked;
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                MethodVisitor visitor=super.visitMethod(access,name,descriptor,signature,exceptions);
                if(!name.equals("drawFurnace"))return visitor;
                hooked=true;
                return new MethodVisitor(Opcodes.ASM9,visitor){
                    @Override public void visitCode(){
                        super.visitCode();Label retained=new Label();
                        mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitMethodInsn(Opcodes.INVOKESTATIC,GATE,"claims","(Ljava/lang/Object;)Z",false);
                        mv.visitJumpInsn(Opcodes.IFEQ,retained);mv.visitInsn(Opcodes.RETURN);mv.visitLabel(retained);mv.visitFrame(Opcodes.F_SAME,0,null,0,null);
                    }
                };
            }
            @Override public void visitEnd(){
                if(!hooked)throw new IllegalStateException("Host lacks expected drawFurnace method");
                MethodVisitor marker=super.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"externalVolumeHookVersion","()I",null,null);
                marker.visitCode();marker.visitInsn(Opcodes.ICONST_1);marker.visitInsn(Opcodes.IRETURN);marker.visitMaxs(1,0);marker.visitEnd();super.visitEnd();
            }
        },0);return writer.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        Path input=Path.of(args[0]).toAbsolutePath().normalize(),output=Path.of(args[1]).toAbsolutePath().normalize();
        if(input.equals(output))throw new IllegalArgumentException("QA output must differ from host input");
        Files.createDirectories(output.getParent());int patches=0;
        try(JarFile source=new JarFile(input.toFile());JarOutputStream target=new JarOutputStream(Files.newOutputStream(output))){
            var entries=source.entries();while(entries.hasMoreElements()){
                JarEntry entry=entries.nextElement();if(entry.getName().equals(GATE+".class"))throw new IllegalStateException("Input already contains QA hook");
                byte[] bytes;try(InputStream stream=source.getInputStream(entry)){bytes=stream.readAllBytes();}
                if(entry.getName().equals(EFFECTS+".class")){bytes=patch(bytes);patches++;}
                target.putNextEntry(new JarEntry(entry.getName()));target.write(bytes);target.closeEntry();
            }
            try(InputStream stream=QaHostPatch.class.getResourceAsStream("/"+GATE+".class")){if(stream==null)throw new IllegalStateException("QA helper missing");target.putNextEntry(new JarEntry(GATE+".class"));target.write(stream.readAllBytes());target.closeEntry();}
        }
        if(patches!=1)throw new IllegalStateException("Expected exactly one hook target");
        String originalHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        String qaHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output)));
        Files.writeString(output.resolveSibling("qa-host-provenance.json"),"{\n  \"input\": \""+input.toString().replace("\\","/")+"\",\n  \"inputSha256\": \""+originalHash+"\",\n  \"outputSha256\": \""+qaHash+"\",\n  \"hookVersion\": 1,\n  \"sourceModified\": false\n}\n");
        System.out.println("QA host patch created with original input SHA-256 "+originalHash);
    }
}
