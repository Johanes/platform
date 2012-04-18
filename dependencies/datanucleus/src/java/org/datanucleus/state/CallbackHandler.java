/**********************************************************************
Copyright (c) 2006 Erik Bengtson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
2007 Andy Jefferson - added prePersist
    ...
**********************************************************************/
package org.datanucleus.state;

/**
 * CallBack handlers receive notification of events on persistent objects. 
 * Handlers are responsible for invoking event listeners/callback methods on Callback or Listener
 * implementations.
 */
public interface CallbackHandler
{
    /**
     * Callback after the object has been created.
     * @param pc The Object
     */
    void postCreate(Object pc);

    /**
     * Callback before the object is persisted (just before the lifecycle state change).
     * @param pc The Object
     */
    void prePersist(Object pc);

    /**
     * Callback before the object is stored.
     * @param pc The Object
     */
    void preStore(Object pc);

    /**
     * Callback after the object is stored.
     * @param pc The Object
     */
    void postStore(Object pc);

    /**
     * Callback before the fields of the object are cleared.
     * @param pc The Object
     */
    void preClear(Object pc);

    /**
     * Callback after the fields of the object are cleared.
     * @param pc The Object
     */
    void postClear(Object pc);

    /**
     * Callback before the object is deleted.
     * @param pc The Object
     */
    void preDelete(Object pc);

    /**
     * Callback after the object is deleted.
     * @param pc The Object
     */
    void postDelete(Object pc);

    /**
     * Callback before the object is made dirty.
     * @param pc The Object
     */
    void preDirty(Object pc);

    /**
     * Callback after the object is made dirty.
     * @param pc The Object
     */
    void postDirty(Object pc);

    /**
     * Callback after the fields of the object are loaded.
     * @param pc The Object
     */
    void postLoad(Object pc);

    /**
     * Callback after the fields of the object are refreshed.
     * @param pc The Object
     */
    void postRefresh(Object pc);

    /**
     * Callback before the object is detached.
     * @param pc The Object
     */
    void preDetach(Object pc);

    /**
     * Callback after the object is detached.
     * @param pc The Object
     * @param detachedPC The detached object
     */
    void postDetach(Object pc, Object detachedPC);

    /**
     * Callback before the object is attached.
     * @param detachedPC The Object
     */
    void preAttach(Object detachedPC);

    /**
     * Callback after the object is attached.
     * @param pc The attached Object
     * @param detachedPC The detached object
     */
    void postAttach(Object pc, Object detachedPC);

    /**
     * Adds a new listener to this handler.
     * @param listener the listener instance
     * @param classes the persistent classes which events are fired for the listener  
     */
    void addListener(Object listener, Class[] classes);

    /**
     * Remove a listener for this handler.
     * @param listener the listener instance
     */
    void removeListener(Object listener);

    /**
     * Clear any objects to release resources.
     */
    void close();    
}