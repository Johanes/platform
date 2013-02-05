/**********************************************************************
Copyright (c) 2002 Mike Martin (TJDO) and others. All rights reserved. 
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
2003 Andy Jefferson - coding standards
2004 Andy Jefferson - added query methods and comments
    ...
**********************************************************************/
package org.datanucleus.store.scostore;

import java.util.Map;

import org.datanucleus.store.ObjectProvider;

/**
 * Interface representation of the backing store for a Map.
 */
public interface MapStore extends Store
{
    /**
     * Accessor for whether the keys are embedded.
     * @return Whether we have embedded keys
     */
    boolean keysAreEmbedded();

    /**
     * Accessor for whether the keys are serialised.
     * @return Whether we have serialised keys
     */
    boolean keysAreSerialised();

    /**
     * Accessor for whether the values are embedded.
     * @return Whether we have embedded values
     */
    boolean valuesAreEmbedded();

    /**
     * Accessor for whether the values are serialised.
     * @return Whether we have serialised values
     */
    boolean valuesAreSerialised();

    // -------------------------------- Map Methods ----------------------------
 
    /**
     * Accessor for whether the Map contains this value.
     * @param op ObjectProvider for the owner of the map.
     * @param value The value to check
     * @return Whether it is contained.
     */
    boolean containsValue(ObjectProvider op, Object value);

    /**
     * Accessor for whether the Map contains this key.
     * @param op ObjectProvider for the owner of the map.
     * @param key The key to check
     * @return Whether it is contained.
     */
    boolean containsKey(ObjectProvider op, Object key);

    /**
     * Accessor for a value from the Map.
     * @param op ObjectProvider for the owner of the map. 
     * @param key Key for the value.
     * @return Value for this key.
     */
    Object get(ObjectProvider op, Object key);

    /**
     * Method to add a value to the Map against this key.
     * @param op ObjectProvider for the owner of the map. 
     * @param key The key.
     * @param value The value.
     * @return Value that was previously against this key.
     */
    Object put(ObjectProvider op, Object key, Object value);

    /**
     * Method to add a map of values to the Map.
     * @param op ObjectProvider for the owner of the map. 
     * @param m The map to add.
     */ 
    void putAll(ObjectProvider op, Map m);

    /**
     * Method to remove a value from the Map.
     * @param op ObjectProvider for the owner of the map. 
     * @param key Key whose value is to be removed.
     * @return Value that was removed.
     */
    Object remove(ObjectProvider op, Object key);

    /**
     * Method to clear the map.
     * @param op ObjectProvider for the owner of the map. 
     */
    void clear(ObjectProvider op);

    /**
     * Accessor for the keys in the Map.
     * @return Keys for the Map.
     */
    SetStore keySetStore();

    /**
     * Accessor for the values in the Map.
     * @return Values for the Map.
     */
    SetStore valueSetStore();

    /**
     * Accessor for the entry set for the Map.
     * @return Entry set for the Map.
     */
    SetStore entrySetStore();

    /**
     * Method to update en embedded key in the map.
     * @param op ObjectProvider for the owner of the map
     * @param key The element
     * @param fieldNumber Field to update in the key
     * @param newValue The new value for the field
     * @return Whether the element was modified
     */
    boolean updateEmbeddedKey(ObjectProvider op, Object key, int fieldNumber, Object newValue);

    /**
     * Method to update en embedded value in the map.
     * @param op ObjectProvider for the owner of the map
     * @param value The element
     * @param fieldNumber Field to update in the value
     * @param newValue The new value for the field
     * @return Whether the element was modified
     */
    boolean updateEmbeddedValue(ObjectProvider op, Object value, int fieldNumber, Object newValue);
}