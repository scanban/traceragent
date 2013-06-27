package agent;

import javassist.*;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class TracerAgent implements ClassFileTransformer {
    private static TracerControl tracerControl;

    private static final ThreadLocal<ClassPool> classPool = new ThreadLocal<ClassPool> () {
        @Override
        protected ClassPool initialValue() {
            return new ClassPool(true);
        }
    };


    public static void premain(String args, Instrumentation instrumentation) {

        instrumentation.addTransformer(new TracerAgent(args), false);
    }

    public static void methodEnter(long id) { tracerControl.methodEnter(id); }

    public static void methodExit(long id) { tracerControl.methodExit(id); }


    private TracerAgent(String args) {
        tracerControl = TracerControlSingleton.D.getInstance();
        tracerControl.init(args);

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                TracerControlSingleton.D.getInstance().printStats();
            }
        });
    }

    private void instrumentMethod(CtMethod method, long methodId)
            throws CannotCompileException {
        debug(1, "Instrumenting method: " +  method.getLongName());
        method.insertBefore("agent.TracerAgent.methodEnter(" + methodId + "L);");
        method.insertAfter("agent.TracerAgent.methodExit(" + methodId + "L);");
    }

    private void instrumentConstructor(CtConstructor constructor, long methodId)
            throws CannotCompileException {
        debug(1, "Instrumenting constructor: " +  constructor.getLongName());
        constructor.insertBefore("agent.TracerAgent.methodEnter(" + methodId + "L);");
        constructor.insertAfter("agent.TracerAgent.methodExit(" + methodId + "L);");
    }


    private static void debug(int level, String message) {
        tracerControl.debug(level, message);
    }

    private byte[] instrumentClass(String className, byte[] classfileBuffer) {
        ClassPool pool = classPool.get();
        CtClass iclass = null;

        debug(1, "Instrumenting class: " + className);

        try {
            iclass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
            if(!iclass.isInterface()) {
                for(CtMethod m: iclass.getMethods()) {
                    String mName = m.getLongName();
                    if(tracerControl.allowInstrumentMethod(mName) && !m.isEmpty() &&
                            (m.getModifiers() & Modifier.NATIVE) == 0) {
                        int flags = 0;
                        if(tracerControl.allowTraceMethod(mName)) { flags |= MethodInfo.MF_TRACEDCALL; }
                        instrumentMethod(m,
                                tracerControl.registerMethod(new
                                        MethodInfo(className, mName, flags)));
                    }
                }
                for(CtConstructor c: iclass.getConstructors()) {
                    String cName = c.getLongName();
                    if((c.getModifiers() & Modifier.NATIVE) == 0) {
                        int flags = MethodInfo.MF_CONSTRUCTOR;
                        if(tracerControl.allowTraceMethod(cName)) { flags |= MethodInfo.MF_TRACEDCALL; }
                        instrumentConstructor(c, tracerControl.registerMethod(new
                                MethodInfo(className, cName, flags)));
                    }
                }
            }

            return iclass.toBytecode();
        } catch (Exception e) {
            System.err.println("Exception caught during class " + className +
                    " instrumentation");
            e.printStackTrace();
        } finally {
            if(iclass != null) { iclass.detach(); }
        }

        return null;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        String translatedName = className.replace("/", ".");

        //debug(1, "trans: " + translatedName);

        if(tracerControl.allowInstrumentClass(translatedName)) {
            return instrumentClass(translatedName, classfileBuffer);
        }

        return classfileBuffer;
    }
}
