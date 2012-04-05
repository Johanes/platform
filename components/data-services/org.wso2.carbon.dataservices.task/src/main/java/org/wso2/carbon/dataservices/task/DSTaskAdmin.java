/*
 *  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.dataservices.task;

import org.apache.axis2.AxisFault;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.description.Parameter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.AbstractAdmin;
import org.wso2.carbon.dataservices.task.internal.DSTaskServiceComponent;
import org.wso2.carbon.ntask.common.TaskException;
import org.wso2.carbon.ntask.core.TaskInfo;
import org.wso2.carbon.ntask.core.TaskManager;
import org.wso2.carbon.utils.deployment.GhostDeployerUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DSTaskAdmin extends AbstractAdmin {

    private static final Log log = LogFactory.getLog(DSTaskAdmin.class);

    public String[] getAllTaskNames() throws AxisFault {
        try {
            TaskManager taskManager = DSTaskServiceComponent.getTaskService().getTaskManager(
                    DSTaskConstants.DATA_SERVICE_TASK_TYPE);
            List<TaskInfo> taskInfoList = taskManager.getAllTasks();
            List<String> result = new ArrayList<String>();
            for (TaskInfo taskInfo : taskInfoList) {
                result.add(taskInfo.getName());
            }
            return result.toArray(new String[result.size()]);
        } catch (Exception e) {
            log.error(e);
            throw new AxisFault("Error in getting task names: " + e.getMessage(), e);
        }
    }

    public DSTaskInfo getTaskInfo(String taskName) throws AxisFault {
        try {
            TaskManager tm = DSTaskServiceComponent.getTaskService().getTaskManager(
                    DSTaskConstants.DATA_SERVICE_TASK_TYPE);
            return DSTaskUtils.convert(tm.getTask(taskName));
        } catch (Exception e) {
            log.error(e);
            throw new AxisFault("Error getting task info for task: " + taskName, e);
        }
    }

    public void scheduleTask(DSTaskInfo dsTaskInfo) throws AxisFault {
        TaskManager tm = null;
        try {
            tm = DSTaskServiceComponent.getTaskService().getTaskManager(
                    DSTaskConstants.DATA_SERVICE_TASK_TYPE);
            TaskInfo taskInfo = DSTaskUtils.convert(dsTaskInfo);
            tm.registerTask(taskInfo);
            tm.scheduleTask(taskInfo.getName());
        } catch (Exception e) {
            log.error(e);
            if (tm != null) {
                try {
                    tm.deleteTask(dsTaskInfo.getName());
                } catch (TaskException e1) {
                    log.error(e1);
                }
            }
            throw new AxisFault("Error scheduling task: " + dsTaskInfo.getName(), e);
        }
    }

    public boolean rescheduleTask(DSTaskInfo dsTaskInfo) throws AxisFault {
        try {
            TaskManager tm = DSTaskServiceComponent.getTaskService().getTaskManager(
                    DSTaskConstants.DATA_SERVICE_TASK_TYPE);
            TaskInfo taskInfo = DSTaskUtils.convert(dsTaskInfo);
            tm.registerTask(taskInfo);
            tm.rescheduleTask(taskInfo.getName());
        } catch (Exception e) {
            log.error(e);
            throw new AxisFault("Error rescheduling task: " + dsTaskInfo.getName(), e);
        }
        return true;
    }

    public void deleteTask(String taskName) throws AxisFault {
        try {
            TaskManager tm = DSTaskServiceComponent.getTaskService().getTaskManager(
                    DSTaskConstants.DATA_SERVICE_TASK_TYPE);
            tm.deleteTask(taskName);
        } catch (Exception e) {
            log.error(e);
            throw new AxisFault("Error deleting task: " + taskName, e);
        }
    }

    public boolean isTaskScheduled(String taskName) throws AxisFault {
        try {
            TaskManager tm = DSTaskServiceComponent.getTaskService().getTaskManager(
                    DSTaskConstants.DATA_SERVICE_TASK_TYPE);
            return tm.isTaskScheduled(taskName);
        } catch (Exception e) {
            log.error(e);
            throw new AxisFault("Error checking task scheduled status: " + taskName, e);
        }
    }

    /**
     * Retrieves the list of Data Services deployed.
     *
     * @return a String array containing the list of data services
     * @throws AxisFault AxisFault
     */
    public String[] getAllSchedulableDataServices() throws AxisFault {
        List<String> serviceList = new ArrayList<String>();
        Map<String, AxisService> serviceMap = this.getAxisConfig().getServices();
        AxisService axisService;
        Parameter serviceTypeParam;
        for (String serviceName : serviceMap.keySet()) {
            axisService = this.getAxisConfig().getService(serviceName);
            serviceTypeParam = axisService.getParameter(DSTaskConstants.AXIS2_SERVICE_TYPE);
            if (serviceTypeParam != null) {
                if (DSTaskConstants.DB_SERVICE_TYPE.equals(serviceTypeParam.getValue().toString())) {
                    if (DSTaskUtils.extractHTTPEPR(axisService) != null) {
                        serviceList.add(serviceName);
                    }
                }
            }
        }
        return serviceList.toArray(new String[serviceList.size()]);
    }

    /**
     * Returns the data service operations which contains no input parameters.
     *
     * @param dsName The data service name
     * @return The no parameter operation names
     * @throws AxisFault
     */
    public String[] getNoParamDSOperations(String dsName) throws AxisFault {
        AxisService axisService = this.getAxisConfig().getService(dsName);
        if (axisService == null) {
            return new String[0];
        }
        /* if it's a ghost service, deploy the real one now */
        if (GhostDeployerUtils.isGhostService(axisService)) {
            GhostDeployerUtils.deployActualService(this.getAxisConfig(), axisService);
        }

//        List<String> opNames = axisService.getOperationsNameList();
//        List<String> result = new ArrayList<String>();
//        for (String opName : opNames) {
//            AxisOperation op = axisService.getOperation(new QName(opName));
//            if (op.getOutputAction() != null && !isBoxcarringOp(opName)) {
//                result.add(opName);
//            }
//        }

        List<String> result = new ArrayList<String>();
        String dbsContent = this.getDataServiceContentAsString(dsName);
        List<String> opNames = DSTaskUtils.getOutOnlyOperationsList(dbsContent);
        for (String opName : opNames) {
            if (!DSTaskUtils.isBoxcarringOp(opName)) {
                result.add(opName);
            }
        }
        return result.toArray(new String[result.size()]);
    }

    private String getDataServiceContentAsString(String dsName) throws AxisFault {
        URL repositoryURL = getAxisConfig().getRepository();
        String filePath = repositoryURL.getPath() + DSTaskConstants.DEPLOYMENT_FOLDER_NAME +
                File.separator + dsName + "." + DSTaskConstants.DBS_FILE_EXTENSION;
        StringBuffer fileContents = new StringBuffer();
        File config = new File(filePath);
        try {
            FileReader fileReader = new FileReader(config);
            BufferedReader in = new BufferedReader(fileReader);
            String str;
            while ((str = in.readLine()) != null) {
                fileContents.append(str).append("\n");
            }
            in.close();
        } catch (IOException e) {
            log.error(e);
            throw new AxisFault("Error while reading the contents from the service config " +
                    "file for service '" + dsName + "'", e);
        }
        return fileContents.toString();
    }

}
