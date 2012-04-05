/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.app.test.registry;

import org.wso2.carbon.integration.core.FrameworkSettings;
import org.wso2.carbon.integration.core.TestTemplate;
import org.wso2.carbon.registry.app.RemoteRegistry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.ResourceImpl;

public class ContinuousOperations extends TestTemplate {
     public RemoteRegistry registry;

    @Override
    public void init() {
        InitializeAPI initializeAPI = new InitializeAPI();
        registry = initializeAPI.getRegistry(FrameworkSettings.CARBON_HOME,FrameworkSettings.HTTPS_PORT,FrameworkSettings.HTTP_PORT);
    }

    @Override
    public void runSuccessCase() {
        try {
            ContinousDelete();
            ContinuousUpdate();
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("Continuous Operation Test Failed");
        }
    }

    @Override
    public void runFailureCase() {

    }

    @Override
    public void cleanup() {

    }

    public void ContinousDelete() throws RegistryException, InterruptedException {

        int iterations = 100;

        for (int i = 0; i < iterations; i++) {

            Resource res1 = registry.newResource();
            byte[] r1content = "R2 content".getBytes();
            res1.setContent(r1content);
            String path = "/con-delete/test/" + i + 1;

            registry.put(path, res1);

            Resource resource1 = registry.get(path);

            assertEquals("File content is not matching", new String((byte[]) resource1.getContent()),
                         new String((byte[]) res1.getContent()));

            registry.delete(path);

            boolean value = false;

            if (registry.resourceExists(path)) {
                value = true;
            }

            assertFalse("Resource found at the path", value);

            res1.discard();
            resource1.discard();
            Thread.sleep(100);
        }
    }

    public void ContinuousUpdate() throws RegistryException, InterruptedException {

        int iterations = 100;

        for (int i = 0; i < iterations; i++) {

            Resource res1 = registry.newResource();
            byte[] r1content = "R2 content".getBytes();
            res1.setContent(r1content);
            String path = "/con-delete/test-update/" + i + 1;

            registry.put(path, res1);

            Resource resource1 = registry.get(path);

            assertEquals("File content is not matching", new String((byte[]) resource1.getContent()),
                         new String((byte[]) res1.getContent()));

            Resource resource = new ResourceImpl();
            byte[] r1content1 = "R2 content updated".getBytes();
            resource.setContent(r1content1);
            resource.setProperty("abc", "abc");

            registry.put(path, resource);

            Resource resource2 = registry.get(path);

            assertEquals("File content is not matching", new String((byte[]) resource.getContent()),
                         new String((byte[]) resource2.getContent()));

            resource.discard();
            res1.discard();
            resource1.discard();
            resource2.discard();
            Thread.sleep(100);
        }
    }
}
