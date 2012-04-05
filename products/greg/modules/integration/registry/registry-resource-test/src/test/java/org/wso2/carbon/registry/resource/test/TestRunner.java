/*
*Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/

package org.wso2.carbon.registry.resource.test;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.wso2.carbon.integration.core.FrameworkSettings;


public class TestRunner extends TestSuite {

    public static Test suite() throws Exception {
        FrameworkSettings.getProperty();
        String frameworkPath = FrameworkSettings.getFrameworkPath();

        System.setProperty("java.util.logging.config.file", frameworkPath + "/repository/conf/log4j.properties");

        TestSuite testSuite = new TestSuite();

        testSuite.addTestSuite(NonXMLResourceAddTest.class);
        testSuite.addTestSuite(XMLResourceAddTest.class);
        testSuite.addTestSuite(AssociationsTest.class);
        testSuite.addTestSuite(CommunityFeatureTest.class);
        testSuite.addTestSuite(DependencyTest.class);
        testSuite.addTestSuite(LifeCycleTest.class);
        testSuite.addTestSuite(RegistryCollectionTest.class);
        testSuite.addTestSuite(RegistryResourceTest.class);
        testSuite.addTestSuite(ResourceAdminServiceTest.class);
        testSuite.addTestSuite(SymlinkTest.class);
        testSuite.addTestSuite(NotificationTest.class);
        testSuite.addTestSuite(ContentSearchTest.class);
        testSuite.addTestSuite(ActivitySearchTest.class);


        return testSuite;
    }
}
