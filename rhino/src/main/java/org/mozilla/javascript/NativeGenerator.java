/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript;

/**
 * This class implements generator objects. See <a
 * href="http://developer.mozilla.org/en/docs/New_in_JavaScript_1.7#Generators">Generators</a>
 *
 * @author Norris Boyd
 */
public final class NativeGenerator extends IdScriptableObject {
    private static final long serialVersionUID = 1645892441041347273L;

    private static final Object GENERATOR_TAG = "Generator";

    static NativeGenerator init(ScriptableObject scope, boolean sealed) {
        // Generator
        // Can't use "NativeGenerator().exportAsJSClass" since we don't want
        // to define "Generator" as a constructor in the top-level scope.

        NativeGenerator prototype = new NativeGenerator();
        if (scope != null) {
            prototype.setParentScope(scope);
            prototype.setPrototype(getObjectPrototype(scope));
        }
        prototype.activatePrototypeMap(MAX_PROTOTYPE_ID);
        if (sealed) {
            prototype.sealObject();
        }

        // Need to access Generator prototype when constructing
        // Generator instances, but don't have a generator constructor
        // to use to find the prototype. Use the "associateValue"
        // approach instead.
        if (scope != null) {
            scope.associateValue(GENERATOR_TAG, prototype);
        }

        return prototype;
    }

    /** Only for constructing the prototype object. */
    private NativeGenerator() {}

    public NativeGenerator(Scriptable scope, NativeFunction function, Object savedState) {
        this.function = function;
        this.savedState = savedState;
        // Set parent and prototype properties. Since we don't have a
        // "Generator" constructor in the top scope, we stash the
        // prototype in the top scope's associated value.
        Scriptable top = ScriptableObject.getTopLevelScope(scope);
        this.setParentScope(top);
        NativeGenerator prototype =
                (NativeGenerator) ScriptableObject.getTopScopeValue(top, GENERATOR_TAG);
        this.setPrototype(prototype);
    }

    public static final int GENERATOR_SEND = 0, GENERATOR_THROW = 1, GENERATOR_CLOSE = 2;

    @Override
    public String getClassName() {
        return "Generator";
    }

    @Override
    protected void initPrototypeId(int id) {
        String s;
        int arity;
        switch (id) {
            case Id_close:
                arity = 1;
                s = "close";
                break;
            case Id_next:
                arity = 1;
                s = "next";
                break;
            case Id_send:
                arity = 0;
                s = "send";
                break;
            case Id_throw:
                arity = 0;
                s = "throw";
                break;
            case Id___iterator__:
                arity = 1;
                s = "__iterator__";
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(id));
        }
        initPrototypeMethod(GENERATOR_TAG, id, s, arity);
    }

    @Override
    public Object execIdCall(
            IdFunctionObject f, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (!f.hasTag(GENERATOR_TAG)) {
            return super.execIdCall(f, cx, scope, thisObj, args);
        }
        int id = f.methodId();

        NativeGenerator generator = ensureType(thisObj, NativeGenerator.class, f);

        switch (id) {
            case Id_close:
                // need to run any pending finally clauses
                return generator.resume(cx, scope, GENERATOR_CLOSE, new GeneratorClosedException());

            case Id_next:
                // arguments to next() are ignored
                generator.firstTime = false;
                return generator.resume(cx, scope, GENERATOR_SEND, Undefined.instance);

            case Id_send:
                {
                    Object arg = args.length > 0 ? args[0] : Undefined.instance;
                    if (generator.firstTime && !arg.equals(Undefined.instance)) {
                        throw ScriptRuntime.typeErrorById("msg.send.newborn");
                    }
                    return generator.resume(cx, scope, GENERATOR_SEND, arg);
                }

            case Id_throw:
                return generator.resume(
                        cx, scope, GENERATOR_THROW, args.length > 0 ? args[0] : Undefined.instance);

            case Id___iterator__:
                return thisObj;

            default:
                throw new IllegalArgumentException(String.valueOf(id));
        }
    }

    private Object resume(Context cx, Scriptable scope, int operation, Object value) {
        if (savedState == null) {
            if (operation == GENERATOR_CLOSE) return Undefined.instance;
            Object thrown;
            if (operation == GENERATOR_THROW) {
                thrown = value;
            } else {
                thrown = NativeIterator.getStopIterationObject(scope);
            }
            throw new JavaScriptException(thrown, lineSource, lineNumber);
        }
        try {
            synchronized (this) {
                // generator execution is necessarily single-threaded and
                // non-reentrant.
                // See https://bugzilla.mozilla.org/show_bug.cgi?id=349263
                if (locked) throw ScriptRuntime.typeErrorById("msg.already.exec.gen");
                locked = true;
            }
            return function.resumeGenerator(cx, scope, operation, savedState, value);
        } catch (GeneratorClosedException e) {
            // On closing a generator in the compile path, the generator
            // throws a special exception. This ensures execution of all pending
            // finalizers and will not get caught by user code.
            return Undefined.instance;
        } catch (RhinoException e) {
            lineNumber = e.lineNumber();
            lineSource = e.lineSource();
            savedState = null;
            throw e;
        } finally {
            synchronized (this) {
                locked = false;
            }
            if (operation == GENERATOR_CLOSE) savedState = null;
        }
    }

    @Override
    protected int findPrototypeId(String s) {
        int id;
        switch (s) {
            case "close":
                id = Id_close;
                break;
            case "next":
                id = Id_next;
                break;
            case "send":
                id = Id_send;
                break;
            case "throw":
                id = Id_throw;
                break;
            case "__iterator__":
                id = Id___iterator__;
                break;
            default:
                id = 0;
                break;
        }
        return id;
    }

    private static final int Id_close = 1,
            Id_next = 2,
            Id_send = 3,
            Id_throw = 4,
            Id___iterator__ = 5,
            MAX_PROTOTYPE_ID = 5;

    private NativeFunction function;
    private Object savedState;
    private String lineSource;
    private int lineNumber;
    private boolean firstTime = true;
    private boolean locked;

    public static class GeneratorClosedException extends RuntimeException {
        private static final long serialVersionUID = 2561315658662379681L;
    }
}
