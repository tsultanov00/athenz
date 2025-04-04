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
package com.yahoo.athenz.instance.provider.impl;

import com.yahoo.athenz.auth.token.AccessToken;
import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.auth.util.CryptoException;
import com.yahoo.athenz.common.server.http.HttpDriver;
import com.yahoo.athenz.instance.provider.ExternalCredentialsProvider;
import com.yahoo.athenz.instance.provider.InstanceConfirmation;
import com.yahoo.athenz.instance.provider.InstanceProvider;
import com.yahoo.athenz.instance.provider.ProviderResourceException;
import com.yahoo.athenz.zts.ExternalCredentialsResponse;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class InstanceAzureProviderTest {

    private final File ecPrivateKey = new File("./src/test/resources/unit_test_ec_private.key");

    @BeforeMethod
    public void setup() {
        System.setProperty(InstanceAzureProvider.AZURE_PROP_DNS_SUFFIX, "azure.cloud");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_MGMT_MAX_RETRIES, "0");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_MGMT_CONNECT_TIMEOUT_MS, "100");
    }

    @AfterMethod
    public void shutdown() {
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_DNS_SUFFIX);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_MGMT_MAX_RETRIES);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_MGMT_CONNECT_TIMEOUT_MS);
    }

    private void setUpExternalCredentialsProvider(InstanceAzureProvider provider) {
        ExternalCredentialsProvider credentialsProvider = Mockito.mock(ExternalCredentialsProvider.class);
        provider.setExternalCredentialsProvider(credentialsProvider);
        ExternalCredentialsResponse response = new ExternalCredentialsResponse();
        response.setAttributes(new HashMap<>());
        response.getAttributes().put("accessToken", "access-token");
        Mockito.when(credentialsProvider.getExternalCredentials(any(), any(), any())).thenReturn(response);
    }

    @Test
    public void testInitializeWithOpenIdConfig() throws IOException {

        File issuerFile = new File("./src/test/resources/config-openid/");
        File configFile = new File("./src/test/resources/config-openid/.well-known/openid-configuration");
        createEmptyConfigFile(configFile);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + issuerFile.getCanonicalPath());

        // std test where the http driver will return null for the config object

        InstanceAzureProvider provider = new InstanceAzureProvider();
        try {
            provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);
            fail();
        } catch (CryptoException ex) {
            assertTrue(ex.getMessage().contains("Jwks uri must be specified"));
        }
        Files.delete(configFile.toPath());
    }

    @Test
    public void testInitializeDefaults() throws IOException {

        File configUri = new File("./src/test/resources/azure-openid-uri.json");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        assertEquals(provider.getProviderScheme(), InstanceProvider.Scheme.CLASS);
        assertTrue(provider.dnsSuffixes.contains("azure.cloud"));
        assertEquals(provider.azureJwksUri, "file://src/test/resources/keys.json");
        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
    }

    @Test
    public void testInitializeEmptyValues() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, false);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_DNS_SUFFIX);

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        assertTrue(provider.dnsSuffixes.isEmpty());
        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);
    }

    @Test
    public void testConfirmInstance() throws IOException, ProviderResourceException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        InstanceAzureProvider provider = new InstanceAzureProvider();
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        ExternalCredentialsResponse credentialsResponse = new ExternalCredentialsResponse();
        credentialsResponse.setAttributes(new HashMap<>());
        credentialsResponse.getAttributes().put("accessToken", "access-token");
        provider.externalCredentialsProvider = Mockito.mock(ExternalCredentialsProvider.class);
        Mockito.when(provider.externalCredentialsProvider.getExternalCredentials(eq("azure"), eq("athenz"), argThat(arg -> {
            return arg.getClientId().equals("athenz.azure.azure-client") &&
                   arg.getAttributes().get("athenzScope").equals("openid athenz.azure:role.azure-client") &&
                   arg.getAttributes().size() == 1;
        }))).thenReturn(credentialsResponse);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));
        provider.httpDriver = setupHttpDriver();

        InstanceConfirmation providerConfirm = provider.confirmInstance(confirmation);
        assertNotNull(providerConfirm);

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceProviderConfig() throws IOException, ProviderResourceException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_PROVIDER, "athenz.azure.provider");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.provider");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));
        provider.httpDriver = setupHttpDriver();

        InstanceConfirmation providerConfirm = provider.confirmInstance(confirmation);
        assertNotNull(providerConfirm);

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_PROVIDER);
        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testRefreshInstance() throws IOException, ProviderResourceException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());
        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        String vmDetailsWithUserAssignedIdentities =
                "{\n" +
                "  \"name\": \"athenz-client\",\n" +
                "  \"id\": \"/subscriptions/123456/resourceGroups/Athenz/providers/Microsoft.Compute/virtualMachines/athenz-client\",\n" +
                "  \"location\": \"westus2\",\n" +
                "  \"tags\": {\n" +
                "    \"athenz\": \"athenz.backend\"\n" +
                "  },\n" +
                "  \"identity\": {\n" +
                "    \"type\": \"UserAssigned\",\n" +
                "    \"userAssignedIdentities\": {\n" +
                "      \"/subscriptions/23423423-d46a-45db-aad6-29a1fdab4f86/resourceGroups/system/providers/Microsoft.ManagedIdentity/userAssignedIdentities/my-id\": {\n" +
                "        \"principalId\": \"111111-2222-3333-4444-555555555\",\n" +
                "        \"clientId\": \"f6ed0c62-f2cb-4ebc-8c4e-e81c43887914\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"properties\": {\n" +
                "    \"vmId\": \"2222-3333\"\n" +
                "  }\n" +
                "}";

        provider.httpDriver = setupHttpDriver(vmDetailsWithUserAssignedIdentities);

        InstanceConfirmation providerConfirm = provider.refreshInstance(confirmation);
        assertNotNull(providerConfirm);

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    private HttpDriver setupHttpDriver() throws IOException {
        final String vmDetails =
                "{\n" +
                "  \"name\": \"athenz-client\",\n" +
                "  \"id\": \"/subscriptions/123456/resourceGroups/Athenz/providers/Microsoft.Compute/virtualMachines/athenz-client\",\n" +
                "  \"location\": \"westus2\",\n" +
                "  \"tags\": {\n" +
                "    \"athenz\": \"athenz.backend\"\n" +
                "  },\n" +
                "  \"identity\": {\n" +
                "    \"type\": \"SystemAssigned, UserAssigned\",\n" +
                "    \"principalId\": \"111111-2222-3333-4444-555555555\",\n" +
                "    \"tenantId\": \"222222-3333-4444-5555-66666666\"\n" +
                "  },\n" +
                "  \"properties\": {\n" +
                "    \"vmId\": \"2222-3333\"\n" +
                "  }\n" +
                "}";
        return setupHttpDriver(vmDetails);
    }

    private HttpDriver setupHttpDriver(String vmDetails) throws IOException {

        HttpDriver httpDriver = Mockito.mock(HttpDriver.class);

        final String vmUri = "https://management.azure.com/subscriptions/1111-2222/resourceGroups/prod" +
                "/providers/Microsoft.Compute/virtualMachines/athenz-client?api-version=2020-06-01";

        Map<String, String> vmHeaders = new HashMap<>();
        vmHeaders.put("Authorization", "Bearer access-token");
        Mockito.when(httpDriver.doGet(vmUri, vmHeaders)).thenReturn(vmDetails);

        return httpDriver;
    }

    @Test
    public void testConfirmInstanceInvalidAttestationData() {

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setAttestationData("invalid-json");

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to parse attestation data"));
        }

        provider.close();
    }

    @Test
    public void testConfirmInstanceAzureSubscriptionIssues() throws IOException {

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to extract Azure Subscription id"));
        }

        // add the subscription but different from what's in the data object

        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-3333");

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Azure Subscription Id mismatch"));
        }

        provider.close();
    }

    @Test
    public void testConfirmInstanceSanDnsMismatch() throws IOException {

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.test.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to validate certificate request hostnames"));
        }

        provider.close();
    }

    @Test
    public void testConfirmInstanceInvalidAccessToken() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, false);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken("invalid-token");

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceAudienceMismatch() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts-nomatch");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceUnableToFetchVMDetails() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        // first with null http-driver

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        // then without access token from the external credentials provider

        provider.httpDriver = Mockito.mock(HttpDriver.class);
        ExternalCredentialsProvider externalCredentialsProvider = Mockito.mock(ExternalCredentialsProvider.class);
        ExternalCredentialsResponse externalCredentialsResponse = new ExternalCredentialsResponse();
        externalCredentialsResponse.setAttributes(new HashMap<>());
        Mockito.when(externalCredentialsProvider.getExternalCredentials(any(), any(), any())).thenReturn(externalCredentialsResponse);
        provider.setExternalCredentialsProvider(externalCredentialsProvider);
        confirmation.setAttributes(attributes);

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        // then with null-responses
        setUpExternalCredentialsProvider(provider);
        confirmation.setAttributes(attributes);

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        // then with mock throwing an exception

        Mockito.when(provider.httpDriver.doGet(any(), any())).thenThrow(new IllegalArgumentException("bad client"));
        confirmation.setAttributes(attributes);

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceInvalidVMDetails() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        HttpDriver httpDriver = Mockito.mock(HttpDriver.class);
        Mockito.when(httpDriver.doGet(any(), any())).thenReturn("invalid-vmdetails");
        provider.httpDriver = httpDriver;

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceSubjectMismatch() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        final String vmDetails =
                "{\n" +
                        "  \"name\": \"athenz-client\",\n" +
                        "  \"id\": \"/subscriptions/123456/resourceGroups/Athenz/providers/Microsoft.Compute/virtualMachines/athenz-client\",\n" +
                        "  \"location\": \"westus2\",\n" +
                        "  \"tags\": {\n" +
                        "    \"athenz\": \"athenz.backend\"\n" +
                        "  },\n" +
                        "  \"identity\": {\n" +
                        "    \"type\": \"SystemAssigned, UserAssigned\",\n" +
                        "    \"principalId\": \"4444-555555555\",\n" +
                        "    \"tenantId\": \"222222-3333-4444-5555-66666666\"\n" +
                        "  },\n" +
                        "  \"properties\": {\n" +
                        "    \"vmId\": \"2222-3333\"\n" +
                        "  }\n" +
                        "}";
        HttpDriver httpDriver = Mockito.mock(HttpDriver.class);
        Mockito.when(httpDriver.doGet(any(), any())).thenReturn(vmDetails);
        provider.httpDriver = httpDriver;

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceServiceNameMismatch() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("api");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "api.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        final String vmDetails =
                "{\n" +
                        "  \"name\": \"athenz-client\",\n" +
                        "  \"id\": \"/subscriptions/123456/resourceGroups/Athenz/providers/Microsoft.Compute/virtualMachines/athenz-client\",\n" +
                        "  \"location\": \"westus2\",\n" +
                        "  \"tags\": {\n" +
                        "    \"athenz\": \"athenz.backend\"\n" +
                        "  },\n" +
                        "  \"identity\": {\n" +
                        "    \"type\": \"SystemAssigned, UserAssigned\",\n" +
                        "    \"principalId\": \"111111-2222-3333-4444-555555555\",\n" +
                        "    \"tenantId\": \"222222-3333-4444-5555-66666666\"\n" +
                        "  },\n" +
                        "  \"properties\": {\n" +
                        "    \"vmId\": \"2222-3333\"\n" +
                        "  }\n" +
                        "}";
        HttpDriver httpDriver = Mockito.mock(HttpDriver.class);
        Mockito.when(httpDriver.doGet(any(), any())).thenReturn(vmDetails);
        provider.httpDriver = httpDriver;

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceVMIdMismatch() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.westus2");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        final String vmDetails =
                "{\n" +
                        "  \"name\": \"athenz-client\",\n" +
                        "  \"id\": \"/subscriptions/123456/resourceGroups/Athenz/providers/Microsoft.Compute/virtualMachines/athenz-client\",\n" +
                        "  \"location\": \"westus2\",\n" +
                        "  \"tags\": {\n" +
                        "    \"athenz\": \"athenz.backend\"\n" +
                        "  },\n" +
                        "  \"identity\": {\n" +
                        "    \"type\": \"SystemAssigned, UserAssigned\",\n" +
                        "    \"principalId\": \"111111-2222-3333-4444-555555555\",\n" +
                        "    \"tenantId\": \"222222-3333-4444-5555-66666666\"\n" +
                        "  },\n" +
                        "  \"properties\": {\n" +
                        "    \"vmId\": \"2222-5555\"\n" +
                        "  }\n" +
                        "}";
        HttpDriver httpDriver = Mockito.mock(HttpDriver.class);
        Mockito.when(httpDriver.doGet(any(), any())).thenReturn(vmDetails);
        provider.httpDriver = httpDriver;

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    @Test
    public void testConfirmInstanceWithoutCredentialsProvider() {
        InstanceAzureProvider provider = new InstanceAzureProvider();
        provider.setExternalCredentialsProvider(null);
        try {
            provider.confirmInstance(null);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("External credentials provider must be configured for the Azure provider"));
        }
    }

    @Test
    public void testConfirmInstanceProviderMismatch() throws IOException {

        File configFile = new File("./src/test/resources/azure-openid.json");
        File jwksUri = new File("./src/test/resources/azure-jwks.json");
        createOpenIdConfigFile(configFile, jwksUri, true);

        System.setProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI, "https://azure-zts");
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI, "file://" + configFile.getCanonicalPath());
        System.setProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI, "file://" + jwksUri.getCanonicalPath());

        InstanceAzureProvider provider = new InstanceAzureProvider();
        setUpExternalCredentialsProvider(provider);
        provider.initialize("provider", "com.yahoo.athenz.instance.provider.impl.InstanceAzureProvider", null, null);

        InstanceConfirmation confirmation = new InstanceConfirmation();
        confirmation.setDomain("athenz");
        confirmation.setService("backend");
        confirmation.setProvider("athenz.azure.eastus1");
        Map<String, String> attributes = new HashMap<>();
        attributes.put(InstanceProvider.ZTS_INSTANCE_AZURE_SUBSCRIPTION, "1111-2222");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_DNS, "backend.athenz.azure.cloud");
        attributes.put(InstanceProvider.ZTS_INSTANCE_SAN_URI, "athenz://instanceid/athenz.azure.uswest2/2222-3333");
        confirmation.setAttributes(attributes);

        AzureAttestationData data = new AzureAttestationData();
        data.setVmId("2222-3333");
        data.setSubscriptionId("1111-2222");
        data.setResourceGroupName("prod");
        data.setName("athenz-client");
        data.setLocation("westus2");
        data.setToken(createAccessToken());

        confirmation.setAttestationData(provider.jsonMapper.writeValueAsString(data));

        final String vmDetails =
                "{\n" +
                        "  \"name\": \"athenz-client\",\n" +
                        "  \"id\": \"/subscriptions/123456/resourceGroups/Athenz/providers/Microsoft.Compute/virtualMachines/athenz-client\",\n" +
                        "  \"location\": \"westus2\",\n" +
                        "  \"tags\": {\n" +
                        "    \"athenz\": \"athenz.backend\"\n" +
                        "  },\n" +
                        "  \"identity\": {\n" +
                        "    \"type\": \"SystemAssigned, UserAssigned\",\n" +
                        "    \"principalId\": \"111111-2222-3333-4444-555555555\",\n" +
                        "    \"tenantId\": \"222222-3333-4444-5555-66666666\"\n" +
                        "  },\n" +
                        "  \"properties\": {\n" +
                        "    \"vmId\": \"2222-3333\"\n" +
                        "  }\n" +
                        "}";
        HttpDriver httpDriver = Mockito.mock(HttpDriver.class);
        Mockito.when(httpDriver.doGet(any(), any())).thenReturn(vmDetails);
        provider.httpDriver = httpDriver;

        try {
            provider.confirmInstance(confirmation);
            fail();
        } catch (ProviderResourceException ex) {
            assertTrue(ex.getMessage().contains("Unable to verify instance identity credentials"));
        }

        provider.close();

        System.clearProperty(InstanceAzureProvider.AZURE_PROP_ZTS_RESOURCE_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_CONFIG_URI);
        System.clearProperty(InstanceAzureProvider.AZURE_PROP_OPENID_JWKS_URI);

        removeOpenIdConfigFile(configFile, jwksUri);
    }

    private String createAccessToken() {

        long now = System.currentTimeMillis() / 1000;

        AccessToken accessToken = new AccessToken();
        accessToken.setAuthTime(now);
        accessToken.setSubject("111111-2222-3333-4444-555555555");
        accessToken.setExpiryTime(now + 3600);
        accessToken.setIssueTime(now);
        accessToken.setClientId("azure-client");
        accessToken.setAudience("https://azure-zts");
        accessToken.setVersion(1);
        accessToken.setIssuer("azure");

        // now get the signed token

        PrivateKey privateKey = Crypto.loadPrivateKey(ecPrivateKey);
        return accessToken.getSignedToken(privateKey, "eckey1", "ES256");
    }

    private void removeOpenIdConfigFile(File configFile, File jwksUri) {
        try {
            Files.delete(configFile.toPath());
        } catch (Exception ignored) {
        }
        try {
            Files.delete(jwksUri.toPath());
        } catch (Exception ignored) {
        }
    }

    private void createEmptyConfigFile(File configFile) throws IOException {
        final String fileContents = "{}";
        Files.createDirectories(configFile.toPath().getParent());
        Files.write(configFile.toPath(), fileContents.getBytes());
    }

    private void createOpenIdConfigFile(File configFile, File jwksUri, boolean createJkws) throws IOException {

        final String fileContents = "{\n" +
                "    \"jwks_uri\": \"file://" + jwksUri.getCanonicalPath() + "\"\n" +
                "}";
        Files.createDirectories(configFile.toPath().getParent());
        Files.write(configFile.toPath(), fileContents.getBytes());

        if (createJkws) {
            final String keyContents = "{\n" +
                    "    \"keys\": [\n" +
                    "        {\n" +
                    "        \"kty\": \"EC\",\n" +
                    "        \"kid\": \"eckey1\",\n" +
                    "        \"alg\": \"ES256\",\n" +
                    "        \"use\": \"sig\",\n" +
                    "        \"crv\": \"P-256\",\n" +
                    "        \"x\": \"AI0x6wEUk5T0hslaT83DNVy5r98XnG7HAjQynjCrcdCe\",\n" +
                    "        \"y\": \"ATdV2ebpefqBli_SXZwvL3-7OiD3MTryGbR-zRSFZ_s=\"\n" +
                    "        }\n" +
                    "    ]\n" +
                    "}";
            Files.write(jwksUri.toPath(), keyContents.getBytes());
        }
    }
}
