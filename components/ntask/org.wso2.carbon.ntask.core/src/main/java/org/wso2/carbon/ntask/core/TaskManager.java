/**
 *  Copyright (c) 2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.ntask.core;

import java.util.List;
import java.util.Map;

import org.wso2.carbon.ntask.common.TaskException;

/**
 * This interface represents the task manager functionalities.
 */
public interface TaskManager {

	/**
	 * Schedule all the tasks which the current node is responsible for.
	 * @throws TaskException
	 */
	public void scheduleAllTasks() throws TaskException;
	
	/**
	 * Starts a task with the given name.
	 * @param taskName The name of the task
	 * @throws TaskException
	 */
	public void scheduleTask(String taskName) throws TaskException;
	
	/**
	 * Reschedules a task with the given name, only the trigger information will be updated in the
	 * reschedule. 
	 * @param taskName The task to be rescheduled
	 * @throws TaskException
	 */
	public void rescheduleTask(String taskName) throws TaskException;
		
	/**
	 * Stops and deletes a task with the given name.
	 * @param taskName The name of the task
	 * @throws TaskException
	 */
	public void deleteTask(String taskName) throws TaskException;
	
	/**
	 * Pauses a task with the given name.
	 * @param taskName The name of the task
	 * @throws TaskException
	 */
	public void pauseTask(String taskName) throws TaskException;
	
	/**
	 * Resumes a paused task with the given name.
	 * @param taskName The name of the task
	 * @throws TaskException
	 */
	public void resumeTask(String taskName) throws TaskException;
	
	/**
	 * Registers a new task or updates if one already exists.
	 * @param taskInfo The task information 
	 * @throws TaskException
	 */
	public void registerTask(TaskInfo taskInfo) throws TaskException;	
	
	/**
	 * Gets tasks state information
	 * @param taskName The name of the task
	 * @return State of the task
	 * @throws TaskException
	 */
	public TaskState getTaskState(String taskName) throws TaskException;
	
	/**
	 * Get all the task states. 
	 * @return State of the task
	 * @throws TaskException
	 */
	public Map<String, TaskState> getAllTaskStates() throws TaskException;
	
	/**
	 * Get task information.
	 * @param taskName The name of the task
	 * @return The task information 
	 * @throws TaskException
	 */
	public TaskInfo getTask(String taskName) throws TaskException;
	
	/**
	 * Get all task information.
	 * @return Task information list
	 * @throws TaskException
	 */
	public List<TaskInfo> getAllTasks() throws TaskException;
	
	/**
	 * Returns the task server count.
	 * @return The server count.
	 * @throws TaskException
	 */
	public int getServerCount() throws TaskException;
	
	/**
	 * Lists all the tasks in a given server location.
	 * @param location The 0 based index of a server
	 * @return The task information list
	 * @throws TaskException
	 */
	public List<TaskInfo> getTasksInServer(int location) throws TaskException;
	
	/**
	 * Lists all the tasks in all the servers.
	 * @return The outer-most list represent a server in each element, with the server location,
	 *         corresponding to the list location, the content of that list element is the list of
	 *         tasks assigned to that server
	 * @throws TaskException
	 */
	public List<List<TaskInfo>> getAllTasksInServers() throws TaskException;
	
	/**
	 * Checks if the given task is already scheduled.
	 * @param taskName The task name
	 * @return true if already scheduled
	 * @throws TaskException
	 */
	public boolean isTaskScheduled(String taskName) throws TaskException;
	
	/**
	 * Task states.
	 */
	public enum TaskState {
		STARTED,
		STOPPED,
		PAUSED,
		ERROR,
		FINISHED,
		UNKNOWN
	}
	
}
