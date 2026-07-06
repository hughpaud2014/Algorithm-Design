# ADR-006: Authentication and Authorization Architecture

**Status**: Proposed 🔄  
**Date**: 2026-07-06  
**Deciders**: Platform Architecture Team, Security Team  
**Blocks**: Phase 2 (Core Platform)

## Context

Substrata requires a robust authentication and authorization system that supports:

1. **Multi-tenancy**: Users belong to one or more organizations within a tenant
2. **Enterprise SSO**: SAML 2.0 and OIDC for large customers
3. **SCIM provisioning**: Automated user/group sync for enterprise customers
4. **RBAC + ABAC**: Role-based + attribute-based access control
5. **API keys**: For programmatic access (warehouse sync, automation)
6. **Session management**: Secure, tenant-scoped sessions
7. **Audit trail**: All auth events logged (logins, permission changes, etc.)
8. **Security invariant**: Tenant context derived from verified auth principal (no header-trust)

### Current State (Phase 0)

- **Header-trust tenant context**: Violates security invariants (temporary scaffold)
- **No authentication**: No user accounts, sessions, or tokens
- **No authorization**: No RBAC/ABAC enforcement

### Requirements

**Authentication:**
- Username/password for standard users
- Enterprise SSO (SAML/OIDC) for large customers
- MFA (Time-based OTP) for admin roles
- API keys for automation/integration
- Session timeout and refresh tokens

**Authorization:**
- **RBAC**: Roles like Admin, Analyst, Reviewer, Viewer
- **ABAC**: Org-scoped permissions (user can only access their org subtree)
- **Permission granularity**: Document read/write, rule create/edit, cost view, admin functions
- **Inherited permissions**: Child orgs inherit parent org permissions (optional per role)

**Enterprise:**
- SAML 2.0 and OIDC providers (Okta, Azure AD, Google Workspace, OneLogin)
- SCIM 2.0 for automated user/group provisioning
- Just-in-time (JIT) user provisioning on first SSO login
- Custom attribute mapping (e.g., map SAML attribute to Substrata org)

### Security Invariant

**No header-trust post-Phase 2**: Tenant context must be derived from a verified auth token. The `x-tenant-id` header pattern from Phase 0 must be completely removed.

## Decision

We will use **Auth0** (or similar managed identity provider) with custom extensions for tenant-scoped RBAC/ABAC.

### Why Managed Identity Provider?

Building a production-grade auth system with enterprise SSO, SCIM, MFA, and compliance (SOC 2, GDPR) requires:
- Secure credential storage (bcrypt, Argon2)
- OAuth 2.0 / OpenID Connect implementation
- SAML 2.0 implementation
- SCIM 2.0 implementation
- MFA (TOTP, SMS, WebAuthn)
- Session management with refresh tokens
- Rate limiting and brute-force protection
- Password reset flows
- Email verification
- Audit logging
- Compliance certifications

**Engineering cost**: 6-12 months of dedicated work for a team to build and harden.

**Managed provider** (Auth0, Okta, FusionAuth, etc.) provides all of this out-of-the-box with:
- Compliance certifications (SOC 2, ISO 27001, GDPR)
- 99.9%+ uptime SLA
- Security updates and monitoring
- Enterprise SSO connectors (pre-built)
- SCIM support (pre-built)

### Architecture Components

#### 1. Auth0 as Identity Provider

**User authentication flow:**
```
1. User visits app → redirected to Auth0 login
2. User enters credentials (or SSO with enterprise IdP)
3. Auth0 validates and issues JWT access token + refresh token
4. Frontend stores tokens (HttpOnly cookie or localStorage)
5. Frontend includes access token in API requests (Authorization: Bearer <token>)
6. Backend verifies token signature (Auth0 public key)
7. Backend extracts claims: userId, tenantId, orgPath, roles
8. Backend enforces authorization based on claims
```

**JWT claims structure:**
```json
{
  "sub": "auth0|12345",
  "iss": "https://substrata.us.auth0.com/",
  "aud": "https://api.substrata.io",
  "exp": 1735689600,
  "iat": 1735686000,
  "email": "user@example.com",
  "https://substrata.io/claims": {
    "tenant_id": "acme-oil",
    "org_path": "/us-ops/permian",
    "roles": ["analyst", "document-reviewer"],
    "permissions": ["documents:read", "documents:write", "qa:execute"]
  }
}
```

**Backend verification:**
```python
from fastapi import Depends, HTTPException, Request
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt
from jwt import PyJWKClient

security = HTTPBearer()
jwks_client = PyJWKClient("https://substrata.us.auth0.com/.well-known/jwks.json")

async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security)
) -> UserContext:
    token = credentials.credentials
    
    try:
        # Verify token signature
        signing_key = jwks_client.get_signing_key_from_jwt(token)
        payload = jwt.decode(
            token,
            signing_key.key,
            algorithms=["RS256"],
            audience="https://api.substrata.io",
            issuer="https://substrata.us.auth0.com/"
        )
        
        # Extract custom claims
        claims = payload.get("https://substrata.io/claims", {})
        
        return UserContext(
            user_id=payload["sub"],
            email=payload.get("email"),
            tenant_id=claims["tenant_id"],
            org_path=claims["org_path"],
            roles=claims["roles"],
            permissions=set(claims["permissions"])
        )
    
    except jwt.InvalidTokenError as e:
        raise HTTPException(status_code=401, detail="Invalid token")

@app.get("/api/v1/documents")
async def list_documents(
    user: UserContext = Depends(get_current_user)
) -> list[Document]:
    # Tenant context is now auth-derived (security invariant satisfied)
    return await db.documents.find({
        "tenantId": user.tenant_id,
        "orgPath": {"$regex": f"^{user.org_path}"}
    }).to_list()
```

#### 2. Custom Auth0 Actions (Middleware)

**Action: Enrich JWT with Tenant/Org Claims**

When Auth0 issues a token, run custom logic to add Substrata claims:

```javascript
// Auth0 Action (runs on login)
exports.onExecutePostLogin = async (event, api) => {
  const userId = event.user.user_id;
  
  // Fetch user's tenant/org from Substrata API
  const response = await fetch(`https://api.substrata.io/internal/users/${userId}/context`, {
    headers: { "Authorization": `Bearer ${event.secrets.SUBSTRATA_INTERNAL_TOKEN}` }
  });
  
  const userContext = await response.json();
  
  // Add custom claims to token
  api.idToken.setCustomClaim("https://substrata.io/claims", {
    tenant_id: userContext.tenant_id,
    org_path: userContext.org_path,
    roles: userContext.roles,
    permissions: userContext.permissions
  });
  
  api.accessToken.setCustomClaim("https://substrata.io/claims", {
    tenant_id: userContext.tenant_id,
    org_path: userContext.org_path,
    roles: userContext.roles,
    permissions: userContext.permissions
  });
};
```

#### 3. RBAC/ABAC in Substrata Backend

**Role definitions:**
```python
class Role(str, Enum):
    SYSTEM_ADMIN = "system-admin"        # Cross-tenant (support only)
    TENANT_ADMIN = "tenant-admin"        # Full tenant access
    ORG_ADMIN = "org-admin"              # Manage org + children
    ANALYST = "analyst"                  # Read/write documents, run QA
    REVIEWER = "reviewer"                # Review/correct documents
    VIEWER = "viewer"                    # Read-only

class Permission(str, Enum):
    DOCUMENTS_READ = "documents:read"
    DOCUMENTS_WRITE = "documents:write"
    DOCUMENTS_DELETE = "documents:delete"
    QA_RULES_READ = "qa:read"
    QA_RULES_WRITE = "qa:write"
    QA_EXECUTE = "qa:execute"
    COST_VIEW = "cost:view"
    COST_ANALYZE = "cost:analyze"
    ADMIN_USERS = "admin:users"
    ADMIN_ORGS = "admin:orgs"
    ADMIN_WAREHOUSE = "admin:warehouse"

# Role-permission mapping
ROLE_PERMISSIONS: dict[Role, set[Permission]] = {
    Role.VIEWER: {
        Permission.DOCUMENTS_READ,
        Permission.QA_RULES_READ,
        Permission.COST_VIEW,
    },
    Role.ANALYST: {
        Permission.DOCUMENTS_READ,
        Permission.DOCUMENTS_WRITE,
        Permission.QA_RULES_READ,
        Permission.QA_EXECUTE,
        Permission.COST_VIEW,
        Permission.COST_ANALYZE,
    },
    # ... other roles
}

def require_permission(permission: Permission):
    def decorator(func):
        async def wrapper(*args, user: UserContext, **kwargs):
            if permission not in user.permissions:
                raise HTTPException(status_code=403, detail="Insufficient permissions")
            return await func(*args, user=user, **kwargs)
        return wrapper
    return decorator

@app.delete("/api/v1/documents/{document_id}")
@require_permission(Permission.DOCUMENTS_DELETE)
async def delete_document(
    document_id: str,
    user: UserContext = Depends(get_current_user)
) -> None:
    # Permission check passed; enforce tenant/org scope
    result = await db.documents.delete_one({
        "_id": document_id,
        "tenantId": user.tenant_id,
        "orgPath": {"$regex": f"^{user.org_path}"}
    })
    
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Document not found or access denied")
```

#### 4. Enterprise SSO (SAML/OIDC)

**Setup per tenant:**
1. Tenant admin configures SSO connection in Auth0 (via Substrata UI)
2. Substrata creates Auth0 Enterprise Connection (SAML or OIDC)
3. Tenant admin provides IdP metadata (Okta, Azure AD, etc.)
4. Substrata maps SAML/OIDC attributes to Substrata claims
5. Users log in via tenant-specific SSO URL

**SAML attribute mapping:**
```yaml
# Example: Map Okta attributes to Substrata claims
saml_attributes:
  tenant_id: "urn:okta:custom:tenant_id"
  org_path: "urn:okta:custom:org_path"
  roles: "urn:okta:groups"  # Map Okta groups to Substrata roles
```

#### 5. SCIM Provisioning

**Automated user/group sync:**
- Enterprise customer (e.g., Okta) acts as SCIM client
- Substrata API acts as SCIM server (via Auth0 SCIM integration)
- When user added/removed in Okta → automatically added/removed in Substrata
- When user's groups change → roles updated in Substrata

**Auth0 SCIM support**: Built-in for Enterprise plan.

#### 6. API Keys for Automation

**Use case:** Warehouse sync workers (ADR-002) need to authenticate without user session.

**API key structure:**
```python
class APIKey(BaseModel):
    key_id: str                  # Public ID
    key_secret: str              # Hashed secret (bcrypt)
    tenant_id: str               # Scoped to tenant
    name: str                    # "Warehouse Sync - Production"
    permissions: set[Permission] # Limited scope
    created_by: str              # User who created
    created_at: datetime
    last_used_at: datetime | None
    expires_at: datetime | None

# Authentication
async def authenticate_api_key(api_key: str) -> UserContext:
    # Parse key: format is "sk_<key_id>_<secret>"
    parts = api_key.split("_")
    key_id, secret = parts[1], parts[2]
    
    # Fetch key from database
    key_record = await db.api_keys.find_one({"keyId": key_id})
    if not key_record or not verify_hash(secret, key_record.secret_hash):
        raise HTTPException(status_code=401, detail="Invalid API key")
    
    # Check expiration
    if key_record.expires_at and key_record.expires_at < datetime.now():
        raise HTTPException(status_code=401, detail="API key expired")
    
    # Update last used
    await db.api_keys.update_one(
        {"keyId": key_id},
        {"$set": {"lastUsedAt": datetime.now()}}
    )
    
    return UserContext(
        user_id=f"api-key-{key_id}",
        tenant_id=key_record.tenant_id,
        org_path="/",  # Root access for service keys
        roles=["service"],
        permissions=key_record.permissions
    )
```

## Consequences

### Positive

- **Security**: Managed provider handles auth best practices
- **Compliance**: Auth0 SOC 2 / ISO 27001 certified
- **Enterprise-ready**: SAML/OIDC/SCIM out-of-the-box
- **Developer productivity**: No need to build/maintain auth infrastructure
- **Tenant isolation**: Auth-derived context satisfies security invariant
- **MFA support**: Built-in TOTP, SMS, WebAuthn

### Negative

- **Vendor lock-in**: Dependency on Auth0 (mitigated by standard protocols)
- **Cost**: Auth0 charges per user (can be expensive at scale)
- **Custom logic**: JWT enrichment requires Auth0 Actions (JavaScript)
- **Latency**: Token verification adds ~10-20ms per request (mitigated by caching)

### Mitigations

- **Standard protocols**: Use OAuth 2.0 / OIDC (portable to other providers)
- **Cost optimization**: Monitor MAU, negotiate enterprise pricing
- **Action simplicity**: Keep custom logic minimal and well-tested
- **Token caching**: Cache JWKs for faster verification

## Alternatives Considered

### Build Custom Auth System

**Pros:** Full control, no vendor lock-in  
**Cons:** 6-12 months engineering, security risks, no compliance certifications  
**Rejected:** Unrealistic investment for v1; security-critical code best left to experts

### Okta (Alternative Managed Provider)

**Pros:** Mature, enterprise-focused, strong SCIM support  
**Cons:** More expensive than Auth0, less developer-friendly  
**Decision:** Auth0 chosen for better DX and pricing for v1; Okta is a viable alternative

### FusionAuth (Open-Source Alternative)

**Pros:** Self-hosted option, lower cost, no vendor lock-in  
**Cons:** Requires infrastructure management, smaller community  
**Decision:** Managed provider preferred for v1; FusionAuth is a post-v1 option

### Keycloak (Open-Source)

**Pros:** Full-featured, open-source, self-hosted  
**Cons:** Complex setup, operational burden, no managed option  
**Rejected:** Too much operational overhead for v1

### AWS Cognito

**Pros:** Fully managed, AWS-integrated, low cost  
**Cons:** Limited customization, no SCIM, AWS lock-in  
**Rejected:** Insufficient enterprise features (no SCIM)

## Implementation Phases

### Phase 2: Core Auth

1. Set up Auth0 tenant
2. Configure Auth0 Application (API + SPA)
3. Implement JWT verification in FastAPI
4. Replace header-trust with auth-derived tenant context
5. Implement user registration/login flows
6. Add MFA for admin roles

### Phase 2+: Authorization

7. Define roles and permissions
8. Implement RBAC decorator (`@require_permission`)
9. Add org-scoped access control (ABAC)
10. Create audit log for auth events

### Phase 2+: Enterprise Features

11. Configure SAML/OIDC connections per tenant
12. Implement SCIM endpoint (via Auth0)
13. Build tenant admin UI for SSO configuration
14. Test JIT provisioning

### Phase 6+: API Keys

15. Implement API key creation/management
16. Add API key authentication middleware
17. Scope API keys to specific permissions

## Open Questions

- **Session timeout**: 1 hour? 8 hours? Configurable per tenant?
- **Refresh token rotation**: Always rotate or only on suspicious activity?
- **System Admin access**: Require step-up auth (re-authenticate) for customer data access?
- **API key expiration**: Default 90 days? Never expire for production keys?

## References

- [Auth0 Documentation](https://auth0.com/docs)
- [OAuth 2.0 RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749)
- [OpenID Connect Specification](https://openid.net/specs/openid-connect-core-1_0.html)
- [SCIM 2.0 RFC 7643](https://datatracker.ietf.org/doc/html/rfc7643)

---

**Recommendation**: Use Auth0 for managed identity with custom JWT enrichment.

**Next Steps**: Prototype Auth0 integration, validate JWT enrichment, approve before Phase 2.
