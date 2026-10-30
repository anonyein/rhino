/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.javascript.tests;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.TopLevel;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

public class NativeArrayBufferTest {

    /**
     * Test case for issue {@link https://github.com/mozilla/rhino/issues/437}
     *
     * @throws Exception in case of failure
     */
    @Test
    public void test() throws Exception {
        try (Context cx = Context.enter()) {
            cx.setLanguageVersion(Context.VERSION_ES6);
            TopLevel global = cx.initStandardObjects();
            Object result = cx.evaluateString(global, "(new ArrayBuffer(5)).isView", "", 1, null);
            Assertions.assertEquals(Undefined.instance, result);
        }
    }

    @Test
    public void bytesConstructorCreatesNonSharedBuffer() {
        byte[] bytes = {1, 2, 3};
        NativeArrayBuffer buffer = new NativeArrayBuffer(bytes);

        Assertions.assertFalse(buffer.isShared());
        Assertions.assertEquals(NativeArrayBuffer.ARRAY_CLASS_NAME, buffer.getClassName());
        Assertions.assertEquals(3, buffer.getLength());
        Assertions.assertArrayEquals(bytes, buffer.getBuffer());
    }
}
