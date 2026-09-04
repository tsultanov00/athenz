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
const {
    toSelfServeItem,
    toSelfServeSearchResponse,
    toZmsSearchParams,
} = require('../../../server/utils/selfServeContract');

describe('selfServeContract', () => {
    it('maps the designed ZMS search payload into UI items', () => {
        const mapped = toSelfServeSearchResponse({
            roles: [
                {
                    domainName: 'paranoids.tools',
                    roleName: 'security-platform-users',
                    description: 'Day to day access',
                    memberStatus: 'NONE',
                    memberCount: 1876,
                    roleOwner: 'paranoids-tools@example.com',
                    selfRenew: true,
                    selfRenewMins: 43200,
                    reviewEnabled: true,
                    auditEnabled: false,
                    deleteProtection: true,
                    memberExpiryDays: 90,
                },
                {
                    name: 'athenz.prod:role.security-platform-auditors',
                    description: 'Audit coverage',
                    approved: false,
                    expiration: '2026-09-12T00:00:00.000Z',
                    requestTime: '2026-08-12T15:01:00.000Z',
                    auditRef: 'Q3 scan coverage',
                },
            ],
            groups: [
                {
                    domainName: 'paranoids.tools',
                    groupName: 'security-champions',
                    description: 'Champions group',
                    memberStatus: 'member',
                    memberCount: 54,
                },
            ],
        });

        expect(mapped.list).toHaveLength(3);
        expect(mapped.list[0]).toEqual(
            expect.objectContaining({
                type: 'role',
                domainName: 'paranoids.tools',
                name: 'security-platform-users',
                memberStatus: 'none',
                memberCount: 1876,
                owner: 'paranoids-tools@example.com',
                selfRenew: true,
                selfRenewMins: 43200,
                reviewEnabled: true,
                deleteProtection: true,
                maxExpiryDays: 90,
            })
        );
        expect(mapped.list[1]).toEqual(
            expect.objectContaining({
                type: 'role',
                domainName: 'athenz.prod',
                name: 'security-platform-auditors',
                memberStatus: 'pending',
                expiration: '2026-09-12T00:00:00.000Z',
                requestedOn: '2026-08-12T15:01:00.000Z',
                requestJustification: 'Q3 scan coverage',
            })
        );
        expect(mapped.list[2]).toEqual(
            expect.objectContaining({
                type: 'group',
                name: 'security-champions',
                memberStatus: 'member',
            })
        );
        expect(mapped.domains).toEqual(['athenz.prod', 'paranoids.tools']);
        expect(mapped.membershipCount).toBeUndefined();
    });

    it('counts memberships only for member=true searches without a backend total', () => {
        const mapped = toSelfServeSearchResponse(
            {
                list: [
                    {
                        type: 'role',
                        domainName: 'a',
                        name: 'r1',
                        memberStatus: 'member',
                    },
                    {
                        type: 'role',
                        domainName: 'a',
                        name: 'r2',
                        memberStatus: 'pending',
                    },
                ],
            },
            { member: true }
        );
        expect(mapped.membershipCount).toBe(1);
    });

    it('treats a group memberName as inheritedFrom', () => {
        const item = toSelfServeItem({
            domainName: 'paranoids.tools',
            name: 'scanner-users',
            memberStatus: 'member',
            memberName: 'paranoids.tools:group.security-champions',
        });
        expect(item.inheritedFrom).toBe(
            'paranoids.tools:group.security-champions'
        );
        expect(item.memberStatus).toBe('member');
    });

    it('flattens a nested membership object', () => {
        const item = toSelfServeItem({
            domainName: 'sports.prod',
            roleName: 'readers',
            membership: {
                approved: false,
                expiration: '2026-10-01T00:00:00.000Z',
                requestTime: '2026-08-01T00:00:00.000Z',
            },
        });
        expect(item.memberStatus).toBe('pending');
        expect(item.expiration).toBe('2026-10-01T00:00:00.000Z');
        expect(item.requestedOn).toBe('2026-08-01T00:00:00.000Z');
    });

    it('sends designed ZMS search query names plus aliases', () => {
        expect(
            toZmsSearchParams({
                substring: 'security-platform',
                domain: 'paranoids.tools',
                member: 'true',
                skip: 'abc',
            })
        ).toEqual(
            expect.objectContaining({
                substring: 'security-platform',
                query: 'security-platform',
                domain: 'paranoids.tools',
                domainName: 'paranoids.tools',
                member: true,
                skip: 'abc',
                next: 'abc',
            })
        );
    });
});
