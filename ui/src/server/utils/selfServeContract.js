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
'use strict';

const pick = (item, keys, fallback) => {
    for (const key of keys) {
        const value = item[key];
        if (value !== undefined && value !== null && value !== '') {
            return value;
        }
    }
    return fallback;
};

const toBool = (value, fallback = false) => {
    if (value === undefined || value === null || value === '') {
        return fallback;
    }
    if (typeof value === 'boolean') {
        return value;
    }
    if (typeof value === 'number') {
        return value !== 0;
    }
    const normalized = String(value).toLowerCase();
    if (normalized === 'true' || normalized === '1') {
        return true;
    }
    if (normalized === 'false' || normalized === '0') {
        return false;
    }
    return fallback;
};

const toNumber = (value, fallback = 0) => {
    const number = Number(value);
    return Number.isFinite(number) ? number : fallback;
};

const mergeDefined = (base, override) => {
    const merged = { ...base };
    Object.keys(override || {}).forEach((key) => {
        const value = override[key];
        if (value !== undefined && value !== null && value !== '') {
            merged[key] = value;
        }
    });
    return merged;
};

const flattenItem = (item = {}) => {
    const membership =
        item.membership || item.roleMember || item.groupMember || {};
    const members = item.roleMembers || item.groupMembers;
    const nestedMember =
        Array.isArray(members) && members.length === 1 ? members[0] : {};
    return mergeDefined(mergeDefined(membership, nestedMember), item);
};

const parseResourceName = (value) => {
    if (!value || typeof value !== 'string' || value.indexOf(':') === -1) {
        return { domainName: '', name: value || '' };
    }
    const [domainName, rest] = value.split(':');
    const dot = rest.indexOf('.');
    if (dot === -1) {
        return { domainName, name: rest };
    }
    return {
        domainName,
        name: rest.slice(dot + 1),
        kind: rest.slice(0, dot),
    };
};

const shortName = (value, kind) => {
    if (!value) {
        return '';
    }
    const prefix = `:${kind}.`;
    const index = String(value).lastIndexOf(prefix);
    return index === -1 ? value : String(value).slice(index + prefix.length);
};

const inheritedFrom = (item) => {
    const source = pick(
        item,
        ['inheritedFrom', 'memberName', 'memberFullName'],
        ''
    );
    const value = String(source);
    if (value.includes(':group.')) {
        return source;
    }
    return item.inheritedFrom || '';
};

const mapMemberStatus = (item) => {
    const raw = pick(item, ['memberStatus', 'membershipStatus', 'status']);
    if (raw !== undefined && raw !== null && raw !== '') {
        const value = String(raw).toLowerCase();
        if (value === 'pending' || value === 'requested') {
            return 'pending';
        }
        if (
            value === 'member' ||
            value === 'active' ||
            value === 'approved' ||
            value === 'present'
        ) {
            return 'member';
        }
        if (value === 'none' || value === 'not_member' || value === 'absent') {
            return 'none';
        }
    }
    if (
        item.approved === false ||
        item.active === false ||
        item.pending === true
    ) {
        return 'pending';
    }
    if (
        item.approved === true ||
        item.active === true ||
        item.isMember === true
    ) {
        return 'member';
    }
    return 'none';
};

const toSelfServeItem = (item = {}, fallbackType) => {
    const flat = flattenItem(item);
    const parsed = parseResourceName(
        pick(flat, ['name', 'fullName', 'roleName', 'groupName'], '')
    );
    const type =
        flat.type ||
        fallbackType ||
        (parsed.kind === 'group' || flat.groupName ? 'group' : 'role');
    const fullName = pick(
        flat,
        ['name', 'fullName', 'roleName', 'groupName'],
        ''
    );
    return {
        type,
        domainName: pick(flat, ['domainName', 'domain'], parsed.domainName),
        name:
            shortName(fullName, type) ||
            pick(flat, ['roleName', 'groupName', 'simpleName', 'name'], '') ||
            parsed.name,
        description: pick(flat, ['description', 'desc', 'detail'], ''),
        memberCount: toNumber(pick(flat, ['memberCount', 'members'], 0)),
        memberStatus: mapMemberStatus(flat),
        owner: pick(
            flat,
            ['owner', 'roleOwner', 'groupOwner', 'principalOwner', 'metaOwner'],
            ''
        ),
        expiration: pick(flat, ['expiration', 'expiry', 'memberExpiry'], ''),
        requestedOn: pick(
            flat,
            ['requestedOn', 'requestTime', 'pendingTime'],
            ''
        ),
        requestJustification: pick(
            flat,
            ['requestJustification', 'auditRef'],
            ''
        ),
        selfRenew: toBool(pick(flat, ['selfRenew', 'selfRenewEnabled'])),
        selfRenewMins: toNumber(
            pick(flat, ['selfRenewMins', 'selfRenewTimeout'], 0)
        ),
        reviewEnabled: toBool(pick(flat, ['reviewEnabled'])),
        auditEnabled: toBool(pick(flat, ['auditEnabled'])),
        deleteProtection: toBool(pick(flat, ['deleteProtection'])),
        inheritedFrom: inheritedFrom(flat) || undefined,
        maxExpiryDays: toNumber(
            pick(
                flat,
                ['maxExpiryDays', 'memberExpiryDays', 'maxMemberExpiryDays'],
                0
            )
        ),
    };
};

const uniqueDomains = (list) =>
    [...new Set(list.map((item) => item.domainName).filter(Boolean))].sort();

const membershipCountFromList = (list) =>
    list.filter((item) => item.memberStatus === 'member').length;

const extractList = (data = {}) => {
    if (Array.isArray(data)) {
        return data;
    }
    const roles = (data.roles || data.roleList || []).map((item) => ({
        ...item,
        type: item.type || 'role',
    }));
    const groups = (data.groups || data.groupList || []).map((item) => ({
        ...item,
        type: item.type || 'group',
    }));
    if (data.list || data.resources) {
        return data.list || data.resources;
    }
    return [...roles, ...groups];
};

const toSelfServeSearchResponse = (data = {}, options = {}) => {
    const list = extractList(data).map((item) =>
        typeof item === 'string'
            ? toSelfServeItem({ name: item }, options.type)
            : toSelfServeItem(item, options.type)
    );
    const membershipCount = pick(data, ['membershipCount']);
    const response = {
        list,
        domains: data.domains || uniqueDomains(list),
        next: data.next || data.continue || undefined,
    };
    if (membershipCount !== undefined) {
        response.membershipCount = toNumber(membershipCount, 0);
    } else if (options.member) {
        response.membershipCount = membershipCountFromList(list);
    }
    return response;
};

const toZmsSearchParams = (params = {}) => {
    const substring = pick(params, ['substring', 'query', 'name'], '');
    const domain = pick(params, ['domain', 'domainName'], '');
    const member =
        params.member === true ||
        params.member === 'true' ||
        params.member === '1';
    const payload = {
        substring,
        query: substring,
        domain,
        domainName: domain,
        member,
        // ZMS performs the "my memberships" filtering server-side via memberOnly;
        // member is retained so the adapter can derive the membership count.
        memberOnly: member,
    };
    if (params.limit) {
        payload.limit = Number(params.limit);
    }
    if (params.skip || params.next) {
        const cursor = params.skip || params.next;
        payload.skip = cursor;
        payload.next = cursor;
    }
    return payload;
};

module.exports = {
    toSelfServeItem,
    toSelfServeSearchResponse,
    toZmsSearchParams,
};
