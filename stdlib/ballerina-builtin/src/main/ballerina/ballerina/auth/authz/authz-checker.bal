// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package ballerina.auth.authz;

import ballerina.caching;
import ballerina.auth.authz.permissionstore;

@Description {value:"Representation of AuthzChecker"}
@Field {value:"authzCache: authorization cache instance"}
public struct AuthzChecker {
    permissionstore:PermissionStore permissionstore;
    caching:Cache authzCache;
}

@Description {value:"Creates a Basic Authenticator"}
@Param {value:"permissionstore: PermissionStore instance"}
@Param {value:"cache: Cache instance"}
@Return {value:"AuthzChecker: AuthzChecker instance"}
public function createChecker (permissionstore:PermissionStore permissionstore, caching:Cache cache) (AuthzChecker) {
    if (permissionstore == null) {
        // error, cannot proceed without permissionstore
        error e = {message:"Permission store cannot be null for authz checker"};
        throw e;
    }

    AuthzChecker authzChecker = {permissionstore:permissionstore, authzCache:cache};
    return authzChecker;
}

@Description {value:"Performs a authorization check, by comparing the groups of the user and the groups of the scope"}
@Param {value:"username: user name"}
@Param {value:"scopeName: name of the scope"}
@Return {value:"boolean: true if authorization check is a success, else false"}
public function <AuthzChecker authzChecker> check (string username, string scopeName) (boolean) {
    // TODO: check if there are any groups set in the SecurityContext and if so, match against those.
    return authzChecker.permissionstore.isAuthorized(username, scopeName);
}

@Description {value:"Retrieves the cached authorization result if any, for the given basic auth header value"}
@Param {value:"authzCacheKey: cache key - <username>-<resource>"}
@Return {value:"any: cached entry, or null in a cache miss"}
function <AuthzChecker authzChecker> getCachedAuthzResult (string authzCacheKey) (any) {
    if (authzChecker.authzCache != null) {
        return authzChecker.authzCache.get(authzCacheKey);
    }
    return null;
}

@Description {value:"Caches the authorization result"}
@Param {value:"authzCacheKey: cache key - <username>-<resource>"}
@Param {value:"isAuthorized: authorization decision"}
function <AuthzChecker authzChecker> cacheAuthzResult (string authzCacheKey, boolean isAuthorized) {
    if (authzChecker.authzCache != null) {
        authzChecker.authzCache.put(authzCacheKey, isAuthorized);
    }
}

@Description {value:"Clears any cached authorization result"}
@Param {value:"authzCacheKey: cache key - <username>-<resource>"}
function <AuthzChecker authzChecker> clearCachedAuthzResult (string authzCacheKey) {
    if (authzChecker.authzCache != null) {
        authzChecker.authzCache.remove(authzCacheKey);
    }
}
