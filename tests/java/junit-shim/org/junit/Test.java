// Minimal stand-in for JUnit 4 so tests/run.sh can run the host tests that framework
// patches carry (written against real JUnit 4) with javac alone. Not shipped anywhere.
package org.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {}
