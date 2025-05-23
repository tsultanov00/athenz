/*
 * Copyright The Athenz Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.athenz.common.server.store.impl;

import com.yahoo.athenz.common.server.store.ResourceValidator;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class NoOpResourceValidatorFactoryTest {

    @Test
    public void testCreate() {
        NoOpResourceValidatorFactory factory = new NoOpResourceValidatorFactory();
        ResourceValidator validator = factory.create();
        assertNotNull(validator);

        // validate some members - we should always return true

        assertTrue(validator.validateRoleMember("domain", "role", "user"));
        assertTrue(validator.validateGroupMember("domain", "group", "user"));
    }
}
