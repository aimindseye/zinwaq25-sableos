// Runs every @Test method of the named classes and prints RAN=<n> FAILED=<n> (see Test.java).
package org.junit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Run {
    private Run() {}

    public static void main(String[] args) throws ReflectiveOperationException {
        int ran = 0;
        int failed = 0;
        for (String name : args) {
            final Class<?> c = Class.forName(name);
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(Test.class)) {
                    continue;
                }
                ran++;
                try {
                    m.invoke(c.getDeclaredConstructor().newInstance());
                } catch (InvocationTargetException e) {
                    failed++;
                    System.out.println("FAIL " + c.getSimpleName() + "." + m.getName() + ": "
                            + e.getTargetException());
                }
            }
        }
        System.out.println("RAN=" + ran + " FAILED=" + failed);
        if (failed > 0 || ran == 0) {
            System.exit(1);
        }
    }
}
