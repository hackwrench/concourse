/*
 * Copyright (c) 2013-2015 Cinchapi, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cinchapi.concourse.server;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;

import org.apache.thrift.transport.TTransportException;
import org.cinchapi.concourse.ConcourseBaseTest;
import org.cinchapi.concourse.util.Environments;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link ConcourseServer}.
 * 
 * @author Jeff Nelson
 */
public class ConcourseServerTest extends ConcourseBaseTest {

    @Test(expected = IllegalStateException.class)
    public void testCannotStartServerWhenBufferAndDatabaseDirectoryAreSame()
            throws TTransportException {
        new ConcourseServer(1, System.getProperty("user.home"),
                System.getProperty("user.home"));
    }

    @Test(expected = IllegalStateException.class)
    public void testCannotStartServerWhenDefaultEnvironmentIsEmptyString()
            throws TTransportException, MalformedObjectNameException,
            InstanceAlreadyExistsException, MBeanRegistrationException,
            NotCompliantMBeanException {
        String oldDefault = GlobalState.DEFAULT_ENVIRONMENT;
        try {
            GlobalState.DEFAULT_ENVIRONMENT = "$$";
            new ConcourseServer(1, "buffer", "db");
        }
        finally {
            GlobalState.DEFAULT_ENVIRONMENT = oldDefault;
        }
    }

    @Test
    public void testFindEnvReturnsDefaultForEmptyString() {
        Assert.assertEquals(GlobalState.DEFAULT_ENVIRONMENT,
                Environments.sanitize(""));
    }

    @Test
    public void testFindEnvStripsNonAlphaNumChars() {
        String env = "$%&foo@3**";
        Assert.assertEquals("foo3", Environments.sanitize(env));
    }

    @Test
    public void testFindEnvStripsNonAlphaNumCharsInDefaultEnv() {
        String oldDefault = GlobalState.DEFAULT_ENVIRONMENT;
        GlobalState.DEFAULT_ENVIRONMENT = "%$#9blah@@3foo1#$";
        Assert.assertEquals("9blah3foo1", Environments.sanitize(""));
        GlobalState.DEFAULT_ENVIRONMENT = oldDefault;
    }
    
    @Test
    public void testFindEnvKeepsUnderScore() {
    	String env = "$_%&test_@envir==--onment*_*";
    	Assert.assertEquals("_test_environment_", 
    			Environments.sanitize(env));
    }

}
