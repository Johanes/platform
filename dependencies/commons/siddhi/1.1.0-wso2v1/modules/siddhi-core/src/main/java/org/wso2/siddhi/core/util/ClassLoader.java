/*
*  Copyright (c) 2005-2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.siddhi.core.util;

import org.wso2.siddhi.core.exception.CannotLoadClassException;

public class ClassLoader {

    public static Object loadClass(String name) throws CannotLoadClassException {
        try {
            return Class.forName(name).newInstance();
        } catch (InstantiationException e) {
            throw new CannotLoadClassException("Cannot load class: " + name, e);
        } catch (IllegalAccessException e) {
            throw new CannotLoadClassException("Cannot load class: " + name, e);
        } catch (ClassNotFoundException e) {
            throw new CannotLoadClassException("Cannot load class: " + name, e);
        }

    }
}
