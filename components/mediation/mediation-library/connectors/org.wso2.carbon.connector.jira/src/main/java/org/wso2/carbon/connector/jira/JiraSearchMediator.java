/*
 * Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.connector.jira;

import static org.wso2.carbon.connector.jira.JiraConstants.CONTEXT_SEARCH_RESULT;

import org.apache.synapse.MessageContext;
import org.wso2.carbon.connector.jira.util.JiraMediatorUtil;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.SearchResult;

public class JiraSearchMediator extends JiraMediator {

	private String jqlQuery;
	
	public String getJqlQuery() {
		return jqlQuery;
	}

	public void setJqlQuery(String jqlQuery) {
		this.jqlQuery = jqlQuery;
	}

	@Override
    public boolean mediate(MessageContext synCtx) {
        JiraRestClient client = JiraMediatorUtil.getClient(getUri(), getUsername(), getPassword());
        SearchResult searchResult = client.getSearchClient().searchJql(getJqlQuery()).claim();
        synCtx.setProperty(CONTEXT_SEARCH_RESULT, searchResult);
        return true;
    }

}
