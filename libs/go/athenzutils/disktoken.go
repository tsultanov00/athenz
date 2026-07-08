// Copyright The Athenz Authors
// Licensed under the terms of the Apache version 2.0 license. See LICENSE file for terms.

package athenzutils

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/AthenZ/athenz/clients/go/zts"
	"github.com/go-jose/go-jose/v4"
	"github.com/go-jose/go-jose/v4/jwt"
)

// jwtSignatureAlgorithms is the set of algorithms accepted by the JWT parser.
// Signature is not verified — we only need the claims.
var jwtSignatureAlgorithms = []jose.SignatureAlgorithm{
	jose.RS256, jose.RS384, jose.RS512,
	jose.PS256, jose.PS384, jose.PS512,
	jose.ES256, jose.ES384, jose.ES512,
	jose.EdDSA,
}

// NewAccessTokenCacheFromDir is like NewAccessTokenCache but also checks
// pre-provisioned token files written by a SIA agent before calling ZTS.
// tokenDir is the SIA token directory root, typically /var/lib/sia/tokens,
// with files laid out as <tokenDir>/<domain>/<filename>.
// Pass an empty tokenDir to skip disk support.
func NewAccessTokenCacheFromDir(ctx context.Context, ztsURL, keyFile, certFile, caCertFile string, proxy bool, tokenDir string, refreshInterval time.Duration) (*AccessTokenCache, error) {
	ztsClient, err := ZtsClient(ztsURL, keyFile, certFile, caCertFile, proxy)
	if err != nil {
		return nil, err
	}
	c := newAccessTokenCacheWithFetcher(ctx, ztsClient, refreshInterval)
	c.tokenDir = tokenDir
	if tokenDir != "" {
		c.loadDiskIndex()
	}
	return c, nil
}

// newAccessTokenCacheWithFetcherAndDir is the test-only variant of
// NewAccessTokenCacheFromDir that accepts a mock fetcher.
func newAccessTokenCacheWithFetcherAndDir(ctx context.Context, fetcher ztsTokenFetcher, tokenDir string, refreshInterval time.Duration) *AccessTokenCache {
	c := newAccessTokenCacheWithFetcher(ctx, fetcher, refreshInterval)
	c.tokenDir = tokenDir
	if tokenDir != "" {
		c.loadDiskIndex()
	}
	return c
}

// loadDiskIndex scans tokenDir and populates diskIndex with
// diskCacheKey(domain, roles) → filePath for every valid token file found.
func (c *AccessTokenCache) loadDiskIndex() {
	domainDirs, err := os.ReadDir(c.tokenDir)
	if err != nil {
		slog.Warn("athenzutils: cannot read token directory", "dir", c.tokenDir, "err", err)
		return
	}
	for _, domainEntry := range domainDirs {
		if !domainEntry.IsDir() {
			continue
		}
		domain := domainEntry.Name()
		domainPath := filepath.Join(c.tokenDir, domain)
		files, err := os.ReadDir(domainPath)
		if err != nil {
			slog.Warn("athenzutils: cannot read domain directory", "dir", domainPath, "err", err)
			continue
		}
		for _, f := range files {
			if f.IsDir() {
				continue
			}
			filePath := filepath.Join(domainPath, f.Name())
			roles, err := rolesFromFile(filePath, domain)
			if err != nil {
				slog.Warn("athenzutils: skipping token file", "file", filePath, "err", err)
				continue
			}
			c.diskIndex.Store(diskCacheKey(domain, roles), filePath)
		}
	}
}

// readFromDisk returns a valid accessTokenEntry from disk for (domain, roles),
// or nil if no match, the file is unreadable, or the token has expired.
func (c *AccessTokenCache) readFromDisk(domain string, roles []string) *accessTokenEntry {
	key := diskCacheKey(domain, roles)
	val, ok := c.diskIndex.Load(key)
	if !ok {
		return nil
	}
	filePath := val.(string)

	data, err := os.ReadFile(filePath)
	if err != nil {
		slog.Warn("athenzutils: cannot read token file", "file", filePath, "err", err)
		c.diskIndex.Delete(key)
		return nil
	}

	var resp zts.AccessTokenResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		slog.Warn("athenzutils: cannot parse token file", "file", filePath, "err", err)
		c.diskIndex.Delete(key)
		return nil
	}
	if resp.Expires_in == nil {
		slog.Warn("athenzutils: token file missing expires_in", "file", filePath)
		c.diskIndex.Delete(key)
		return nil
	}

	// Use the JWT exp claim for accuracy; fall back to now+expires_in.
	expiresAt, err := jwtExpiry(resp.Access_token)
	if err != nil {
		expiresAt = time.Now().Unix() + int64(*resp.Expires_in)
	}

	entry := &accessTokenEntry{
		response:     &resp,
		expiresAt:    expiresAt,
		serverExpiry: int64(*resp.Expires_in),
		domain:       domain,
		roles:        strings.Join(sortedCopy(roles), ","),
	}
	if !entry.hasMinLifetime(0) {
		c.diskIndex.Delete(key)
		return nil
	}
	return entry
}

// rolesFromFile reads a token file and returns the role names from its JWT.
func rolesFromFile(filePath, domain string) ([]string, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil, err
	}
	var resp zts.AccessTokenResponse
	if err := json.Unmarshal(data, &resp); err != nil {
		return nil, fmt.Errorf("parsing token JSON: %w", err)
	}
	if resp.Access_token == "" {
		return nil, fmt.Errorf("empty access_token field")
	}
	scope, err := jwtScope(resp.Access_token)
	if err != nil {
		return nil, fmt.Errorf("parsing token JWT: %w", err)
	}
	return rolesFromScope(domain, scope), nil
}

// jwtScope returns the scope as a space-separated string from an Athenz JWT
// without verifying the signature. Checks "scope" (string) then "scp" (array).
func jwtScope(tokenStr string) (string, error) {
	tok, err := jwt.ParseSigned(tokenStr, jwtSignatureAlgorithms)
	if err != nil {
		return "", err
	}
	var claims map[string]interface{}
	if err := tok.UnsafeClaimsWithoutVerification(&claims); err != nil {
		return "", err
	}
	if v, ok := claims["scope"]; ok {
		if s, ok := v.(string); ok {
			return s, nil
		}
	}
	if v, ok := claims["scp"]; ok {
		switch s := v.(type) {
		case string:
			return s, nil
		case []interface{}:
			parts := make([]string, 0, len(s))
			for _, item := range s {
				if str, ok := item.(string); ok {
					parts = append(parts, str)
				}
			}
			return strings.Join(parts, " "), nil
		}
	}
	return "", nil
}

// jwtExpiry returns the exp claim (Unix seconds) from a JWT without
// verifying the signature.
func jwtExpiry(tokenStr string) (int64, error) {
	tok, err := jwt.ParseSigned(tokenStr, jwtSignatureAlgorithms)
	if err != nil {
		return 0, err
	}
	var claims jwt.Claims
	if err := tok.UnsafeClaimsWithoutVerification(&claims); err != nil {
		return 0, err
	}
	if claims.Expiry == nil {
		return 0, fmt.Errorf("JWT missing exp claim")
	}
	return claims.Expiry.Time().Unix(), nil
}

// rolesFromScope extracts role names from an Athenz JWT scope string.
// Handles both "sports:role.reader" (scoped) and "reader" (bare) formats.
// Ignores "openid", "sports:domain", "sports:service.X", etc.
func rolesFromScope(domain, scope string) []string {
	rolePrefix := domain + ":role."
	var roles []string
	for _, part := range strings.Fields(scope) {
		switch {
		case strings.HasPrefix(part, rolePrefix):
			roles = append(roles, strings.TrimPrefix(part, rolePrefix))
		case !strings.Contains(part, ":") && part != "openid":
			roles = append(roles, part)
		}
	}
	return roles
}

// diskCacheKey returns the diskIndex lookup key for (domain, roles).
// Mirrors Java's ZTSAccessTokenFileLoader format:
// "sports:role:reader,writer" or "sports:role:*" for all-domain tokens.
func diskCacheKey(domain string, roles []string) string {
	if len(roles) == 0 {
		return domain + ":role:*"
	}
	return domain + ":role:" + strings.Join(sortedCopy(roles), ",")
}

// sortedCopy returns a sorted copy of roles without modifying the original slice.
func sortedCopy(roles []string) []string {
	cp := make([]string, len(roles))
	copy(cp, roles)
	sort.Strings(cp)
	return cp
}
