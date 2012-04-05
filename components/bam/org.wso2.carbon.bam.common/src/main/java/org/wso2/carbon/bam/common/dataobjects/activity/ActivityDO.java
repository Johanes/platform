/**
 * Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.bam.common.dataobjects.activity;

/*
 * Activity Data class
 */
public class ActivityDO {
	private String description;
	private String activityId;
	private String name;
	private int activityKeyId; // <---Primary key

    public ActivityDO() {
		this.activityKeyId = -1;
	}

	public ActivityDO(String activityId, String name, String description) {

		this.activityId = activityId;
		this.name = name;
		this.description = description;

	}

	public String getActivityId() {
        return activityId;
	}

	public void setActivityId(String activityId) {
        this.activityId = activityId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getActivityKeyId() {
        return this.activityKeyId;
	}

	public void setActivityKeyId(int activityKeyId) {
        this.activityKeyId = activityKeyId;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

}
