/**********************************************************************
Copyright (c) 2005 Erik Bengtson and others. All rights reserved.
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
package org.datanucleus.exceptions;

/**
 * A <tt>ClassNotResolvedException</tt> is thrown if an attempt is
 * made to load a class that is cannot be found by the ClassLoaderResolver.
 *
 * @version $Revision: 1.5 $
 */
public class ClassNotResolvedException extends NucleusException
{
    /**
     * Constructs a class-not-detachable exception with the specified
     * detail message and nested exception.
     * @param msg the exception message
     * @param nested the nested exception(s).
     */
    public ClassNotResolvedException(String msg, Throwable nested)
    {
        super(msg, nested);
    }
    
    /**
     * Constructs a class-not-detachable exception with the specified
     * detail message and nested exception.
     * @param msg the exception message
     */
    public ClassNotResolvedException(String msg)
    {
        super(msg);
    }
}