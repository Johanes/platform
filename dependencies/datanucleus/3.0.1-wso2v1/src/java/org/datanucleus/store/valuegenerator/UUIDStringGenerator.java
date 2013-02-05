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
package org.datanucleus.store.valuegenerator;

import java.util.Properties;

import org.datanucleus.util.TypeConversionHelper;

/**
 * Value generator for a UUID String format.
 * Results in Strings of length 16 characters, containing the IP address of the local machine
 * as per the JDO2 spec section 18.6.1.
 */
public class UUIDStringGenerator extends AbstractUUIDGenerator
{
    /**
     * Constructor.
     * @param name Symbolic name for this generator
     * @param props Properties controlling its behaviour
     */
    public UUIDStringGenerator(String name, Properties props)
    {
        super(name, props);
    }

    /**
     * Create an identifier with the form "IIIIJJJJHHLLLLCC".
     * Where IIII is the IP address, JJJJ is something unique across JVMs,
     * HH is the High Time, LLLL is the low time, and CC is a count.
     * @return The identifier
     */
    protected String getIdentifier()
    {
        StringBuffer str = new StringBuffer(16);

        str.append(TypeConversionHelper.getStringFromInt(IP_ADDRESS));
        str.append(TypeConversionHelper.getStringFromInt(JVM_UNIQUE));
        short timeHigh = (short) (System.currentTimeMillis() >>> 32);
        str.append(TypeConversionHelper.getStringFromShort(timeHigh));
        int timeLow = (int) System.currentTimeMillis();
        str.append(TypeConversionHelper.getStringFromInt(timeLow));
        str.append(TypeConversionHelper.getStringFromShort(getCount()));

        return str.toString();
    }
}