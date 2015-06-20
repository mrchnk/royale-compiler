/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.flex.compiler.internal.codegen.externals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.flex.compiler.clients.ExternCConfiguration;
import org.apache.flex.compiler.internal.codegen.externals.reference.ClassReference;
import org.junit.Test;

import com.google.javascript.jscomp.Result;

public class TestExternChrome extends ExternalsTestBase
{
    @Test
    public void test_classes() throws IOException
    {
        client.cleanOutput();
        Result result = compile();
        assertTrue(result.success);

        String[] classes = {
                "chrome",
                "chrome.app",
                "chrome.webstore",
                "chrome.runtime",
                "chrome.runtime.lastError",

                "Port",
                "ChromeEvent",
                "ChromeStringEvent",
                "ChromeBooleanEvent",
                "ChromeNumberEvent",
                "ChromeObjectEvent",
                "ChromeStringArrayEvent",
                "ChromeStringStringEvent",
                "MessageSender",
                "Tab",
                "ChromeLoadTimes",
                "ChromeCsiInfo" };

        assertEquals(17, model.getClasses().size());
        for (String className : classes)
        {
            assertTrue(model.hasClass(className));
        }

        client.emit();
    }

    @Test
    public void test_members() throws IOException
    {
        client.cleanOutput();
        Result result = compile();
        assertTrue(result.success);

        // Port
        ClassReference Port = model.getClassReference("Port");
        assertNotNull(Port);
        assertTrue(Port.hasInstanceField("name"));
        assertTrue(Port.hasInstanceField("onDisconnect"));
        assertTrue(Port.hasInstanceField("onMessage"));
        assertTrue(Port.hasInstanceField("sender"));

        assertTrue(Port.hasInstanceMethod("postMessage"));
        assertTrue(Port.hasInstanceMethod("disconnect"));

        assertEquals("string", Port.getField("name").toTypeAnnotationString());
        assertEquals("ChromeEvent",
                Port.getField("onDisconnect").toTypeAnnotationString());
        assertEquals("ChromeEvent",
                Port.getField("onMessage").toTypeAnnotationString());
        assertEquals("(MessageSender|undefined)",
                Port.getField("sender").toTypeAnnotationString());

        // chrome
        ClassReference chrome = model.getClassReference("chrome");
        assertNotNull(chrome);
        assertTrue(chrome.hasStaticMethod("loadTimes"));
        assertTrue(chrome.hasStaticMethod("csi"));
        assertEquals("ChromeLoadTimes",
                chrome.getMethod("loadTimes").toReturnTypeAnnotationString());
        assertEquals("ChromeCsiInfo",
                chrome.getMethod("csi").toReturnTypeAnnotationString());

        // chrome.app
        ClassReference chrome_app = model.getClassReference("chrome.app");
        assertNotNull(chrome_app);
        assertTrue(chrome_app.hasInstanceField("isInstalled"));
        assertEquals("boolean",
                chrome_app.getField("isInstalled").toTypeAnnotationString());

        // chrome.runtime
        ClassReference chrome_runtime = model.getClassReference("chrome.runtime");
        assertNotNull(chrome_runtime);
        assertTrue(chrome_runtime.hasInstanceMethod("connect"));
        assertTrue(chrome_runtime.hasInstanceMethod("sendMessage"));

        // chrome.runtime.lastError
        ClassReference chrome_runtime_lastError = model.getClassReference("chrome.runtime.lastError");
        assertNotNull(chrome_runtime_lastError);
        assertTrue(chrome_runtime_lastError.hasInstanceField("message"));
        assertEquals(
                "(string|undefined)",
                chrome_runtime_lastError.getField("message").toTypeAnnotationString());

        // chrome.webstore
        ClassReference chrome_webstore = model.getClassReference("chrome.webstore");
        assertNotNull(chrome_webstore);
        assertTrue(chrome_webstore.hasInstanceField("onInstallStageChanged"));
        assertTrue(chrome_webstore.hasInstanceField("onDownloadProgress"));
        assertTrue(chrome_webstore.hasInstanceMethod("install"));

        // Code generated
        assertTrue(chrome.hasStaticField("app"));
        assertTrue(chrome.hasStaticField("runtime"));
        assertTrue(chrome.hasStaticField("webstore"));

        assertTrue(chrome_runtime.hasInstanceField("lastError"));
    }

    @Override
    protected void configure(ExternCConfiguration install) throws IOException
    {
        config.setASRoot(ExternalsTestUtils.AS_ROOT_DIR);

        String coreRoot = ExternalsTestUtils.EXTERNAL_JS_DIR.getAbsolutePath();
        config.addExternal(coreRoot + "/chrome.js");
    }

}