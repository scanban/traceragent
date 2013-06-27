package agent;

import javassist.*;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicLong;

public class TracerAgent implements ClassFileTransformer {
    private static TracerControl tracerControl;
    private final AtomicLong classIdSeq = new AtomicLong(0);


    private static final ThreadLocal<ClassPool> classPool = new ThreadLocal<ClassPool> () {
        @Override
        protected ClassPool initialValue() {
            return new ClassPool(true);
        }
    };


    public static void premain(String args, Instrumentation instrumentation) {

        instrumentation.addTransformer(new TracerAgent(args), false);
    }

    public static void methodEnter(MethodInfo methodInfo) {
        tracerControl.methodEnter(methodInfo);
    }

    public static void methodExit() {
        tracerControl.methodExit();
    }

    public static MethodInfo getMethodInfo(long methodId) {
        return TracerControlSingleton.D.getInstance().getMethodInfo(methodId);
    }

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

    private void instrumentMethod(CtMethod method, long methodId, CtClass shadowClass,
                                  String shadowClassName)
            throws CannotCompileException {
        debug(1, "Instrumenting method: " +  method.getLongName());
        String fieldName = "m" + methodId;

        CtField callTarget = CtField.make("public static agent.MethodInfo " +
                fieldName + " = agent.TracerAgent.getMethodInfo(" + methodId + "L);", shadowClass);

        shadowClass.addField(callTarget);

        method.insertBefore("agent.TracerAgent.methodEnter(" + shadowClassName  + "." + fieldName + ");");
        method.insertAfter("agent.TracerAgent.methodExit();");
    }

    private void instrumentConstructor(CtConstructor constructor, long methodId, CtClass shadowClass,
                                       String shadowClassName)
            throws CannotCompileException {

        debug(1, "Instrumenting constructor: " +  constructor.getLongName());
        String fieldName = "m" + methodId;

        CtField callTarget = CtField.make("public static agent.MethodInfo " +
                fieldName + " = agent.TracerAgent.getMethodInfo(" + methodId + "L);", shadowClass);

        shadowClass.addField(callTarget);

        constructor.insertBefore("agent.TracerAgent.methodEnter(" + shadowClassName  + "." + fieldName + ");");
        constructor.insertAfter("agent.TracerAgent.methodExit();");
    }


    private static void debug(int level, String message) {
        tracerControl.debug(level, message);
    }

    private byte[] instrumentClass(ClassLoader loader, ProtectionDomain domain, String className,
                                   byte[] classfileBuffer) throws NotFoundException {
        ClassPool pool = classPool.get();
        CtClass iclass = null;

        String shadowClassName = "agent.s.c" + classIdSeq.incrementAndGet();
        CtClass shadowClass = pool.makeClass(shadowClassName);

        debug(1, "Instrumenting class: " + className + ", shadow: " + shadowClassName);

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
                                        MethodInfo(className, mName, flags)),
                                shadowClass, shadowClassName);
                    }
                }

                for(CtConstructor c: iclass.getConstructors()) {
                    String cName = c.getLongName();
                    if((c.getModifiers() & Modifier.NATIVE) == 0) {
                        int flags = MethodInfo.MF_CONSTRUCTOR;
                        if(tracerControl.allowTraceMethod(cName)) { flags |= MethodInfo.MF_TRACEDCALL; }
                        instrumentConstructor(c, tracerControl.registerMethod(new
                                MethodInfo(className, cName, flags)),
                                shadowClass, shadowClassName);
                    }
                }
            }

            pool.toClass(shadowClass, loader, domain);
            return iclass.toBytecode();
        } catch (Exception e) {
            System.err.println("Exception caught during class " + className +
                    " instrumentation");
            e.printStackTrace();
        } finally {
            if(iclass != null) { iclass.detach(); }
            if(shadowClass != null) { shadowClass.detach(); }
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
            try {
                return instrumentClass(loader, protectionDomain, translatedName, classfileBuffer);
            } catch (NotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }

        return null;
    }
}
