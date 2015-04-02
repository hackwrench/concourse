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
package org.cinchapi.concourse.bugrepro;

import org.cinchapi.concourse.ConcourseIntegrationTest;
import org.cinchapi.concourse.thrift.Operator;
import org.cinchapi.concourse.time.Time;
import org.cinchapi.concourse.util.Convert;
import org.junit.Assert;
import org.junit.Test;

/**
 * Repro of <a href="https://cinchapi.atlassian.net/browse/CON-55">CON-55</a>
 * where a transaction deadlock would occur when range reading a key before
 * adding data against that key.
 * 
 * @author Jeff Nelson
 */
public class CON55 extends ConcourseIntegrationTest {

    @Test
    public void repro() {
        client.stage();
        client.find("ipeds_id", Operator.EQUALS, Convert.stringToJava("1"));
        long record = Time.now();
        client.add("ipeds_id", Convert.stringToJava("1"), record);
        client.add("avg_net_price_income_below_30000",
                Convert.stringToJava("15759"), record);
        client.add("avg_net_price_income_30001_to_48000",
                Convert.stringToJava("17292"), record);
        client.add("avg_net_price_income_48001_to_75000",
                Convert.stringToJava("19059"), record);
        client.add("avg_net_price_income_75001_110000",
                Convert.stringToJava("19734"), record);
        client.add("avg_net_price_income_above_110000",
                Convert.stringToJava("23351"), record);
        Assert.assertTrue(client.commit());
    }

}
