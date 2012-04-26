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

package org.wso2.carbon.registry.app.test;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.integration.core.TestTemplate;
import org.wso2.carbon.registry.app.RemoteRegistry;
import org.wso2.carbon.registry.core.Resource;

import java.io.InputStream;
import java.io.StringWriter;

import static org.testng.Assert.*;

/**
 * A test case which tests registry ratings operations
 */
public class RatingTestCase {
    public RemoteRegistry registry;

    @BeforeClass(groups = {"wso2.greg"})
    public void init() {
        InitializeAPI initializeAPI = new InitializeAPI();
        registry = initializeAPI.getRegistry(FrameworkSettings.CARBON_HOME, FrameworkSettings.HTTPS_PORT, FrameworkSettings.HTTP_PORT);
    }

    @Test(groups = {"wso2.greg"})
    public void AddResourceRatingTest() throws Exception {
        Resource r1 = registry.newResource();
        byte[] r1content = "R1 content".getBytes();
        r1.setContent(r1content);

        registry.put("/d16/d17/r1", r1);

        registry.rateResource("/d16/d17/r1", 5);

        float rating = registry.getAverageRating("/d16/d17/r1");

        //System.out.println("Start rating:" + rating);
        assertEquals(rating, (float) 5.0, (float) 0.01,
                "Rating of the resource /d16/d17/r1 should be 5.");
    }

    @Test(groups = {"wso2.greg"})
    public void AddCollectionRatingTest() throws Exception {
        Resource r1 = registry.newCollection();

        registry.put("/d16/d18", r1);
        registry.rateResource("/d16/d18", 4);

        float rating = registry.getAverageRating("/d16/d18");

        //System.out.println("Start rating:" + rating);
        assertEquals(rating, (float) 4.0, (float) 0.01,
                "Rating of the resource /d16/d18 should be 5.");
    }

    @Test(groups = {"wso2.greg"})
    public void EditResourceRatingTest() throws Exception {
        Resource r1 = registry.newResource();
        byte[] r1content = "R1 content".getBytes();
        r1.setContent(r1content);

        registry.put("/d61/d17/d18/r1", r1);
        registry.rateResource("/d61/d17/d18/r1", 5);

        float rating = registry.getAverageRating("/d61/d17/d18/r1");

        //System.out.println("Start rating:" + rating);

        assertEquals(rating,(float) 5.0, (float) 0.01,
                "Rating of the resource /d61/d17/d18/r1 should be 5.");

        /*rate the same resource again*/

        registry.rateResource("/d61/d17/d18/r1", 3);

        float rating_edit = registry.getAverageRating("/d61/d17/d18/r1");

        //System.out.println("Start rating:" + rating_edit);

        assertEquals(rating_edit,(float) 3.0, (float) 0.01,
                "Rating of the resource /d61/d17/d18/r1 should be 3.");
    }


    @Test(groups = {"wso2.greg"})
    public void RatingsPathTest() throws Exception {
        Resource r5 = registry.newResource();
        String r5Content = "this is r5 content";
        r5.setContent(r5Content.getBytes());
        r5.setDescription("production ready.");
        String r5Path = "/c1/r5";

        registry.put(r5Path, r5);

        registry.rateResource("/c1/r5", 3);

        Resource ratings = registry.get("/c1/r5;ratings");
        String[] ratingPaths = (String[]) ratings.getContent();

        Resource c1 = registry.get(ratingPaths[0]);

        InputStream stream = c1.getContentStream();

        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer);
        String ratingString = writer.toString();
        assertEquals(ratingString, "3", "Ratings are not retrieved properly as resources.");
    }
}
