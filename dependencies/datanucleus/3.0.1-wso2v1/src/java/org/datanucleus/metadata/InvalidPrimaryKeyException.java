/**********************************************************************
Copyright (c) 2004 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.metadata;

import org.datanucleus.util.Localiser;

/**
 * Exception thrown when a primary key class is found to be invalid for some reason.
 * This is due to an invalid specification of MetaData, or maybe the class specified
 * is just wrong, but we just throw it as a meta-data issue.
 */
public class InvalidPrimaryKeyException extends InvalidMetaDataException
{
    /**
     * Constructor with message resource, message param
     * @param localiser message resources
     * @param key message resources key
     * @param param1 message resources param0
     */
    public InvalidPrimaryKeyException(Localiser localiser,
                                    String key,
                                    Object param1)
    {
        super(localiser, key, param1, "", "");
    }

    /**
     * Constructor with message resource, message params
     * @param localiser message resources
     * @param key message resources key
     * @param param1 message resources param0
     * @param param2 message resources param1
     */
    public InvalidPrimaryKeyException(Localiser localiser,
                                    String key,
                                    Object param1,
                                    Object param2)
    {
        super(localiser, key, param1, param2, "");
    }
    
    /**
     * Constructor with message resource, message params
     * @param localiser message resources
     * @param key message resources key
     * @param param1 message resources param0
     * @param param2 message resources param1
     * @param param3 message resources param2
     */
    public InvalidPrimaryKeyException(Localiser localiser,
                                    String key,
                                    Object param1,
                                    Object param2,
                                    Object param3)
    {
        super(localiser, key, param1, param2, param3);
        this.messageKey = key;
    }

    /**
     * Constructor with message resource, message params
     * @param localiser message resources
     * @param key message resources key
     * @param param1 message resources param1
     * @param param2 message resources param2
     * @param param3 message resources param3
     * @param param4 message resources param4
     */
    public InvalidPrimaryKeyException(Localiser localiser,
                                    String key,
                                    Object param1,
                                    Object param2,
                                    Object param3,
                                    Object param4)
    {
        super(localiser, key, param1, param2, param3, param4);
        this.messageKey = key;
    }
    
    /**
     * Constructor with message resource, message params
     * @param localiser message resources
     * @param key message resources key
     * @param param1 message resources param1
     * @param param2 message resources param2
     * @param param3 message resources param3
     * @param param4 message resources param4
     * @param param5 message resources param5
     */
    public InvalidPrimaryKeyException(Localiser localiser,
                                    String key,
                                    Object param1,
                                    Object param2,
                                    Object param3,
                                    Object param4,
                                    Object param5)
    {
        super(localiser, key, param1, param2, param3, param4, param5);
        this.messageKey = key;
    }    
}