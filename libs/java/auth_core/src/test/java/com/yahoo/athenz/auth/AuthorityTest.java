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
package com.yahoo.athenz.auth;

import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

import static org.testng.Assert.*;

public class AuthorityTest {

    @Test
    public void testAuthority() {

        Authority authority = new Authority() {
            @Override
            public void initialize() {
            }

            @Override
            public String getDomain() {
                return null;
            }

            @Override
            public String getHeader() {
                return null;
            }

            @Override
            public Principal authenticate(String creds, String remoteAddr, String httpMethod, StringBuilder errMsg) {
                return null;
            }

            @Override
            public boolean isAttributeSet(String username, String attribute) {
                return attribute.equals("local");
            }

            @Override
            public boolean isAttributeRevocable(String attribute) {
                return attribute.equals("local");
            }

            @Override
            public Date getDateAttribute(String username, String attribute) {
                return ("expiry".equals(attribute)) ? new Date() : null;
            }

            @Override
            public String getUserEmail(String username) {
                return username + "@example.com";
            }

            @Override
            public String getUserManager(String username) {
                return username + "-manager";
            }

            @Override
            public UserType getUserType(String username) {
                return "john".equals(username) ? UserType.USER_ACTIVE : UserType.USER_SUSPENDED;
            }
        };

        assertNull(authority.getAuthenticateChallenge());
        assertEquals(authority.getCredSource(), Authority.CredSource.HEADER);
        assertTrue(authority.allowAuthorization());
        assertEquals(authority.getUserDomainName("user"), "user");
        assertTrue(authority.isValidUser("john"));
        assertEquals(authority.getUserType("john"), Authority.UserType.USER_ACTIVE);
        assertEquals(authority.getUserType("joe"), Authority.UserType.USER_SUSPENDED);
        assertNull(authority.authenticate((X509Certificate[]) null, null));
        assertNull(authority.authenticate((HttpServletRequest) null, null));
        Set<String> attrSet = authority.booleanAttributesSupported();
        assertTrue(attrSet.isEmpty());
        attrSet = authority.dateAttributesSupported();
        assertTrue(attrSet.isEmpty());

        assertTrue(authority.isAttributeSet("john", "local"));
        assertFalse(authority.isAttributeSet("john", "remote"));
        assertTrue(authority.isAttributeRevocable("local"));
        assertFalse(authority.isAttributeRevocable("remote"));
        assertNull(authority.getDateAttribute("john", "review"));
        assertNotNull(authority.getDateAttribute("john", "expiry"));
        assertEquals(authority.getUserEmail("john"), "john@example.com");
        assertEquals(authority.getUserManager("john"), "john-manager");
        assertEquals(authority.getID(), "Auth-ID");
    }

    @Test
    public void testAuthorityDefaults() {

        Authority authority = new Authority() {
            @Override
            public void initialize() {
            }

            @Override
            public String getDomain() {
                return null;
            }

            @Override
            public String getHeader() {
                return null;
            }

            @Override
            public boolean isValidUser(String username) {
                return "john".equals(username);
            }

            @Override
            public Principal authenticate(String creds, String remoteAddr, String httpMethod, StringBuilder errMsg) {
                return null;
            }
        };

        assertNull(authority.getAuthenticateChallenge());
        assertEquals(authority.getCredSource(), Authority.CredSource.HEADER);
        assertTrue(authority.allowAuthorization());
        assertEquals(authority.getUserDomainName("user"), "user");
        assertTrue(authority.isValidUser("john"));
        assertEquals(authority.getUserType("john"), Authority.UserType.USER_ACTIVE);
        assertEquals(authority.getUserType("joe"), Authority.UserType.USER_INVALID);
        assertNull(authority.authenticate((X509Certificate[]) null, null));
        assertNull(authority.authenticate((HttpServletRequest) null, null));
        assertNull(authority.authenticate("creds", "127.0.0.1", "GET", null));
        assertTrue(authority.booleanAttributesSupported().isEmpty());
        assertFalse(authority.isAttributeSet("john", "remote"));
        assertTrue(authority.isAttributeRevocable("remove"));
        assertTrue(authority.dateAttributesSupported().isEmpty());
        assertNull(authority.getDateAttribute("john", "review"));
        assertNull(authority.getUserEmail("john"), "john@example.com");
        assertEquals(authority.getID(), "Auth-ID");
        assertTrue(authority.getPrincipals(EnumSet.of(Principal.State.ACTIVE)).isEmpty());
        assertNull(authority.getUserManager("john"));
    }
}
