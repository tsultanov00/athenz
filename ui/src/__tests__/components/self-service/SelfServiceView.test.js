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
import React from 'react';
import { fireEvent, waitFor, screen } from '@testing-library/react';
import SelfServiceView from '../../../components/self-service/SelfServiceView';
import { renderWithRedux } from '../../../tests_utils/ComponentsTestUtils';
import MockApi from '../../../mock/MockApi';

const SEARCH_RESULTS = {
    list: [
        {
            type: 'role',
            domainName: 'paranoids.tools',
            name: 'security-platform-users',
            description: 'Day to day access to the Security Platform console.',
            memberCount: 1876,
            memberStatus: 'none',
            owner: 'paranoids-tools@example.com',
            maxExpiryDays: 90,
        },
        {
            type: 'group',
            domainName: 'paranoids.tools',
            name: 'security-platform-reviewers',
            description: 'Review Security Platform scan exceptions.',
            memberCount: 11,
            memberStatus: 'none',
            owner: 'paranoids-tools@example.com',
        },
        {
            type: 'role',
            domainName: 'athenz.prod',
            name: 'security-platform-auditors',
            description: 'Audit Security Platform scan coverage.',
            memberCount: 17,
            memberStatus: 'pending',
            owner: 'athenz-grc@example.com',
            auditEnabled: true,
        },
        {
            type: 'role',
            domainName: 'paranoids.tools',
            name: 'scanner-users',
            description: 'Run scans against domains you own.',
            memberCount: 733,
            memberStatus: 'member',
            owner: 'paranoids-tools@example.com',
            inheritedFrom: 'paranoids.tools:group.security-champions',
        },
        {
            type: 'group',
            domainName: 'paranoids.tools',
            name: 'security-champions',
            description:
                'Security champions for domains onboarded to the platform.',
            memberCount: 54,
            memberStatus: 'member',
            owner: 'paranoids-tools@example.com',
        },
    ],
    domains: ['athenz.prod', 'paranoids.tools'],
    membershipCount: 4,
};

const MEMBERSHIPS = {
    list: [
        ...SEARCH_RESULTS.list.filter((item) => item.memberStatus !== 'none'),
        {
            type: 'role',
            domainName: 'paranoids.tools',
            name: 'scanner-admins',
            description: 'Admin access to scans.',
            memberCount: 3,
            memberStatus: 'member',
            selfRenew: true,
            maxExpiryDays: 30,
            expiration: '2026-12-30T00:00:00.000Z',
        },
        {
            type: 'group',
            domainName: 'paranoids.tools',
            name: 'scanner-operators',
            memberCount: 8,
            memberStatus: 'member',
            selfRenew: true,
            maxExpiryDays: 30,
        },
    ],
    domains: SEARCH_RESULTS.domains,
    membershipCount: 4,
};

const EMPTY_SEARCH = {
    list: [],
    domains: SEARCH_RESULTS.domains,
    membershipCount: 4,
};

describe('SelfServiceView', () => {
    beforeEach(() => {
        MockApi.setMockApi({
            getPendingDomainMembersList: jest.fn().mockResolvedValue([]),
            getReviewGroups: jest.fn().mockReturnValue([]),
            getReviewRoles: jest.fn().mockReturnValue([]),
            getPageFeatureFlag: jest.fn().mockResolvedValue({}),
            searchSelfServe: jest
                .fn()
                .mockImplementation((substring, domain, member) => {
                    if (member) {
                        return Promise.resolve(MEMBERSHIPS);
                    }
                    if (substring) {
                        return Promise.resolve(SEARCH_RESULTS);
                    }
                    return Promise.resolve(EMPTY_SEARCH);
                }),
            updateSelfServe: jest.fn().mockResolvedValue({}),
        });
    });

    afterEach(() => {
        MockApi.cleanMockApi();
    });

    it('should render the find roles empty state', async () => {
        const { getByTestId } = renderWithRedux(
            <SelfServiceView userName='tsultanov' _csrf='csrf' />
        );
        await waitFor(() =>
            expect(getByTestId('self-service')).toBeInTheDocument()
        );
        expect(getByTestId('self-service')).toMatchSnapshot();
        expect(
            screen.getByText(/Search by name or description/i)
        ).toBeInTheDocument();
    });

    async function searchForSecurityPlatform() {
        await waitFor(() =>
            expect(
                screen.getByTestId('self-service-search-bar')
            ).toBeInTheDocument()
        );
        fireEvent.change(
            screen.getByPlaceholderText('Search by name or description'),
            {
                target: { value: 'security-platform' },
            }
        );
        fireEvent.click(screen.getByTestId('self-service-search-button'));
        await waitFor(() =>
            expect(
                screen.getByText('security-platform-users')
            ).toBeInTheDocument()
        );
    }

    it('should group search results for roles and groups', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await searchForSecurityPlatform();
        expect(screen.getByText('Roles (3)')).toBeInTheDocument();
        expect(screen.getByText('Groups (2)')).toBeInTheDocument();
        expect(
            screen.getByText('security-platform-reviewers')
        ).toBeInTheDocument();
        expect(
            screen.getByText(/3 roles and 2 groups match/)
        ).toBeInTheDocument();
    });

    it('links each result name to its members page in a new tab', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await searchForSecurityPlatform();
        const roleLink = screen.getByTestId(
            'self-service-row-link-paranoids.tools:role.security-platform-users'
        );
        expect(roleLink).toHaveAttribute(
            'href',
            '/domain/paranoids.tools/role/security-platform-users/members'
        );
        expect(roleLink).toHaveAttribute('target', '_blank');
        expect(roleLink).toHaveAttribute('rel', 'noopener noreferrer');
        const groupLink = screen.getByTestId(
            'self-service-row-link-paranoids.tools:group.security-platform-reviewers'
        );
        expect(groupLink).toHaveAttribute(
            'href',
            '/domain/paranoids.tools/group/security-platform-reviewers/members'
        );
    });

    it('defaults the domain filter to All domains', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await waitFor(() =>
            expect(
                screen.getByTestId('self-service-search-bar')
            ).toBeInTheDocument()
        );
        const domainInput = document.querySelector(
            'input[name="self-service-domain"]'
        );
        expect(domainInput.value).toBe('All domains');
    });

    it('clears results and resets the domain filter when the query is emptied', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await searchForSecurityPlatform();
        expect(screen.getByText('Roles (3)')).toBeInTheDocument();
        fireEvent.change(
            screen.getByPlaceholderText('Search by name or description'),
            { target: { value: '' } }
        );
        fireEvent.click(screen.getByTestId('self-service-search-button'));
        await waitFor(() =>
            expect(
                screen.getByText(
                    /Search by name or description to find self-service/
                )
            ).toBeInTheDocument()
        );
        const domainInput = document.querySelector(
            'input[name="self-service-domain"]'
        );
        expect(domainInput.value).toBe('All domains');
    });

    it('should select multiple requestable rows and open the request dialog', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await searchForSecurityPlatform();
        fireEvent.click(
            screen.getByTestId(
                'select-paranoids.tools:role.security-platform-users'
            )
        );
        fireEvent.click(
            screen.getByTestId(
                'select-paranoids.tools:group.security-platform-reviewers'
            )
        );
        expect(
            screen.getByTestId('self-service-selection-bar')
        ).toHaveTextContent('2 selected · 1 role, 1 group');
        fireEvent.click(screen.getByTestId('request-selected'));
        expect(
            screen.getByText('Add Member to Roles & Groups')
        ).toBeInTheDocument();
        const memberInput = screen.getByDisplayValue('user.tsultanov');
        expect(memberInput).toBeDisabled();
        expect(memberInput).toHaveAttribute('readonly');
        const selected = screen.getAllByTestId('selected-resources');
        expect(selected[0]).toHaveTextContent('security-platform-users');
        expect(selected[1]).toHaveTextContent('security-platform-reviewers');
    });

    it('should disable checkboxes on rows that cannot be requested', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await searchForSecurityPlatform();
        expect(
            screen.getByTestId(
                'select-athenz.prod:role.security-platform-auditors'
            )
        ).toBeDisabled();
        expect(
            screen.getByTestId('select-paranoids.tools:role.scanner-users')
        ).toBeDisabled();
    });

    it('should grey out inherited leave and show a reason on hover', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await searchForSecurityPlatform();
        const leave = screen.getByTestId(
            'leave-paranoids.tools:role.scanner-users'
        );
        expect(leave).toBeDisabled();
        fireEvent.mouseEnter(
            screen.getByTestId(
                'leave-tooltip-paranoids.tools:role.scanner-users'
            )
        );
        expect(
            await screen.findByText(
                /You have this role through the security-champions group/
            )
        ).toBeInTheDocument();
        expect(
            screen.getByTestId('leave-paranoids.tools:group.security-champions')
        ).toBeEnabled();
    });

    it('should open the extend modal for a self-renewable role and show the max', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        fireEvent.click(await screen.findByText(/My Roles & Groups \(4\)/));
        await waitFor(() =>
            expect(screen.getByText('scanner-admins')).toBeInTheDocument()
        );
        fireEvent.click(
            screen.getByTestId('extend-paranoids.tools:role.scanner-admins')
        );
        expect(
            await screen.findByTestId('extend-membership-form')
        ).toBeInTheDocument();
        expect(screen.getByTestId('extend-max-text')).toHaveTextContent(
            'You can extend by up to 30 days'
        );
        // submitting without picking a date surfaces a validation error
        fireEvent.click(screen.getByText('Submit'));
        expect(screen.getByText(/Pick a new expiry date/)).toBeInTheDocument();
    });

    it('should self-renew a group immediately without opening a modal', async () => {
        const updateSelfServe = jest.fn().mockResolvedValue({});
        MockApi.setMockApi({
            getPendingDomainMembersList: jest.fn().mockResolvedValue([]),
            getReviewGroups: jest.fn().mockReturnValue([]),
            getReviewRoles: jest.fn().mockReturnValue([]),
            getPageFeatureFlag: jest.fn().mockResolvedValue({}),
            searchSelfServe: jest
                .fn()
                .mockImplementation((substring, domain, member) =>
                    member
                        ? Promise.resolve(MEMBERSHIPS)
                        : Promise.resolve(EMPTY_SEARCH)
                ),
            updateSelfServe,
        });
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        fireEvent.click(await screen.findByText(/My Roles & Groups \(4\)/));
        await waitFor(() =>
            expect(screen.getByText('scanner-operators')).toBeInTheDocument()
        );
        fireEvent.click(
            screen.getByTestId('extend-paranoids.tools:group.scanner-operators')
        );
        expect(
            screen.queryByTestId('extend-membership-form')
        ).not.toBeInTheDocument();
        await waitFor(() =>
            expect(updateSelfServe).toHaveBeenCalledWith(
                expect.objectContaining({
                    action: 'extend',
                    type: 'group',
                    name: 'scanner-operators',
                }),
                'csrf'
            )
        );
    });

    it('should select memberships on My Roles and open bulk leave', async () => {
        renderWithRedux(<SelfServiceView userName='tsultanov' _csrf='csrf' />);
        await waitFor(() =>
            expect(
                screen.getByText(/My Roles & Groups \(4\)/)
            ).toBeInTheDocument()
        );
        fireEvent.click(screen.getByText(/My Roles & Groups \(4\)/));
        await waitFor(() =>
            expect(screen.getByText('security-champions')).toBeInTheDocument()
        );
        fireEvent.click(
            screen.getByTestId(
                'select-paranoids.tools:group.security-champions'
            )
        );
        expect(
            screen.getByTestId('self-service-selection-bar')
        ).toHaveTextContent('1 selected · 1 group');
        fireEvent.click(screen.getByTestId('leave-selected'));
        expect(
            screen.getByText('This removal is permanent')
        ).toBeInTheDocument();
        expect(screen.getByTestId('leave-modal-message')).toHaveTextContent(
            'paranoids.tools:group.security-champions'
        );
    });
});
