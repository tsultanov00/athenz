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

package com.oath.auth;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class KeyRefresherExceptionTest {

    @Test
    public void testKeyRefresherException() {

        KeyRefresherException keyRefresherException = new KeyRefresherException();
        assertNull(keyRefresherException.getMessage());
        
        keyRefresherException = new KeyRefresherException("exception");
        assertEquals(keyRefresherException.getMessage(), "exception");
        
        keyRefresherException = new KeyRefresherException(new Throwable("new throwable"));
        assertEquals(keyRefresherException.getMessage(), "java.lang.Throwable: new throwable");

        keyRefresherException = new KeyRefresherException("exception", new Throwable("new throwable"));
        assertEquals(keyRefresherException.getMessage(), "exception");
    }
}
