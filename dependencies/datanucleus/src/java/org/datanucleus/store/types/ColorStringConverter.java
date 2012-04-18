/**********************************************************************
Copyright (c) 2010 Andy Jefferson and others. All rights reserved.
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
package org.datanucleus.store.types;

import java.awt.Color;

/**
 * Class to handle the conversion between java.awt.Color (RGBA) and a String form.
 */
public class ColorStringConverter implements ObjectStringConverter
{
    /* (non-Javadoc)
     * @see org.datanucleus.store.types.ObjectStringConverter#toObject(java.lang.String)
     */
    public Object toObject(String str)
    {
        if (str == null)
        {
            return null;
        }

        int componentLength = (str.length()-1)/4;
        String rStr = str.substring(1, 1+componentLength);
        String gStr = str.substring(1+componentLength, 1+2*componentLength);
        String bStr = str.substring(1+2*componentLength, 1+3*componentLength);
        String aStr = str.substring(1+3*componentLength);
        int r = Integer.parseInt(rStr, 16);
        int g = Integer.parseInt(gStr, 16);
        int b = Integer.parseInt(bStr, 16);
        int a = Integer.parseInt(aStr, 16);
        return new Color(r, g, b, a);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.store.types.ObjectStringConverter#toString(java.lang.Object)
     */
    public String toString(Object obj)
    {
        String str;
        if (obj instanceof Color)
        {
            Color c = (Color)obj;
            String r = (c.getRed() < 16) ? "0" + Integer.toHexString(c.getRed()) : Integer.toHexString(c.getRed());
            String g = (c.getGreen() < 16) ? "0" + Integer.toHexString(c.getGreen()) : Integer.toHexString(c.getGreen());
            String b = (c.getBlue() < 16) ? "0" + Integer.toHexString(c.getBlue()) : Integer.toHexString(c.getBlue());
            String a = (c.getAlpha() < 16) ? "0" + Integer.toHexString(c.getAlpha()) : Integer.toHexString(c.getAlpha());
            str = "#" + r + g + b + a;
        }
        else
        {
            str = (String)obj;
        }
        return str;
    }
}