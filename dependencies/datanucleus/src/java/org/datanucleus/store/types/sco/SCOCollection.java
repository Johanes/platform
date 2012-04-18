/**********************************************************************
Copyright (c) 2005 Andy Jefferson and others. All rights reserved.
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
    ...
**********************************************************************/
package org.datanucleus.store.types.sco;

/**
 * Representation of a wrapper for a mutable Collection SCO type supported.
 **/
public interface SCOCollection extends SCOContainer
{
    /**
     * Method to update an embedded element stored in the collection
     * @param element The element
     * @param fieldNumber Number of field in the element
     * @param value th enew value for this field
     */
    public void updateEmbeddedElement(Object element, int fieldNumber, Object value);

    /**
     * Method to remove an element from the collection, and observe the flag for whether to allow 
     * cascade delete.
     * @param element The element
     * @param allowCascadeDelete Whether to allow cascade delete
     * @return Whether the element was removed
     */
    public boolean remove(Object element, boolean allowCascadeDelete);
}