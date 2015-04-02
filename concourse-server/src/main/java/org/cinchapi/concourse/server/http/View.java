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
package org.cinchapi.concourse.server.http;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import com.google.common.collect.Maps;

/**
 * A {@link View} is processed by a {@link Router} to display some front end
 * template.
 * 
 * @author Jeff Nelson
 * 
 */
public abstract class View extends BaseRewritableRoute {

    /**
     * An empty collection that should be returned from the {@link #serve()}
     * method if there is no data for the front end template.
     */
    protected final static Map<String, Object> NO_DATA = Collections
            .unmodifiableMap(Maps.<String, Object> newHashMap());

    /**
     * Construct a new instance
     * 
     * @param relativePath
     */
    public View(String relativePath) {
        super(relativePath);
    }

    @Override
    public final Object handle() {
        return template("templates" + File.separator + template()).render(
                serve());
    }

    /**
     * The name of the template to display. The View will look in the
     * {@link templates} folder at the root of the working directory for the
     * mustache template with the specified name.
     * 
     * @return the name of the view template (e.g. index.mustache)
     */
    protected abstract String template();

    /**
     * Serve the request and render the view.
     * <p>
     * Use this method to take care of any logic that determines whether the
     * request can be served (i.e. is it valid? does it need to be redirected?,
     * etc). Ultimately, this method should return any data (encapsulated in a
     * mapping from variable names to values) that the front end
     * {@link #template()} is expecting.
     * </p>
     * <p>
     * Return {@link #NO_DATA} if there is none to give to the front end
     * template.
     * </p>
     * 
     * @return the view data
     */
    protected abstract Map<String, Object> serve();

}
