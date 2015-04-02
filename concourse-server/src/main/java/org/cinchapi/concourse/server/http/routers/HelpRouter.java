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
package org.cinchapi.concourse.server.http.routers;

import org.cinchapi.concourse.server.ConcourseServer;
import org.cinchapi.concourse.server.http.Endpoint;
import org.cinchapi.concourse.server.http.Router;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * 
 * 
 * @author Jeff Nelson
 */
public class HelpRouter extends Router {

    /**
     * Construct a new instance.
     * @param concourse
     */
    public HelpRouter(ConcourseServer concourse) {
        super(concourse);
    }

    @Override
    public void routes() {
       get(new Endpoint(""){

        @Override
        protected JsonElement serve() throws Exception {
            return new JsonPrimitive("This is where the help goes");
        }
           
       });
        
    }

}
