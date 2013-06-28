package agent;

import javassist.*;
import reports.PerformanceReport;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class TracerAgent implements ClassFileTransformer {
    private static final String AGENT_PACKAGE  = TracerAgent.class.getPackage().getName();
    private static final String GENERATED_PACKAGE = AGENT_PACKAGE + "." + "s";

    private Pattern packagePattern;
    private Pattern tracedPattern;
    private int debugLevel;
    private volatile int flushInterval;

    private static final Map<Long, MethodInfo> methodsInfo = new ConcurrentHashMap<>();
    private static final MethodCallProcessor methodCallProcessor = new MethodCallProcessor();


    private final AtomicLong methodIdSeq = new AtomicLong(0);
    private final AtomicLong classIdSeq = new AtomicLong(0);

    private static final ThreadLocal<ClassPool> classPool = new ThreadLocal<ClassPool> () {
        @Override
        protected ClassPool initialValue() {
            return new ClassPool(true);
        }
    };

    //
    //

    private TracerAgent(String args) {
        init(args);

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                flushInterval = 0;
                printStats();
            }
        });

        if(flushInterval != 0) {
            Thread t = new Thread("Agent statistics flush") {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(flushInterval * 1000);
                            if(flushInterval <= 0) { return; }
                            printStats();
                        } catch (InterruptedException e) {  return; }
                    }
                }
            };
            t.setDaemon(true);
            t.start();
        }
    }

    public static void premain(String args, Instrumentation instrumentation) {

        instrumentation.addTransformer(new TracerAgent(args), false);
    }

    @SuppressWarnings("unused")
    public static void methodEnter(MethodInfo methodInfo) {
        methodCallProcessor.methodEnter(methodInfo);
    }

    @SuppressWarnings("unused")
    public static void methodExit() {
        methodCallProcessor.methodExit();
    }

    @SuppressWarnings("unused")
    public static MethodInfo getMethodInfo(long id) { return methodsInfo.get(id); }

    //
    //

    private void instrumentMethod(CtMethod method, long methodId, CtClass shadowClass,
                                  String shadowClassName)
            throws CannotCompileException {
        debug(1, "Instrumenting method: " +  method.getLongName());
        String fieldName = "m" + methodId;

        CtField callTarget = CtField.make("public static " + AGENT_PACKAGE + ".MethodInfo " +
                fieldName + "=" + AGENT_PACKAGE +
                ".TracerAgent.getMethodInfo(" + methodId + "L);", shadowClass);

        shadowClass.addField(callTarget);

        method.insertBefore(AGENT_PACKAGE + ".TracerAgent.methodEnter(" +
                shadowClassName  + "." + fieldName + ");");
        method.insertAfter(AGENT_PACKAGE + ".TracerAgent.methodExit();", true);
    }

    private void instrumentConstructor(CtConstructor constructor, long methodId, CtClass shadowClass,
                                       String shadowClassName)
            throws CannotCompileException {

        debug(1, "Instrumenting constructor: " +  constructor.getLongName());
        String fieldName = "m" + methodId;

        CtField callTarget = CtField.make("public static "+ AGENT_PACKAGE + ".MethodInfo " +
                fieldName + "=" + AGENT_PACKAGE +
                ".TracerAgent.getMethodInfo(" + methodId + "L);", shadowClass);

        shadowClass.addField(callTarget);

        constructor.insertBefore(AGENT_PACKAGE + ".TracerAgent.methodEnter(" +
                shadowClassName  + "." + fieldName + ");");
        constructor.insertAfter(AGENT_PACKAGE + ".TracerAgent.methodExit();", true);
    }


    private byte[] instrumentClass(ClassLoader loader, ProtectionDomain domain, String className,
                                   byte[] classfileBuffer) {
        ClassPool pool = classPool.get();
        CtClass modclass = null;

        String shadowClassName = GENERATED_PACKAGE + ".c" + classIdSeq.incrementAndGet();
        CtClass shadowClass = pool.makeClass(shadowClassName);

        debug(1, "Instrumenting class: " + className + ", shadow: " + shadowClassName);

        try {
            modclass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
            if(!modclass.isInterface()) {
                for(CtMethod m: modclass.getMethods()) {
                    String mName = m.getLongName();
                    if(allowInstrumentMethod(mName) && !m.isEmpty() &&
                            (m.getModifiers() & Modifier.NATIVE) == 0) {
                        int flags = 0;
                        if(allowTraceMethod(mName)) { flags |= MethodInfo.MF_TRACEDCALL; }
                        instrumentMethod(m, registerMethod(new MethodInfo(className, mName, flags)),
                                shadowClass, shadowClassName);
                    }
                }

                for(CtConstructor c: modclass.getConstructors()) {
                    String cName = c.getLongName();
                    if((c.getModifiers() & Modifier.NATIVE) == 0) {
                        int flags = MethodInfo.MF_CONSTRUCTOR;
                        if(allowTraceMethod(cName)) { flags |= MethodInfo.MF_TRACEDCALL; }
                        instrumentConstructor(c, registerMethod(new MethodInfo(className, cName, flags)),
                                shadowClass, shadowClassName);
                    }
                }
            }

            pool.toClass(shadowClass, loader, domain);
            return modclass.toBytecode();
        } catch (Exception e) {
            System.err.println("Exception caught during class " + className +
                    " instrumentation");
            e.printStackTrace();
        } finally {
            if(modclass != null) { modclass.detach(); }
            if(shadowClass != null) { shadowClass.detach(); }
        }

        return null;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        String translatedName = className.replace("/", ".");

        if(allowInstrumentClass(translatedName)) {
            return instrumentClass(loader, protectionDomain, translatedName, classfileBuffer);
        }

        return null;
    }

    //
    //

    boolean allowInstrumentClass(String className) {
        return packagePattern != null && packagePattern.matcher(className).matches();
    }

    boolean allowInstrumentMethod(String methodName) {
        return packagePattern != null && packagePattern.matcher(methodName).find();
    }

    boolean allowTraceMethod(String methodName) {
        return tracedPattern != null && tracedPattern.matcher(methodName).find();
    }


    long registerMethod(MethodInfo methodInfo) {
        long rvalue = methodIdSeq.getAndIncrement();
        methodsInfo.put(rvalue, methodInfo);

        return rvalue;
    }

    private void debug(int level, String message) {
        if(debugLevel >= level) {
            System.out.println("[AGENT - DEBUG] " + message);
        }
    }

    void printStats() {
        PerformanceReport pr = new PerformanceReport(methodsInfo.values());

        System.out.println("\n[AGENT+] *** performance report ***");
        pr.reportTopTimeConsumed(10);
        pr.reportTopCalled(10);
        pr.reportTopConstructed(10);
        System.out.println("\n[AGENT-] *** end of performance report ***");
    }


    void init(String argString) {
        String args[] = argString.split("#");

        System.out.println("running with args:[" + argString + "]");

        for(String arg: args) {
            String kv[] = arg.split(":");
            if(kv == null || (kv[0] == null) || (kv[1] == null)) { continue; }
            switch (kv[0]) {
                case "p":
                    packagePattern = Pattern.compile(kv[1]);
                    System.out.println("package pattern:[" + kv[1] + "]");
                    break;
                case "t":
                    tracedPattern = Pattern.compile(kv[1]);
                    System.out.println("trace call pattern:[" + kv[1] + "]");
                    break;
                case "d":
                    debugLevel = Integer.parseInt(kv[1]);
                    System.out.println("debug level:[" + debugLevel + "]");
                    break;
                case "i":
                    flushInterval = Integer.parseInt(kv[1]);
                    System.out.println("statistics flush interval:[" + flushInterval + "]");
                    break;
            }
        }
    }
}
