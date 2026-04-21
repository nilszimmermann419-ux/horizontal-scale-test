# ShardedMC v2.0 Security Documentation

## Overview

This document outlines the security features, configurations, and best practices for ShardedMC v2.0.

## TLS Setup for gRPC

### Enabling TLS

TLS encryption for gRPC communication between services can be enabled via environment variables:

```bash
# Coordinator
ENABLE_TLS=true
TLS_CERT=/path/to/cert.pem
TLS_KEY=/path/to/key.pem

# Proxy/Shards
COORDINATOR_ADDR=coordinator:50051
# TLS is automatically used when coordinator presents certificates
```

### Certificate Generation

Generate self-signed certificates for development:

```bash
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365 -nodes
```

For production, use certificates from a trusted CA (Let's Encrypt, etc.).

### Certificate Rotation

- Restart coordinator to pick up new certificates
- Proxy/shards will reconnect with new TLS settings
- No downtime required if using multiple coordinator instances

## Authentication

### Mojang Authentication (Production)

By default, ShardedMC supports Mojang's Yggdrasil authentication:

- Players must have valid Minecraft accounts
- UUIDs are verified against Mojang servers
- Prevents impersonation and spoofing

### Offline Mode (Development Only)

⚠️ **Warning: Only use for local development**

```bash
# Proxy
AUTH_MODE=offline
```

In offline mode:
- No account verification
- Any username can connect
- UUIDs are generated from usernames (v3)
- **Never use in production**

### Authentication Flow

1. Player connects to proxy
2. Proxy initiates handshake with Mojang (if enabled)
3. Mojang returns verified UUID
4. Proxy routes player to appropriate shard
5. Shard validates UUID with coordinator

## Rate Limiting

### Connection Rate Limiting

Protect against connection floods:

```bash
# Proxy
CONNECTION_RATE_LIMIT=100  # connections per second per IP
MAX_CONNECTIONS=10000      # total concurrent connections
```

When limits are exceeded:
- New connections are dropped
- Existing connections are unaffected
- Metrics are recorded for monitoring

### Recommendations

- Set `CONNECTION_RATE_LIMIT` based on expected player count
- Monitor metrics for unusual spikes
- Use external DDoS protection for additional layers

## Input Validation

### Packet Validation

All Minecraft protocol packets are validated:

- Packet size limits (max 2MB)
- Field type checking
- Range validation for coordinates
- UTF-8 string validation
- Buffer overflow protection

### Invalid Packet Handling

Invalid packets result in:
- Connection termination
- Logging of offending IP (for monitoring)
- Metrics recording
- No server crash

### Command Validation

Server commands:
- Whitelist of allowed commands
- Permission level checking
- Argument count validation
- Rate limiting per player

## DDoS Protection

### Layer 7 (Application) Protection

Built-in protections:
- Connection rate limiting
- Packet size validation
- Slowloris protection (timeouts)
- Concurrent connection limits per IP

### Layer 4 (Network) Recommendations

Use external tools:
- **Cloudflare Spectrum**: DDoS protection for Minecraft
- **OVH Game**: Anti-DDoS for game servers
- **iptables**: Rate limiting at kernel level

```bash
# Example: iptables rate limiting
iptables -A INPUT -p tcp --dport 25565 -m limit --limit 100/minute --limit-burst 200 -j ACCEPT
iptables -A INPUT -p tcp --dport 25565 -j DROP
```

### Architecture Resilience

- Stateless proxy allows horizontal scaling
- Coordinator failover through multiple instances
- Shard isolation prevents cascade failures
- Circuit breakers on inter-service calls

## Security Best Practices

### Network Security

1. **Use private networks** between services
   - Docker networks for container communication
   - VPC for cloud deployments
   - VPN for hybrid setups

2. **Firewall rules**
   - Only expose proxy port (25565) to internet
   - Coordinator gRPC (50051) only internally accessible
   - Infrastructure ports (NATS, Redis, MinIO) internal only

3. **TLS everywhere**
   - gRPC with TLS between services
   - HTTPS for dashboard/API
   - Redis TLS if supported

### Secrets Management

**Never commit secrets to repository:**

```bash
# Bad - never do this
cat docker-compose.yml | grep PASSWORD
  MINIO_ROOT_PASSWORD: minioadmin  # ❌ Hardcoded
```

**Use environment files or secret management:**

```bash
# .env file (gitignored)
MINIO_ROOT_PASSWORD=supersecret

# Docker secrets (production)
echo "supersecret" | docker secret create minio_password -
```

### Logging and Monitoring

1. **Log security events**
   - Failed authentication attempts
   - Rate limit triggers
   - Invalid packet sources
   - Unusual connection patterns

2. **Set up alerts**
   - High connection rate
   - Multiple failed logins from same IP
   - Memory/CPU spikes
   - Shard disconnections

### Regular Updates

1. **Dependencies**
   ```bash
   # Go dependencies
   cd coordinator && go get -u ./...
   cd proxy && go get -u ./...
   
   # Docker images
   docker-compose pull
   ```

2. **OS patches**
   - Keep host OS updated
   - Use minimal base images (Alpine, distroless)
   - Scan images for vulnerabilities

### Access Control

1. **Dashboard/API**
   - Require authentication for dashboard
   - Use API keys with rotation
   - IP whitelisting for admin endpoints

2. **Infrastructure**
   - Strong passwords for Redis, MinIO
   - NATS authentication tokens
   - Regular credential rotation

## Incident Response

### Detection

Monitor for:
- Sudden traffic spikes
- Increased error rates
- Unusual geographic distribution
- Memory/CPU exhaustion

### Response

1. **Scale horizontally**
   - Add proxy instances
   - Spawn additional shards

2. **Block attackers**
   ```bash
   # Block IP at firewall
   iptables -A INPUT -s <attacker_ip> -j DROP
   ```

3. **Enable emergency mode**
   - Enable whitelist if configured
   - Reduce max connections temporarily
   - Enable additional logging

4. **Contact upstream**
   - Hosting provider DDoS mitigation
   - CDN/WAF support

## Security Checklist

Before production deployment:

- [ ] TLS enabled for all gRPC communication
- [ ] Mojang authentication enabled
- [ ] Rate limits configured appropriately
- [ ] Firewall rules restrict internal services
- [ ] Secrets externalized (not in repo)
- [ ] Logging and monitoring configured
- [ ] DDoS protection in place (external)
- [ ] Regular update process defined
- [ ] Incident response plan documented
- [ ] Security audit completed

## Reporting Security Issues

If you discover a security vulnerability:

1. Do NOT open a public issue
2. Email security@shardedmc.dev
3. Include detailed reproduction steps
4. Allow 90 days for disclosure

## References

- [Minecraft Protocol](https://wiki.vg/Protocol)
- [gRPC Security](https://grpc.io/docs/guides/auth/)
- [Docker Security](https://docs.docker.com/engine/security/)
- [NATS Security](https://docs.nats.io/running-a-nats-service/configuration/securing_nats)
