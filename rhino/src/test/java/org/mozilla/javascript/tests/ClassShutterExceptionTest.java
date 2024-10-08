/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/** */
package org.mozilla.javascript.tests;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

/**
 * @author Norris Boyd
 */
public class ClassShutterExceptionTest {
    private static Context.ClassShutterSetter classShutterSetter;

    /** Define a ClassShutter that prevents access to all Java classes. */
    static class OpaqueShutter implements ClassShutter {
        @Override
        public boolean visibleToScripts(String name) {
            return false;
        }
    }

    public void helper(String source) {
        try (Context cx = Context.enter()) {
            Context.ClassShutterSetter setter = cx.getClassShutterSetter();
            try {
                Scriptable globalScope = cx.initStandardObjects();
                if (setter == null) {
                    setter = classShutterSetter;
                } else {
                    classShutterSetter = setter;
                }
                setter.setClassShutter(new OpaqueShutter());
                cx.evaluateString(globalScope, source, "test source", 1, null);
            } finally {
                setter.setClassShutter(null);
            }
        }
    }

    @Test
    public void classShutterException() {
        try {
            helper("java.lang.System.out.println('hi');");
            fail();
        } catch (RhinoException e) {
            // OpaqueShutter should prevent access to java.lang...
            return;
        }
    }

    @Test
    public void throwingException() {
        // JavaScript exceptions with no reference to Java
        // should not be affected by the ClassShutter
        helper("try { throw 3; } catch (e) { }");
    }

    @Test
    public void throwingEcmaError() {
        try {
            // JavaScript exceptions with no reference to Java
            // should not be affected by the ClassShutter
            helper("friggin' syntax error!");
            fail("Should have thrown an exception");
        } catch (EvaluatorException e) {
            // should have thrown an exception for syntax error
        }
    }

    @Test
    public void throwingEvaluatorException() {
        // JavaScript exceptions with no reference to Java
        // should not be affected by the ClassShutter
        helper("try { eval('for;if;else'); } catch (e) { }");
    }
}
