# ADR-012: Cloud-Native Deployment and Horizontal Pod Autoscaling (HPA)

## Status
Accepted

## Context
Production workloads for order fulfillment platforms experience predictable peaks (e.g. Black Friday, flash sales, seasonal events) and quiet periods. Static server sizing either leads to expensive resource over-provisioning or catastrophic outages during peak traffic.

## Decision
We standardized deployment on **Kubernetes** using declarative manifests:
1. **Container Isolation & Security:** Multi-stage Alpine container running as non-root user (`nexususer`) with explicitly declared CPU/memory request and limit thresholds.
2. **Zero-Downtime Rolling Updates:** Configured `RollingUpdate` with `maxSurge: 1` and `maxUnavailable: 0`.
3. **Health Probes:** Exposed Spring Boot Actuator `/actuator/health/liveness` and `/actuator/health/readiness` endpoints to guarantee unhealthy pods are recycled without dropping active traffic.
4. **Elastic Scaling (HPA):** Enabled `HorizontalPodAutoscaler` to dynamically scale pod replicas from 2 to 10 instances when average CPU utilization crosses 70% or memory crosses 80%.

## Consequences
### Positive
- High availability, self-healing, and elastic cost efficiency.
- Cloud agnostic deployment across AWS EKS, GCP GKE, Azure AKS, or on-premise Kubernetes clusters.

### Negative / Trade-offs
- Requires Kubernetes cluster management and container registry infrastructure.
