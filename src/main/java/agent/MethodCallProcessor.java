package agent;


class MethodCallProcessor {
    private static final ThreadLocal<MethodCallMarkerStack> callStack =
            new ThreadLocal<MethodCallMarkerStack> () {
                @Override
                protected MethodCallMarkerStack initialValue() {
                    return new MethodCallMarkerStack();
                }
            };


    void methodEnter(long threadId, long methodId) {
        TracerControl tracerControl = TracerControlSingleton.D.getInstance();

        MethodInfo methodInfo = tracerControl.getMethodInfo(methodId);

        if(methodInfo.isTraced()) {
            System.out.println(String.format("[T][%6d] %s",
                    threadId, methodInfo.getMethodName()));

            MethodCallMarker[] stack = callStack.get().toArray();

            for(int i = stack.length - 1; i >= 0; --i) {
                System.out.println(String.format("      %5d %s", i,
                        tracerControl.getMethodInfo(stack[i].methodId).getMethodName()));
            }
            System.out.println();
        }

        methodInfo.incrementCallCount();
        callStack.get().push(methodId);
    }

    void methodExit(long threadId, long methodId) {
        TracerControl tracerControl = TracerControlSingleton.D.getInstance();

        MethodCallMarker marker = callStack.get().pop();
        tracerControl.getMethodInfo(marker.methodId).updateStats(marker);
    }

}
